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
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap

class DefaultDisplayAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val playSynchronizer: PlaySynchronizerInterface,
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val playStackPriority: Int,
    enableDisplayLifeCycleManagement: Boolean
) : AbstractCapabilityAgent()
    , DisplayAgentInterface
    , InputProcessor
    , ControlFocusDirectiveHandler.Controller
    , ControlScrollDirectiveHandler.Controller
    , CloseDirectiveHandler.Controller
    , UpdateDirectiveHandler.Controller
    , PlayStackManagerInterface.PlayContextProvider {
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
        private const val NAME_CUSTOM_TEMPLATE = "CustomTemplate"

        private const val EVENT_NAME_ELEMENT_SELECTED = "ElementSelected"

        // supported types for v1.1
        private const val NAME_WEATHER_1 = "Weather1"
        private const val NAME_WEATHER_2 = "Weather2"
        private const val NAME_WEATHER_3 = "Weather3"
        private const val NAME_WEATHER_4 = "Weather4"
        private const val NAME_WEATHER_5 = "Weather5"
        private const val NAME_FULLIMAGE = "FullImage"

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
        private const val NAME_CALL_3 = "Call3"

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
            NAME_CUSTOM_TEMPLATE
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

        private val COMMERCE_INFO = NamespaceAndName(
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

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"
    }

    data class TemplatePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("token")
        val token: String?,
        @SerializedName("duration")
        val duration: String?,
        @SerializedName("contextLayer")
        val contextLayer: DisplayAgentInterface.ContextLayer?,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    ) {
        fun getContextLayerInternal() = contextLayer ?: DisplayAgentInterface.ContextLayer.INFO
    }

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

    private val pendingInfo = HashMap<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>()
    private val currentInfo = HashMap<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>()
    private val contextLayerTimer: MutableMap<DisplayAgentInterface.ContextLayer, DisplayTimer>? = if(enableDisplayLifeCycleManagement) {
        HashMap()
    } else {
        null
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()
    private val templateControllerMap = HashMap<String, DisplayAgentInterface.Controller>()
    private val eventCallbacks = HashMap<String, DisplayInterface.OnElementSelectedCallback>()
    private var renderer: DisplayAgentInterface.Renderer? = null

    override val namespaceAndName: NamespaceAndName = NamespaceAndName(
        "supportedInterfaces",
        NAMESPACE
    )

    init {
        contextManager.setStateProvider(namespaceAndName, this)
        contextLayerTimer?.apply {
            EnumSet.allOf(DisplayAgentInterface.ContextLayer::class.java).forEach {
                put(it, DisplayTimer(TAG))
            }
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, TemplatePayload::class.java)
        if (payload == null) {
            setHandlingFailed(info, "[preHandleDirective] invalid Payload")
            return
        }

        if (info.directive.getNamespaceAndName() != CUSTOM_TEMPLATE && payload.token.isNullOrBlank()) {
            setHandlingFailed(info, "[preHandleDirective] invalid payload: empty token")
            return
        }

        executor.submit {
            executeCancelPendingInfo(payload.getContextLayerInternal())
            executePreparePendingInfo(info, payload)
        }
    }

    private fun executePreparePendingInfo(info: DirectiveInfo, payload: TemplatePayload) {
        TemplateDirectiveInfo(info, payload).apply {
            templateDirectiveInfoMap[getTemplateId()] = this
            pendingInfo[payload.getContextLayerInternal()] = this
            playSynchronizer.prepareSync(this)
        }
    }

    private fun executeCancelUnknownInfo(info: DirectiveInfo, immediate: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] immediate: $immediate")
        val current = findInfoMatchWithTemplateId(currentInfo, info.directive.getMessageId())
        val pending = findInfoMatchWithTemplateId(pendingInfo, info.directive.getMessageId())
        if (current != null) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            val templateId = info.directive.getMessageId()
            val timer = contextLayerTimer?.get(current.payload.getContextLayerInternal())
            if (immediate) {
                timer?.stop(templateId)
                renderer?.clear(info.directive.getMessageId(), true)
            } else {
                timer?.stop(templateId)
                timer?.start(templateId, current.getDuration()) {
                    renderer?.clear(info.directive.getMessageId(), true)
                }
            }
        } else if (pending != null) {
            executeCancelPendingInfo(pending.payload.getContextLayerInternal())
        } else {
            templateDirectiveInfoMap[info.directive.getMessageId()]?.let {
                executeCancelInfoInternal(it)
            }
        }
    }

    private fun executeCancelPendingInfo(layer: DisplayAgentInterface.ContextLayer) {
        val info = pendingInfo.remove(layer)

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
                else -> {
                    executeHandleTemplateDirective(info)
                }
            }
        }
    }

    private fun findInfoMatchWithTemplateId(from: Map<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>, templateId: String): TemplateDirectiveInfo? {
        val matched = from.filter {
            it.value.getTemplateId() == templateId
        }

        return findHighestLayerFrom(matched)
    }

    private fun findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId: String): TemplateDirectiveInfo? {
        val matched = currentInfo.filter {
            it.value.payload.playServiceId == playServiceId
        }

        return findHighestLayerFrom(matched)
    }

    private fun findHighestLayerFrom(from: Map<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>): TemplateDirectiveInfo? {
        var highest: Map.Entry<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>? = null

        from.forEach {
            val currentHighest = highest
            if(currentHighest == null) {
                highest = it
            } else {
                if(currentHighest.key.priority < it.key.priority) {
                    highest = it
                }
            }
        }

        Logger.d(TAG, "[findHighestLayerFrom] highest: ${highest?.value}")
        return highest?.value
    }

    private fun findCurrentRenderedInfoMatchWithToken(token: String): TemplateDirectiveInfo? {
        val matched = currentInfo.filter {
            it.value.payload.token == token
        }

        return findHighestLayerFrom(matched)
    }

    private fun executeHandleTemplateDirective(info: DirectiveInfo) {
        val templateInfo = findInfoMatchWithTemplateId(pendingInfo, info.directive.getMessageId())
        if (templateInfo == null || (info.directive.getMessageId() != templateInfo.getTemplateId())) {
            Logger.d(TAG, "[executeHandleTemplateDirective] skip, maybe canceled display info")
            return
        }

        pendingInfo.remove(templateInfo.payload.getContextLayerInternal())
        currentInfo[templateInfo.payload.getContextLayerInternal()] = templateInfo
        executeRender(templateInfo)
    }

    private fun releaseSyncImmediately(info: TemplateDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info, info.onReleaseCallback)
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.directive
        val layer = info.payload.contextLayer ?: DisplayAgentInterface.ContextLayer.INFO
        val willBeRender = renderer?.render(
            template.getMessageId(),
            "${template.getNamespace()}.${template.getName()}",
            template.payload,
            info.getDialogRequestId(),
            layer
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
        val blockingPolicy = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[FULLTEXT1] = blockingPolicy
        configuration[FULLTEXT2] = blockingPolicy
        configuration[IMAGETEXT1] = blockingPolicy
        configuration[IMAGETEXT2] = blockingPolicy
        configuration[IMAGETEXT3] = blockingPolicy
        configuration[IMAGETEXT4] = blockingPolicy
        configuration[TEXTLIST1] = blockingPolicy
        configuration[TEXTLIST2] = blockingPolicy
        configuration[TEXTLIST3] = blockingPolicy
        configuration[TEXTLIST4] = blockingPolicy
        configuration[IMAGELIST1] = blockingPolicy
        configuration[IMAGELIST2] = blockingPolicy
        configuration[IMAGELIST3] = blockingPolicy
        configuration[CUSTOM_TEMPLATE] = blockingPolicy

        configuration[WEATHER1] = blockingPolicy
        configuration[WEATHER2] = blockingPolicy
        configuration[WEATHER3] = blockingPolicy
        configuration[WEATHER4] = blockingPolicy
        configuration[WEATHER5] = blockingPolicy
        configuration[FULLIMAGE] = blockingPolicy

        configuration[SCORE_1] = blockingPolicy
        configuration[SCORE_2] = blockingPolicy
        configuration[SEARCH_LIST_1] = blockingPolicy
        configuration[SEARCH_LIST_2] = blockingPolicy

        configuration[COMMERCE_LIST] = blockingPolicy
        configuration[COMMERCE_OPTION] = blockingPolicy
        configuration[COMMERCE_PRICE] = blockingPolicy
        configuration[COMMERCE_INFO] = blockingPolicy

        configuration[CALL_1] = blockingPolicy
        configuration[CALL_2] = blockingPolicy
        configuration[CALL_3] = blockingPolicy

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
                            val timer = contextLayerTimer?.get(it.payload.getContextLayerInternal())
                            if (!playSynchronizer.existOtherSyncObject(it)) {
                                timer?.start(templateId, it.getDuration()) {
                                    renderer?.clear(templateId, true)
                                }
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

                setHandlingCompleted(it)
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] ${it.getTemplateId()}")
                contextLayerTimer?.get(it.payload.getContextLayerInternal())?.stop(templateId)
                templateDirectiveInfoMap.remove(templateId)
                templateControllerMap.remove(templateId)
                releaseSyncImmediately(it)

                onDisplayCardCleared(it)

                if (clearInfoIfCurrent(it)) {
                    val nextInfo = pendingInfo.remove(it.payload.getContextLayerInternal()) ?: return@submit
                    currentInfo[it.payload.getContextLayerInternal()] = nextInfo
                    executeRender(nextInfo)
                }
            }
        }
    }

    private fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        pendingCloseSucceededEvents.remove(templateDirectiveInfo)?.onSuccess()
    }

    private fun clearInfoIfCurrent(info: DirectiveInfo): Boolean {
        Logger.d(TAG, "[clearInfoIfCurrent]")
        val current = findInfoMatchWithTemplateId(currentInfo, info.directive.getMessageId())
        if (current != null) {
            currentInfo.remove(current.payload.getContextLayerInternal())
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

    override fun notifyUserInteraction(templateId: String) {
        val matchedInfo = findInfoMatchWithTemplateId(currentInfo, templateId) ?: return
        contextLayerTimer?.get(matchedInfo.payload.getContextLayerInternal())?.reset(templateId)
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
        ConcurrentHashMap<TemplateDirectiveInfo, CloseDirectiveHandler.Controller.OnCloseListener>()

    private fun sendCloseEventWhenClosed(
        closeTargetTemplateDirective: TemplateDirectiveInfo,
        listener: CloseDirectiveHandler.Controller.OnCloseListener
    ) {
        pendingCloseSucceededEvents[closeTargetTemplateDirective] = listener
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                buildContext(findHighestLayerFrom(currentInfo)),
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

    override fun controlFocus(playServiceId: String, direction: Direction): Boolean {
        val future: Future<Boolean> = executor.submit(Callable {
            val matchedCurrentRenderedInfo = findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId) ?: return@Callable false

            val result =
                templateControllerMap[matchedCurrentRenderedInfo.getTemplateId()]?.controlFocus(direction)
                    ?: false

            if (result) {
                contextLayerTimer?.get(matchedCurrentRenderedInfo.payload.getContextLayerInternal())?.reset(matchedCurrentRenderedInfo.getTemplateId())
            }

            return@Callable result
        })

        return future.get()
    }

    override fun controlScroll(playServiceId: String, direction: Direction): Boolean {
        val future: Future<Boolean> = executor.submit(Callable {
            val matchedCurrentRenderedInfo = findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId) ?: return@Callable false

            val result =
                templateControllerMap[matchedCurrentRenderedInfo.getTemplateId()]?.controlScroll(direction)
                    ?: false

            if (result) {
                contextLayerTimer?.get(matchedCurrentRenderedInfo.payload.getContextLayerInternal())?.reset(matchedCurrentRenderedInfo.getTemplateId())
            }

            return@Callable result
        })

        return future.get()
    }

    override fun close(
        playServiceId: String,
        listener: CloseDirectiveHandler.Controller.OnCloseListener
    ) {
        executor.submit {
            val currentRenderedInfo = findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId)
            if (currentRenderedInfo == null) {
                Logger.w(
                    TAG,
                    "[executeHandleCloseDirective] (Close) no current info matched with ${playServiceId}."
                )
                listener.onFailure()
                return@submit
            }

            executeCancelUnknownInfo(currentRenderedInfo, true)
            sendCloseEventWhenClosed(currentRenderedInfo, listener)
        }
    }

    override fun update(
        token: String,
        payload: String,
        listener: UpdateDirectiveHandler.Controller.OnUpdateListener
    ) {
        executor.submit {
            val currentDisplayInfo = findCurrentRenderedInfoMatchWithToken(token)
            if (currentDisplayInfo == null) {
                listener.onFailure("failed: no current display match with token: $token")
                return@submit
            }

            val currentToken = currentDisplayInfo.payload.token

            if (currentToken == token) {
                renderer?.update(currentDisplayInfo.getTemplateId(), payload)
                contextLayerTimer?.let {
                    it[currentDisplayInfo.payload.getContextLayerInternal()]?.reset(currentDisplayInfo.getTemplateId())
                }
                listener.onSuccess()
            } else {
                listener.onFailure("no matched token (current:$currentToken / update:$token)")
            }
        }
    }

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? {
        val playContext = findHighestLayerFrom(currentInfo)?.playContext
        Logger.d(TAG, "[getPlayContext] $playContext")
        return playContext
    }
}