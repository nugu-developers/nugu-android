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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.display.LayerType
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*

class AudioPlayerTemplateHandler(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val sessionManager: SessionManagerInterface,
    private val interLayerDisplayPolicyManager: InterLayerDisplayPolicyManager
) : AbstractDirectiveHandler()
    , AudioPlayerDisplayInterface
    , AudioPlayerMetadataDirectiveHandler.Listener
    , PlayStackManagerInterface.PlayContextProvider{
    companion object {
        private const val TAG = "AudioPlayerTemplateHandler"
        const val NAMESPACE = DefaultAudioPlayerAgent.NAMESPACE
        val VERSION = DefaultAudioPlayerAgent.VERSION

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

    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()
    private val templateControllerMap = HashMap<String, AudioPlayerDisplayInterface.Controller>()

    private data class TemplatePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("sourceType")
        val sourceType: DefaultAudioPlayerAgent.SourceType?,
        @SerializedName("token")
        val token: String,
        @SerializedName("url")
        val url: String,
        @SerializedName("duration")
        val duration: String?,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    private inner class TemplateDirectiveInfo(
        info: DirectiveInfo,
        val payload: TemplatePayload
    ) : PlaySynchronizerInterface.SynchronizeObject
        , SessionManagerInterface.Requester
        , DirectiveInfo by info {
        var sourceTemplateId: String = payload.playServiceId + ";" + payload.token

        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                Logger.d(TAG, "[onReleaseCallback] granted : $this")
            }

            override fun onDenied() {
            }
        }

        val layerForInterLayerDisplayPolicy = object : InterLayerDisplayPolicyManager.DisplayLayer {
            override fun clear() {
                executor.submit {
                    executeCancelUnknownInfo(this@TemplateDirectiveInfo, true)
                }
            }

            override fun refresh() {
                // nothing
            }

            override fun getLayerType(): LayerType = LayerType.MEDIA

            override fun getDialogRequestId(): String = info.directive.getDialogRequestId()
            override fun getPushPlayServiceId(): String? = payload.playStackControl?.getPushPlayServiceId()
        }

        override fun getPlayServiceId(): String? = payload.playServiceId

        override fun getDialogRequestId(): String = directive.getDialogRequestId()

        override fun requestReleaseSync() {
            executor.submit {
                executeCancelUnknownInfo(this, true)
            }
        }

        override fun onSyncStateChanged(
            prepared: List<PlaySynchronizerInterface.SynchronizeObject>,
            started: List<PlaySynchronizerInterface.SynchronizeObject>
        ) {
            executor.submit {
                if(prepared.isEmpty() && started.size == 1) {
                    executeCancelUnknownInfo(this, false)
                }
            }
        }

        var playContext: PlayStackManagerInterface.PlayContext? = null
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, TemplatePayload::class.java)
        if (payload == null) {
            setHandlingFailed(info, "[preHandleDirective] invalid Payload")
            return
        }

        Logger.d(TAG, "[preHandleDirective] $payload")

        executor.submit {
            executeCancelPendingInfo()
            executePreparePendingInfo(info, payload)
        }
    }

    private fun executePreparePendingInfo(info: DirectiveInfo, payload: TemplatePayload) {
        TemplateDirectiveInfo(info, payload).apply {
            templateDirectiveInfoMap[sourceTemplateId] = this
            pendingInfo = this
            playSynchronizer.prepareSync(this)
        }
    }

    private fun executeCancelUnknownInfo(info: DirectiveInfo, force: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] force: $force")
        val current = currentInfo
        if (info.directive.getMessageId() == current?.directive?.getMessageId()) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            renderer?.clear(current.sourceTemplateId, force)
        } else if (info.directive.getMessageId() == pendingInfo?.directive?.getMessageId()) {
            executeCancelPendingInfo()
        } else {
            val keys = templateDirectiveInfoMap.filterValues {
                it.directive.header.messageId == it.directive.header.messageId
            }

            keys.forEach {
                templateDirectiveInfoMap[it.key]?.let {
                    Logger.d(TAG, "[executeCancelUnknownInfo] cancel outdated")
                    executeCancelInfoInternal(it)
                }
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
        templateDirectiveInfoMap.remove(info.sourceTemplateId)
        sessionManager.deactivate(info.directive.getDialogRequestId(), info)
        releaseSyncForce(info)
    }

    override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
            val templateInfo = pendingInfo
            if (templateInfo == null || (info.directive.getMessageId() != templateInfo.directive.getMessageId())) {
                Logger.d(TAG, "[handleDirective] skip, maybe canceled display info")
                return@submit
            }
            pendingInfo = null

            val current = currentInfo
            currentInfo = templateInfo
            if(current != null && shouldUpdate(current, templateInfo)) {
                // just update
                Logger.d(TAG, "[handleDirective] update directive")
                templateDirectiveInfoMap.remove(templateInfo.sourceTemplateId)
                templateInfo.sourceTemplateId = current.sourceTemplateId
                templateInfo.playContext = templateInfo.payload.playStackControl?.getPushPlayServiceId()?.let {pushPlayServiceId ->
                    PlayStackManagerInterface.PlayContext(pushPlayServiceId, System.currentTimeMillis())
                }

                renderer?.update(templateInfo.sourceTemplateId, JsonObject().apply {
                    add("template", JsonParser.parseString(templateInfo.directive.payload))
                }.toString())
                setHandlingCompleted(info)
                templateDirectiveInfoMap[current.sourceTemplateId] = templateInfo
                releaseSyncForce(current)
            } else {
                executeRender(templateInfo)
            }
        }
    }

    private fun shouldUpdate(
        prev: TemplateDirectiveInfo,
        next: TemplateDirectiveInfo
    ): Boolean {
        if(prev.payload.playServiceId != next.payload.playServiceId) {
            return false
        }

        if(prev.payload.sourceType != next.payload.sourceType) {
            return false
        }

        if(next.payload.sourceType == DefaultAudioPlayerAgent.SourceType.URL) {
            return prev.payload.url == next.payload.url
        }

        if(next.payload.sourceType == DefaultAudioPlayerAgent.SourceType.ATTACHMENT) {
            return prev.payload.token == next.payload.token
        }

        return false
    }

    private fun releaseSyncForce(info: TemplateDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info, info.onReleaseCallback)
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.directive
        val willBeRender = renderer?.render(
            info.sourceTemplateId,
            "$NAMESPACE.${template.getName()}",
            template.payload,
            template.header
        ) ?: false
        if (!willBeRender) {
            // the renderer denied to render
            setHandlingCompleted(info)
            templateDirectiveInfoMap.remove(info.sourceTemplateId)
            playSynchronizer.releaseSync(info, null)
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
                Logger.d(TAG, "[onRendered] $templateId")
                playSynchronizer.startSync(it)
                interLayerDisplayPolicyManager.onDisplayLayerRendered(it.layerForInterLayerDisplayPolicy)

                controller?.let { templateController ->
                    templateControllerMap[templateId] = templateController
                }

                setHandlingCompleted(it)

                sessionManager.activate(it.getDialogRequestId(), it)
                it.playContext = it.payload.playStackControl?.getPushPlayServiceId()?.let {pushPlayServiceId ->
                    PlayStackManagerInterface.PlayContext(pushPlayServiceId, System.currentTimeMillis())
                }
            }
        }
    }

    override fun displayCardRenderFailed(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onRenderFailed] $templateId")
                cleanupInfo(templateId, it)
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] $templateId")
                cleanupInfo(templateId, it)
                interLayerDisplayPolicyManager.onDisplayLayerCleared(it.layerForInterLayerDisplayPolicy)
            }
        }
    }

    private fun cleanupInfo(templateId: String, info: TemplateDirectiveInfo) {
        setHandlingCompleted(info)
        templateDirectiveInfoMap.remove(templateId)
        templateControllerMap.remove(templateId)
        sessionManager.deactivate(info.getDialogRequestId(), info)
        releaseSyncForce(info)

        if (clearInfoIfCurrent(info)) {
            val nextInfo = pendingInfo
            pendingInfo = null
            currentInfo = nextInfo

            if(nextInfo == null) {
                return
            }

            executeRender(nextInfo)
        }
    }

    private fun clearInfoIfCurrent(info: DirectiveInfo): Boolean {
        Logger.d(TAG, "[clearInfoIfCurrent]")
        if (currentInfo?.directive?.getMessageId() == info.directive.getMessageId()) {
            currentInfo = null
            return true
        }

        return false
    }

    override fun setElementSelected(
        templateId: String,
        token: String,
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String {
        throw UnsupportedOperationException("setElementSelected not supported")
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
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

            renderer?.update(info.sourceTemplateId, jsonMetaData)
        }
    }

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? = currentInfo?.playContext
}