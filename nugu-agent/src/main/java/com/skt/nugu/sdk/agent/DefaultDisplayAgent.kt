/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.*
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.util.getValidReferrerDialogRequestId
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.*

class DefaultDisplayAgent(
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface,
    private val playStackPriority: Int
) : AbstractDisplayAgent(
    contextManager,
    messageSender,
    playSynchronizer,
    playStackManager,
    inputProcessorManager
), ControlFocusDirectiveHandler.Controller, ControlScrollDirectiveHandler.Controller {
    companion object {
        private const val TAG = "DisplayTemplateAgent"

        const val NAMESPACE = "Display"
        const val VERSION = "1.2"

        // supported types for v1.0
        private const val NAME_FULLTEXT1 = "FullText1"
        private const val NAME_FULLTEXT2 = "FullText2"
        private const val NAME_IMAGETEXT1 = "ImageText1"
        private const val NAME_IMAGETEXT2 = "ImageText2"
        private const val NAME_IMAGETEXT3 = "ImageText3"
        private const val NAME_IMAGETEXT4 = "ImageText4"
        private const val NAME_TEXTLIST1 = "TextList1"
        private const val NAME_TEXTLIST2 = "TextList2"
        private const val NAME_TEXTLIST3 = "TextList3"
        private const val NAME_TEXTLIST4 = "TextList4"
        private const val NAME_IMAGELIST1 = "ImageList1"
        private const val NAME_IMAGELIST2 = "ImageList2"
        private const val NAME_IMAGELIST3 = "ImageList3"
        private const val NAME_CUSTOMTEMPLATE = "CustomTemplate"

        private const val EVENT_NAME_ELEMENT_SELECTED = "ElementSelected"

        // supported types for v1.1
        private const val NAME_WEATHER_1 = "Weather1"
        private const val NAME_WEATHER_2 = "Weather2"
        private const val NAME_WEATHER_3 = "Weather3"
        private const val NAME_WEATHER_4 = "Weather4"
        private const val NAME_WEATHER_5 = "Weather5"
        private const val NAME_FULLIMAGE = "FullImage"
        private const val NAME_CLOSE = "Close"

        // supported for v1.2
        private const val NAME_SCORE_1 = "Score1"
        private const val NAME_SCORE_2 = "Score2"
        private const val NAME_SEARCH_LIST_1 = "SearchList1"
        private const val NAME_SEARCH_LIST_2 = "SearchList2"

        private const val NAME_COMMERCE_LIST = "CommerceList"
        private const val NAME_COMMERCE_OPTION = "CommerceOption"
        private const val NAME_COMMERCE_PRICE = "CommercePrice"
        private const val NAME_COMMERCE_INFO = "CommerceInfo"

        private const val NAME_CALL_1 = "Call1"
        private const val NAME_CALL_2 = "Call2"
        private const val NAME_CALL_3 = "Call2"

        private const val NAME_UPDATE = "Update"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val FULLTEXT1 = NamespaceAndName(
            NAMESPACE,
            NAME_FULLTEXT1
        )
        private val FULLTEXT2 = NamespaceAndName(
            NAMESPACE,
            NAME_FULLTEXT2
        )
        private val IMAGETEXT1 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT1
        )
        private val IMAGETEXT2 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT2
        )
        private val IMAGETEXT3 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT3
        )
        private val IMAGETEXT4 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT4
        )
        private val TEXTLIST1 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST1
        )
        private val TEXTLIST2 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST2
        )

        private val TEXTLIST3 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST3
        )

        private val TEXTLIST4 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST4
        )

        private val IMAGELIST1 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST1
        )
        private val IMAGELIST2 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST2
        )
        private val IMAGELIST3 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST3
        )
        private val CUSTOM_TEMPLATE = NamespaceAndName(
            NAMESPACE,
            NAME_CUSTOMTEMPLATE
        )

        private val WEATHER1 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_1
        )

        private val WEATHER2 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_2
        )

        private val WEATHER3 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_3
        )

        private val WEATHER4 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_4
        )

        private val WEATHER5 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_5
        )

        private val FULLIMAGE = NamespaceAndName(
            NAMESPACE,
            NAME_FULLIMAGE
        )

        private val CLOSE = NamespaceAndName(
            NAMESPACE,
            NAME_CLOSE
        )

        private val SCORE_1 = NamespaceAndName(
            NAMESPACE,
            NAME_SCORE_1
        )

        private val SCORE_2 = NamespaceAndName(
            NAMESPACE,
            NAME_SCORE_2
        )

        private val SEARCH_LIST_1 = NamespaceAndName(
            NAMESPACE,
            NAME_SEARCH_LIST_1
        )

        private val SEARCH_LIST_2 = NamespaceAndName(
            NAMESPACE,
            NAME_SEARCH_LIST_2
        )

        private val COMMERCE_LIST = NamespaceAndName(
            NAMESPACE,
            NAME_COMMERCE_LIST
        )

        private val COMMERCE_OPTION = NamespaceAndName(
            NAMESPACE,
            NAME_COMMERCE_OPTION
        )

        private val COMMERCE_PRICE = NamespaceAndName(
            NAMESPACE,
            NAME_COMMERCE_PRICE
        )

        private val COMMERCE_INFO= NamespaceAndName(
            NAMESPACE,
            NAME_COMMERCE_INFO
        )

        private val CALL_1 = NamespaceAndName(
            NAMESPACE,
            NAME_CALL_1
        )

        private val CALL_2 = NamespaceAndName(
            NAMESPACE,
            NAME_CALL_2
        )

        private val CALL_3 = NamespaceAndName(
            NAMESPACE,
            NAME_CALL_3
        )

        private val UPDATE = NamespaceAndName(
            NAMESPACE,
            NAME_UPDATE
        )

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"
    }

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

    private data class ClosePayload(
        @SerializedName("playServiceId")
        val playServiceId: String
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
                "NONE" -> 0L
                "MID" -> 15000L
                "LONG" -> 30000L
                "LONGEST" -> 60 * 1000 * 10L // 10min
                else -> 7000L // "SHORT" or Default
            }
        }

        var playContext = payload.playStackControl?.getPushPlayServiceId()?.let {
            PlayStackManagerInterface.PlayContext(it, playStackPriority)
        }
    }

    private var pendingInfo: TemplateDirectiveInfo? = null
    private var currentInfo: TemplateDirectiveInfo? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()
    private val templateControllerMap = HashMap<String, DisplayAgentInterface.Controller>()
    private val eventCallbacks = HashMap<String, DisplayInterface.OnElementSelectedCallback>()
    private var renderer: DisplayAgentInterface.Renderer? = null
    private val timer: DisplayTimer? = DisplayTimer(TAG)

    override val namespaceAndName: NamespaceAndName = NamespaceAndName(
        "supportedInterfaces",
        NAMESPACE
    )

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        if (isTemplateDirective(info.directive.getNamespaceAndName())) {
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
        val current = currentInfo
        if (info.directive.getMessageId() == current?.getTemplateId()) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            val templateId = info.directive.getMessageId()
            if (immediate) {
                timer?.stop(templateId)
                renderer?.clear(info.directive.getMessageId(), true)
            } else {
                restartClearTimer(templateId, current.getDuration())
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
        releaseSyncImmediately(info)
    }

    override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
            when (info.directive.getNamespaceAndName()) {
                CLOSE -> {
                    executeHandleCloseDirective(info)
                }
                UPDATE -> {
                    executeHandleUpdateDirective(info)
                }
                else -> {
                    executeHandleTemplateDirective(info)
                }
            }
        }
    }

    private fun executeHandleCloseDirective(info: DirectiveInfo) {
        val closePayload =
            MessageFactory.create(info.directive.payload, ClosePayload::class.java)
        if (closePayload == null) {
            Logger.w(TAG, "[executeHandleCloseDirective] (Close) no playServiceId at Payload.")
            sendCloseFailed(info, "")
            return
        }

        val currentRenderedInfo = currentInfo ?: return
        val currentPlayServiceId = currentRenderedInfo.payload.playServiceId
        if (currentPlayServiceId.isNullOrBlank()) {
            Logger.w(
                TAG,
                "[executeHandleCloseDirective] (Close) no current info (maybe already closed)."
            )
            sendCloseFailed(info, closePayload.playServiceId)
            return
        }

        Logger.d(
            TAG,
            "[executeHandleCloseDirective] (Close) current : $currentPlayServiceId / close : ${closePayload.playServiceId}"
        )
        if (currentPlayServiceId == closePayload.playServiceId) {
            executeCancelUnknownInfo(currentRenderedInfo, true)
            sendCloseEventWhenClosed(currentRenderedInfo, info)
        } else {
            sendCloseFailed(info, closePayload.playServiceId)
        }
    }

    private fun executeHandleUpdateDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, TemplatePayload::class.java)
        if(payload == null) {
            setHandlingFailed(info, "[executeHandleUpdateDirective] invalid payload: $payload")
            return
        }

        val currentDisplayInfo = currentInfo
        if(currentDisplayInfo == null) {
            setHandlingFailed(info, "[executeHandleUpdateDirective] failed: no current display")
            return
        }

        val currentToken = currentDisplayInfo.payload.token
        val updateToken = payload.token

        if(currentToken == updateToken && !updateToken.isNullOrBlank()) {
            renderer?.update(currentDisplayInfo.getTemplateId(), info.directive.payload)
            setHandlingCompleted(info)
        } else {
            setHandlingFailed(info, "[executeHandleUpdateDirective] no matched token (current:$currentToken / update:$updateToken)")
        }
    }

    private fun executeHandleTemplateDirective(info: DirectiveInfo) {
        val templateInfo = pendingInfo
        if (templateInfo == null || (info.directive.getMessageId() != templateInfo.getTemplateId())) {
            Logger.d(TAG, "[executeHandleTemplateDirective] skip, maybe canceled display info")
            return
        }

        pendingInfo = null
        currentInfo = templateInfo
        executeRender(templateInfo)
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
            "${template.getNamespace()}.${template.getName()}",
            template.payload,
            info.getDialogRequestId()
        ) ?: false
        if (!willBeRender) {
            // the renderer denied to render
            setHandlingCompleted(info)
            templateDirectiveInfoMap.remove(info.directive.getMessageId())
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

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[FULLTEXT1] = nonBlockingPolicy
        configuration[FULLTEXT2] = nonBlockingPolicy
        configuration[IMAGETEXT1] = nonBlockingPolicy
        configuration[IMAGETEXT2] = nonBlockingPolicy
        configuration[IMAGETEXT3] = nonBlockingPolicy
        configuration[IMAGETEXT4] = nonBlockingPolicy
        configuration[TEXTLIST1] = nonBlockingPolicy
        configuration[TEXTLIST2] = nonBlockingPolicy
        configuration[TEXTLIST3] = nonBlockingPolicy
        configuration[TEXTLIST4] = nonBlockingPolicy
        configuration[IMAGELIST1] = nonBlockingPolicy
        configuration[IMAGELIST2] = nonBlockingPolicy
        configuration[IMAGELIST3] = nonBlockingPolicy
        configuration[CUSTOM_TEMPLATE] = nonBlockingPolicy

        configuration[WEATHER1] = nonBlockingPolicy
        configuration[WEATHER2] = nonBlockingPolicy
        configuration[WEATHER3] = nonBlockingPolicy
        configuration[WEATHER4] = nonBlockingPolicy
        configuration[WEATHER5] = nonBlockingPolicy
        configuration[FULLIMAGE] = nonBlockingPolicy

        configuration[CLOSE] = nonBlockingPolicy

        configuration[SCORE_1] = nonBlockingPolicy
        configuration[SCORE_2] = nonBlockingPolicy
        configuration[SEARCH_LIST_1] = nonBlockingPolicy
        configuration[SEARCH_LIST_2] = nonBlockingPolicy

        configuration[COMMERCE_LIST] = nonBlockingPolicy
        configuration[COMMERCE_OPTION] = nonBlockingPolicy
        configuration[COMMERCE_PRICE] = nonBlockingPolicy
        configuration[COMMERCE_INFO] = nonBlockingPolicy

        configuration[CALL_1] = nonBlockingPolicy
        configuration[CALL_2] = nonBlockingPolicy
        configuration[CALL_3] = nonBlockingPolicy

        configuration[UPDATE] = nonBlockingPolicy

        return configuration
    }

    override fun displayCardRendered(
        templateId: String,
        controller: DisplayAgentInterface.Controller?
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
                                timer?.stop(templateId)
                            }
                        }

                        override fun onDenied() {
                        }
                    })
                controller?.let { templateController ->
                    templateControllerMap[templateId] = templateController
                }
                it.playContext?.let { playContext ->
                    playStackManager.add(playContext)
                }
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] ${it.getTemplateId()}")
                timer?.stop(templateId)
                setHandlingCompleted(it)
                templateDirectiveInfoMap.remove(templateId)
                templateControllerMap.remove(templateId)
                releaseSyncImmediately(it)

                onDisplayCardCleared(it)

                if (clearInfoIfCurrent(it)) {
                    val nextInfo = pendingInfo
                    pendingInfo = null
                    currentInfo = nextInfo

                    if (nextInfo == null) {
                        return@submit
                    }

                    executeRender(nextInfo)
                }
            }
        }
    }

    private fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        val pendingCloseSucceededEvent = pendingCloseSucceededEvents[templateDirectiveInfo]
            ?: return

        val payload = MessageFactory.create(
            pendingCloseSucceededEvent.directive.payload,
            ClosePayload::class.java
        ) ?: return

        sendCloseSucceeded(pendingCloseSucceededEvent, payload.playServiceId)
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
        val dialogRequestId = UUIDGeneration.timeUUID().toString()
        val directiveInfo = templateDirectiveInfoMap[templateId]
            ?: throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                if (messageSender.sendMessage(
                        EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            EVENT_NAME_ELEMENT_SELECTED,
                            VERSION
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

    private fun restartClearTimer(
        templateId: String,
        timeout: Long
    ) {
        Logger.d(TAG, "[restartClearTimer] templateId: $templateId, timeout: $timeout")
        timer?.stop(templateId)
        timer?.start(templateId, timeout) {
            renderer?.clear(templateId, false)
        }
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

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        eventCallbacks.remove(dialogRequestId)?.onSuccess(dialogRequestId)
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        eventCallbacks.remove(dialogRequestId)
            ?.onError(dialogRequestId, DisplayInterface.ErrorType.RESPONSE_TIMEOUT)
    }

    private val pendingCloseSucceededEvents =
        ConcurrentHashMap<TemplateDirectiveInfo, DirectiveInfo>()

    private fun sendCloseEventWhenClosed(
        closeTargetTemplateDirective: TemplateDirectiveInfo,
        closeDirective: DirectiveInfo
    ) {
        pendingCloseSucceededEvents[closeTargetTemplateDirective] = closeDirective
    }

    private fun sendCloseEvent(eventName: String, info: DirectiveInfo, playServiceId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        eventName,
                        VERSION
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                    }.toString())
                        .referrerDialogRequestId(info.directive.header.getValidReferrerDialogRequestId())
                        .build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    private fun sendCloseSucceeded(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent("$NAME_CLOSE$NAME_SUCCEEDED", info, playServiceId)
    }

    private fun sendCloseFailed(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent("$NAME_CLOSE$NAME_FAILED", info, playServiceId)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                buildContext(currentInfo),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    private fun buildContext(info: TemplateDirectiveInfo?): String = JsonObject().apply {
        addProperty(
            "version",
            VERSION
        )
        info?.payload?.let {
            addProperty("playServiceId", it.playServiceId)
            addProperty("token", it.token)
            templateControllerMap[info.getTemplateId()]?.let { controller ->
                controller.getFocusedItemToken()?.let { focusedItemToken ->
                    addProperty("focusedItemToken", focusedItemToken)
                }
                controller.getVisibleTokenList()?.let { visibleTokenList ->
                    add("visibleTokenList", JsonArray().apply {
                        visibleTokenList.forEach { token ->
                            add(token)
                        }
                    })
                }
            }
        }
    }.toString()

    private fun isTemplateDirective(namespaceAndName: NamespaceAndName): Boolean =
        when (namespaceAndName) {
            UPDATE,
            CLOSE -> false
            else -> true
        }

    override fun controlFocus(playServiceId: String, direction: Direction): Boolean {
        val result: Future<Boolean> = executor.submit(Callable<Boolean> {
            val currentRenderedInfo = currentInfo ?: return@Callable false
            val currentPlayServiceId = currentRenderedInfo.payload.playServiceId
            if (currentPlayServiceId.isNullOrBlank()) {
                Logger.w(
                    TAG,
                    "[controlFocus] null playServiceId."
                )
                return@Callable false
            }

            templateControllerMap[currentRenderedInfo.getTemplateId()]?.controlFocus(direction)
                ?: false
        })

        return result.get()
    }

    override fun controlScroll(playServiceId: String, direction: Direction): Boolean {
        val result: Future<Boolean> = executor.submit(Callable<Boolean> {
            val currentRenderedInfo = currentInfo ?: return@Callable false
            val currentPlayServiceId = currentRenderedInfo.payload.playServiceId
            if (currentPlayServiceId.isNullOrBlank()) {
                Logger.w(
                    TAG,
                    "[controlFocus] null playServiceId."
                )
                return@Callable false
            }

            templateControllerMap[currentRenderedInfo.getTemplateId()]?.controlScroll(direction)
                ?: false
        })

        return result.get()
    }
}