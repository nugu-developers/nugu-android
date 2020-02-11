/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.agent.display

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.*

class AudioPlayerTemplateHandler(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val playStackManager: PlayStackManagerInterface
) : AbstractDirectiveHandler(), AudioPlayerDisplayInterface, AudioPlayerMetadataDirectiveHandler.Listener {
    companion object {
        private const val TAG = "AudioPlayerTemplateHandler"
        const val NAMESPACE = AbstractAudioPlayerAgent.NAMESPACE
        const val VERSION = AbstractAudioPlayerAgent.VERSION

        private const val NAME_AUDIOPLAYER_TEMPLATE1 = "Template1"
        private const val NAME_AUDIOPLAYER_TEMPLATE2 = "Template2"

        private val AUDIOPLAYER_TEMPLATE1 =
            NamespaceAndName(
                NAMESPACE,
                NAME_AUDIOPLAYER_TEMPLATE1
            )
        private val AUDIOPLAYER_TEMPLATE2 =
            NamespaceAndName(
                NAMESPACE,
                NAME_AUDIOPLAYER_TEMPLATE2
            )

    }

    private var pendingInfo: TemplateDirectiveInfo? = null
    private var currentInfo: TemplateDirectiveInfo? = null

    private var renderer: AudioPlayerDisplayInterface.Renderer? = null

    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val clearTimeoutScheduler = ScheduledThreadPoolExecutor(1)
    private val clearTimeoutFutureMap: MutableMap<String, ScheduledFuture<*>?> = HashMap()
    private val stoppedTimerTemplateIdMap = ConcurrentHashMap<String, Boolean>()
    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()
    private val templateControllerMap = HashMap<String, AudioPlayerDisplayInterface.Controller>()

    private data class TemplatePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("token")
        val token: String?,
        @SerializedName("duration")
        val duration: String?,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    private inner class TemplateDirectiveInfo(
        info: DirectiveInfo,
        val payload: TemplatePayload
    ) : PlaySynchronizerInterface.SynchronizeObject
        , DirectiveInfo by info {
        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                Logger.d(TAG, "[onReleaseCallback] granted : $this")
            }

            override fun onDenied() {
            }
        }

        override fun getDialogRequestId(): String = directive.getDialogRequestId()

        override fun requestReleaseSync(immediate: Boolean) {
            executor.submit {
                executeCancelUnknownInfo(this, immediate)
            }
        }

        fun getTemplateId(): String = directive.getMessageId()

        fun getDuration(): Long {
            return when (payload.duration) {
                "MID" -> 15000L
                "LONG" -> 30000L
                else -> 7000L
            }
        }

        var playContext = payload.playStackControl?.getPushPlayServiceId()?.let {
            PlayStackManagerInterface.PlayContext(it, 300)
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, TemplatePayload::class.java)
        if (payload == null) {
            setHandlingFailed(info, "[preHandleDirective] invalid Payload")
            return
        }

        executor.submit {
            executeCancelPendingInfo()
            executePreparePendingInfo(info, payload)
        }
    }

    private fun executePreparePendingInfo(info: DirectiveInfo, payload: TemplatePayload) {
        TemplateDirectiveInfo(info, payload).apply {
            templateDirectiveInfoMap[getTemplateId()] = this
            pendingInfo = this
            playSynchronizer.prepareSync(this)
        }
    }

    private fun executeCancelUnknownInfo(info: DirectiveInfo, immediate: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] immediate: $immediate")
        if (info.directive.getMessageId() == currentInfo?.getTemplateId()) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            val templateId = info.directive.getMessageId()
            if (immediate) {
                stopClearTimer(templateId)
                renderer?.clear(info.directive.getMessageId(), true)
            } else {
                restartClearTimer(templateId)
            }
        } else if (info.directive.getMessageId() == pendingInfo?.getTemplateId()) {
            executeCancelPendingInfo()
        } else {
            templateDirectiveInfoMap[info.directive.getMessageId()]?.let {
                executeCancelInfoInternal(it)
            }
        }
    }

    private fun executeCancelPendingInfo() {
        val info = pendingInfo
        pendingInfo = null

        if (info == null) {
            Logger.d(TAG, "[executeCancelPendingInfo] pendingInfo is null.")
            return
        }

        executeCancelInfoInternal(info)
    }

    private fun executeCancelInfoInternal(info: TemplateDirectiveInfo) {
        Logger.d(TAG, "[executeCancelInfoInternal] cancel pendingInfo : $info")

        setHandlingFailed(info, "Canceled by the other display info")
        templateDirectiveInfoMap.remove(info.directive.getMessageId())
        stoppedTimerTemplateIdMap.remove(info.directive.getMessageId())
        releaseSyncImmediately(info)
    }

    override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
            val templateInfo = pendingInfo
            if (templateInfo == null || (info.directive.getMessageId() != templateInfo.getTemplateId())) {
                Logger.d(TAG, "[handleDirective] skip, maybe canceled display info")
                return@submit
            }

            pendingInfo = null
            currentInfo = templateInfo
            executeRender(templateInfo)
        }
    }

    private fun releaseSyncImmediately(info: TemplateDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info, info.onReleaseCallback)
        info.playContext?.let {
            playStackManager.remove(it)
        }
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.directive
        val willBeRender = renderer?.render(
            template.getMessageId(),
            "$NAMESPACE.${template.getName()}",
            template.payload,
            info.getDialogRequestId()
        ) ?: false
        if (!willBeRender) {
            // the renderer denied to render
            setHandlingCompleted(info)
            templateDirectiveInfoMap.remove(info.directive.getMessageId())
            stoppedTimerTemplateIdMap.remove(info.directive.getMessageId())
            playSynchronizer.releaseWithoutSync(info)
            clearInfoIfCurrent(info)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[cancelDirective] info: $info")
        executor.submit {
            executeCancelUnknownInfo(info, true)
        }
    }

    override fun displayCardRendered(
        templateId: String,
        controller: AudioPlayerDisplayInterface.Controller?
    ) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onRendered] ${it.getTemplateId()}")
                playSynchronizer.startSync(
                    it,
                    object : PlaySynchronizerInterface.OnRequestSyncListener {
                        override fun onGranted() {
                            if (!playSynchronizer.existOtherSyncObject(it)) {
                                restartClearTimer(templateId, it.getDuration())
                            } else {
                                stopClearTimer(templateId)
                            }
                        }

                        override fun onDenied() {
                        }
                    })
                controller?.let { templateController ->
                    templateControllerMap[templateId] = templateController
                }
                it.playContext?.let {playContext->
                    playStackManager.add(playContext)
                }
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] ${it.getTemplateId()}")
                stopClearTimer(templateId)
                setHandlingCompleted(it)
                templateDirectiveInfoMap.remove(templateId)
                stoppedTimerTemplateIdMap.remove(templateId)
                templateControllerMap.remove(templateId)
                releaseSyncImmediately(it)

                if (clearInfoIfCurrent(it)) {
                    val nextInfo = pendingInfo
                    pendingInfo = null
                    currentInfo = nextInfo

                    if(nextInfo == null) {
                        return@submit
                    }

                    executeRender(nextInfo)
                }
            }
        }
    }

    private fun clearInfoIfCurrent(info: DirectiveInfo): Boolean {
        Logger.d(TAG, "[clearInfoIfCurrent]")
        if (currentInfo?.getTemplateId() == info.directive.getMessageId()) {
            currentInfo = null
            return true
        }

        return false
    }

    override fun setElementSelected(
        templateId: String,
        token: String,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String {
        throw UnsupportedOperationException("setElementSelected not supported")
    }

    override fun stopRenderingTimer(templateId: String) {
        if (stoppedTimerTemplateIdMap[templateId] == true) {
            Logger.d(TAG, "[stopRenderingTimer] templateId: $templateId - already called")
            return
        }
        Logger.d(TAG, "[stopRenderingTimer] templateId: $templateId")
        stoppedTimerTemplateIdMap[templateId] = true
        stopClearTimer(templateId)
    }

    private fun startClearTimer(
        templateId: String,
        timeout: Long = 7000L
    ) {
        Logger.d(TAG, "[startClearTimer] templateId: $templateId, timeout: $timeout")
        clearTimeoutFutureMap[templateId] =
            clearTimeoutScheduler.schedule({
                renderer?.clear(templateId, false)
            }, timeout, TimeUnit.MILLISECONDS)
    }

    private fun restartClearTimer(
        templateId: String,
        timeout: Long = 7000L
    ) {
        if (stoppedTimerTemplateIdMap[templateId] == true) {
            Logger.d(
                TAG,
                "[restartClearTimer] not restart because of stopped by stopRenderingTimer() - templateId: $templateId, timeout: $timeout"
            )
            return
        }

        Logger.d(TAG, "[restartClearTimer] templateId: $templateId, timeout: $timeout")
        stopClearTimer(templateId)
        startClearTimer(templateId, timeout)
    }

    private fun stopClearTimer(templateId: String) {
        val future = clearTimeoutFutureMap[templateId]
        var canceled = false
        if (future != null) {
            canceled = future.cancel(true)
            clearTimeoutFutureMap[templateId] = null
        }

        Logger.d(
            TAG,
            "[stopClearTimer] templateId: $templateId , future: $future, canceled: $canceled"
        )
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    override fun setRenderer(renderer: AudioPlayerDisplayInterface.Renderer?) {
        this.renderer = renderer
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()
        val configuration = java.util.HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[AUDIOPLAYER_TEMPLATE1] = nonBlockingPolicy
        configuration[AUDIOPLAYER_TEMPLATE2] = nonBlockingPolicy

        return configuration
    }

    override fun onMetadataUpdate(playServiceId: String, jsonMetaData: String) {
        executor.submit {
            val info = currentInfo
            Logger.d(
                TAG,
                "[onMetadataUpdate] playServiceId: $playServiceId, jsonMetadata: $jsonMetaData"
            )
            if (info == null) {
                Logger.w(TAG, "[onMetadataUpdate] skip - currentInfo is null (no display)")
                return@submit
            }

            val currentPlayServiceId = info.payload.playServiceId
            if (currentPlayServiceId != playServiceId) {
                Logger.w(
                    TAG,
                    "[onMetadataUpdate] skip - playServiceId does not matched with current (current: $currentPlayServiceId)"
                )
                return@submit
            }

            renderer?.update(info.getTemplateId(), jsonMetaData)
        }
    }
}