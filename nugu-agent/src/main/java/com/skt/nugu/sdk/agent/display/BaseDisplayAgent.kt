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
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.display.DisplayInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.*

abstract class BaseDisplayAgent(
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface,
    channelName: String
) : AbstractDisplayAgent(
    focusManager,
    contextManager,
    messageSender,
    playSynchronizer,
    playStackManager,
    inputProcessorManager,
    channelName
),
    ChannelObserver {
    companion object {
        private const val TAG = "BaseDisplayAgent"

        private const val EVENT_NAME_ELEMENT_SELECTED = "ElementSelected"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"
    }

    private var focusState: FocusState = FocusState.NONE
    private var pendingInfo: TemplateDirectiveInfo? = null
    protected var currentInfo: TemplateDirectiveInfo? = null

    private var renderer: DisplayAgentInterface.Renderer? = null

    protected var executor: ExecutorService = Executors.newSingleThreadExecutor()

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName(
            "supportedInterfaces",
            getNamespace()
        )

    protected abstract fun getNamespace(): String
    protected abstract fun getVersion(): String
    protected abstract fun getContextPriority(): Int

    private val clearTimeoutScheduler = ScheduledThreadPoolExecutor(1)
    private val clearTimeoutFutureMap: MutableMap<String, ScheduledFuture<*>?> = HashMap()
    private val stoppedTimerTemplateIdMap = ConcurrentHashMap<String, Boolean>()
    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()

    private val eventCallbacks = HashMap<String, DisplayInterface.OnElementSelectedCallback>()

    protected data class TemplatePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("token")
        val token: String?,
        @SerializedName("duration")
        val duration: String?,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    protected inner class TemplateDirectiveInfo(
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
            PlayStackManagerInterface.PlayContext(it, getContextPriority())
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

    protected fun executeCancelUnknownInfo(info: DirectiveInfo, immediate: Boolean) {
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
            if (focusState == FocusState.FOREGROUND) {
                executeRender(templateInfo)
            } else {
                requestFocusForRender(templateInfo)
            }
        }
    }

    private fun releaseSyncImmediately(info: TemplateDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info, info.onReleaseCallback)
        info.playContext?.let {
            playStackManager.remove(it)
        }
    }

    private fun requestFocusForRender(info: TemplateDirectiveInfo) {
        Logger.d(TAG, "[requestFocusForRender] $info")
        focusManager.acquireChannel(
            channelName, this,
            getNamespace()
        )
    }

    final override fun onFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[onFocusChanged] $newFocus / $channelName")
        executor.submit {
            this.focusState = newFocus
            val templateInfo = currentInfo ?: return@submit

            when (newFocus) {
                FocusState.NONE -> {
                    // no-op
                }
                FocusState.BACKGROUND -> {
                    executeOnFocusBackground(templateInfo)
                }
                FocusState.FOREGROUND -> {
                    executeRender(templateInfo)
                }
            }
        }
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.directive
        val willBeRender = renderer?.render(
            template.getMessageId(),
            "${template.getNamespace()}.${template.getName()}",
            template.payload,
            info.getDialogRequestId()
        ) ?: false
        if (!willBeRender) {
            // the renderer denied to render
            setHandlingCompleted(info)
            templateDirectiveInfoMap.remove(info.directive.getMessageId())
            stoppedTimerTemplateIdMap.remove(info.directive.getMessageId())
            playSynchronizer.releaseWithoutSync(info)

            if (clearInfoIfCurrent(info)) {
                focusManager.releaseChannel(channelName, this)
            }
        }
    }

    final override fun cancelDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[cancelDirective] info: $info")
        executor.submit {
            executeCancelUnknownInfo(info, true)
        }
    }

    override fun displayCardRendered(templateId: String) {
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
                releaseSyncImmediately(it)

                onDisplayCardCleared(it)

                if (clearInfoIfCurrent(it)) {
                    val nextInfo = pendingInfo
                    pendingInfo = null

                    if (nextInfo == null) {
                        focusManager.releaseChannel(channelName, this)
                        return@submit
                    }

                    currentInfo = nextInfo
                    if (focusState == FocusState.FOREGROUND) {
                        executeRender(nextInfo)
                    } else {
                        requestFocusForRender(nextInfo)
                    }
                }
            }
        }
    }

    protected abstract fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo)

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
        val dialogRequestId = UUIDGeneration.timeUUID().toString()
        val directiveInfo = templateDirectiveInfoMap[templateId] ?:
        throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                if (messageSender.sendMessage(
                        EventMessageRequest.Builder(
                            jsonContext,
                            getNamespace(),
                            EVENT_NAME_ELEMENT_SELECTED,
                            getVersion()
                        ).dialogRequestId(dialogRequestId).payload(
                            JsonObject().apply {
                                addProperty(KEY_TOKEN, token)
                                directiveInfo.payload.playServiceId
                                    ?.let {
                                        addProperty(KEY_PLAY_SERVICE_ID, it)
                                    }
                            }.toString()
                        ).build()
                    )
                ) {
                    callback?.let {
                        eventCallbacks.put(dialogRequestId, callback)
                    }
                    onSendEventFinished(dialogRequestId)
                } else {
                    callback?.onError(dialogRequestId, DisplayInterface.ErrorType.REQUEST_FAIL)
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                callback?.onError(dialogRequestId, DisplayInterface.ErrorType.REQUEST_FAIL)
            }
        }, namespaceAndName)

        return dialogRequestId
    }

    protected abstract fun getDisplayType(): DisplayAggregatorInterface.Type

    protected abstract fun executeOnFocusBackground(info: DirectiveInfo)

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

    protected fun restartClearTimer(
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

    protected fun stopClearTimer(templateId: String) {
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

    override fun setRenderer(renderer: DisplayAgentInterface.Renderer?) {
        this.renderer = renderer
    }

    protected fun getRenderer(): DisplayAgentInterface.Renderer? = renderer

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirective(dialogRequestId: String, header: Header): Boolean {
        eventCallbacks.remove(dialogRequestId)?.onSuccess(dialogRequestId)
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        eventCallbacks.remove(dialogRequestId)
            ?.onError(dialogRequestId, DisplayInterface.ErrorType.RESPONSE_TIMEOUT)
    }
}