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
package com.skt.nugu.sdk.core.capabilityagents.display

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import java.util.concurrent.*

abstract class BaseDisplayAgent(
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizerInterface,
    channelName: String
) : AbstractDisplayAgent(focusManager, contextManager, messageSender, playSynchronizer, channelName),
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
            NAMESPACE
        )

    private val clearTimeoutScheduler = ScheduledThreadPoolExecutor(1)
    private var clearTimeoutFuture: ScheduledFuture<*>? = null

    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()

    protected data class TemplatePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("token")
        val token: String?,
        @SerializedName("duration")
        val duration: String?
    )

    protected inner class TemplateDirectiveInfo(
        val info: DirectiveInfo,
        val payload: TemplatePayload
    ) : PlaySynchronizerInterface.SynchronizeObject {
        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                Logger.d(TAG, "[onReleaseCallback] granted : $info")
            }

            override fun onDenied() {
            }
        }

        override fun getDialogRequestId(): String = info.directive.getDialogRequestId()

        override fun requestReleaseSync(immediate: Boolean) {
            executor.submit {
                executeCancelUnknownInfo(info, immediate)
            }
        }

        fun getDisplayId(): String = info.directive.getMessageId()

        fun getDuration(): Long {
            return when (payload.duration) {
                "MID" -> 15000L
                "LONG" -> 30000L
                else -> 7000L
            }
        }
    }

    final override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, TemplatePayload::class.java)
        if(payload == null) {
            return
        }

        // no-op
        executor.submit {
            executeCancelPendingInfo()
            val templateInfo = TemplateDirectiveInfo(info, payload)
            templateDirectiveInfoMap[templateInfo.getDisplayId()] = templateInfo
            pendingInfo = templateInfo
            playSynchronizer.prepareSync(templateInfo)
        }
    }

    private fun executeCancelUnknownInfo(info: DirectiveInfo, immediate: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] immediate: $immediate")
        if (info == currentInfo?.info) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            if (immediate) {
                restartClearTimer(0L)
            } else {
                restartClearTimer()
            }
        } else if (info == pendingInfo?.info) {
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

        info.info.result?.setFailed("Canceled by the other display info")
        removeDirective(info.info.directive.getMessageId())
        templateDirectiveInfoMap.remove(info.info.directive.getMessageId())
        releaseSyncImmediately(info)
    }

    final override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
            val templateInfo = pendingInfo
            if (templateInfo == null || (info.directive.getMessageId() != templateInfo.getDisplayId())) {
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
    }

    private fun requestFocusForRender(info: TemplateDirectiveInfo) {
        val playServiceId = info.payload.playServiceId
        Logger.d(TAG, "[requestFocusForRender] playServiceId: $playServiceId")
        focusManager.acquireChannel(
            channelName, this,
            NAMESPACE, playServiceId
        )
    }

    final override fun onFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[onFocusChanged] $newFocus / $channelName")
        executor.submit {
            this.focusState = newFocus
            val templateInfo = currentInfo
            if (templateInfo == null) {
                return@submit
            }

            when (newFocus) {
                FocusState.NONE -> {
                    // no-op
                }
                FocusState.BACKGROUND -> {
                    executeOnFocusBackground(templateInfo.info)
                }
                FocusState.FOREGROUND -> {
                    executeRender(templateInfo)
                }
            }
        }
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.info.directive
        val willBeRender = renderer?.render(
            template.getMessageId(),
            template.getName(),
            template.payload
        ) ?: false
        if (!willBeRender) {
            if (clearInfoIfCurrent(info.info)) {
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
                Logger.d(TAG, "[onRendered] ${it.getDisplayId()}")
                playSynchronizer.startSync(
                    it,
                    object : PlaySynchronizerInterface.OnRequestSyncListener {
                        override fun onGranted() {
                            if (!playSynchronizer.existOtherSyncObject(it)) {
                                restartClearTimer(it.getDuration())
                            } else {
                                stopClearTimer()
                            }
                        }

                        override fun onDenied() {
                        }
                    })
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] ${it.getDisplayId()}")
                releaseSyncImmediately(it)

                val cleared = clearInfoIfCurrent(it.info)

                if (cleared) {
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

    private fun clearInfoIfCurrent(info: DirectiveInfo): Boolean {
        Logger.d(TAG, "[clearInfoIfCurrent]")
        if (currentInfo?.info == info) {
            stopClearTimer()
            currentInfo = null
            templateDirectiveInfoMap.remove(info.directive.getMessageId())
            return true
        }

        return false
    }

    override fun setElementSelected(templateId: String, token: String) {
        val directiveInfo = templateDirectiveInfoMap[templateId] ?: return

        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest(
                        name = EVENT_NAME_ELEMENT_SELECTED,
                        namespace = NAMESPACE,
                        version = VERSION,
                        context = jsonContext,
                        payload = JsonObject().apply {
                            addProperty(KEY_TOKEN, token)
                            directiveInfo.payload.playServiceId
                                ?.let {
                                    addProperty(KEY_PLAY_SERVICE_ID, it)
                                }
                        }.toString()
                    )
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        })
    }

    protected abstract fun getDisplayType(): DisplayAggregatorInterface.Type

    protected abstract fun executeOnFocusBackground(info: DirectiveInfo)
    //    protected abstract fun getRequestClearRunnable(info: DirectiveInfo): Runnable
    private fun startClearTimer(
        timeout: Long = 7000L
    ) {
        Logger.d(TAG, "[startClearTimer] timeout: $timeout")
        clearTimeoutFuture =
            clearTimeoutScheduler.schedule(
                {
                    currentInfo?.let {
                        val template = it.info.directive
                        renderer?.clear(template.getMessageId(), false)
                    }
                }, timeout, TimeUnit.MILLISECONDS
            )
    }

    protected fun restartClearTimer(
        timeout: Long = 7000L
    ) {
        Logger.d(TAG, "[restartClearTimer]")
        stopClearTimer()
        startClearTimer(timeout)
    }

    protected fun stopClearTimer() {
        Logger.d(TAG, "[stopClearTimer]")
        clearTimeoutFuture?.cancel(true)
        clearTimeoutFuture = null
    }

    protected fun setHandlingCompleted(info: DirectiveInfo) {
        info.result?.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    override fun setRenderer(renderer: DisplayAgentInterface.Renderer?) {
        this.renderer = renderer
    }

    protected fun getRenderer(): DisplayAgentInterface.Renderer? = renderer
}