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
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap

class DefaultDisplayAgent(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val elementSelectedEventHandler: ElementSelectedEventHandler,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    enableDisplayLifeCycleManagement: Boolean
) : CapabilityAgent, DisplayAgentInterface
    , SupportedInterfaceContextProvider
    , ControlFocusDirectiveHandler.Controller
    , ControlScrollDirectiveHandler.Controller
    , CloseDirectiveHandler.Controller
    , UpdateDirectiveHandler.Controller
    , RenderDirectiveHandler.Controller
    , PlayStackManagerInterface.PlayContextProvider {
    companion object {
        private const val TAG = "DisplayTemplateAgent"

        const val NAMESPACE = "Display"
        val VERSION = Version(1,3)
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
        info: AbstractDirectiveHandler.DirectiveInfo,
        val payload: TemplatePayload
    ) : PlaySynchronizerInterface.SynchronizeObject
        , AbstractDirectiveHandler.DirectiveInfo by info {
        var renderResultListener: RenderDirectiveHandler.Controller.OnResultListener? = null
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
                executeCancelUnknownInfo(getTemplateId(), immediate)
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

        var playContext: PlayStackManagerInterface.PlayContext? = null
    }

    private val pendingInfo = HashMap<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>()
    private val currentInfo = HashMap<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>()
    private val contextLayerTimer: MutableMap<DisplayAgentInterface.ContextLayer, DisplayTimer>? =
        if (enableDisplayLifeCycleManagement) {
            HashMap()
        } else {
            null
        }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val templateDirectiveInfoMap = ConcurrentHashMap<String, TemplateDirectiveInfo>()
    private val templateControllerMap = HashMap<String, DisplayAgentInterface.Controller>()
    private var renderer: DisplayAgentInterface.Renderer? = null

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this, buildCompactContext().toString())
        contextLayerTimer?.apply {
            EnumSet.allOf(DisplayAgentInterface.ContextLayer::class.java).forEach {
                put(it, DisplayTimer(TAG))
            }
        }
    }

    override fun getInterfaceName(): String = NAMESPACE

    private fun executePreparePendingInfo(info: AbstractDirectiveHandler.DirectiveInfo, payload: TemplatePayload) {
        TemplateDirectiveInfo(info, payload).apply {
            templateDirectiveInfoMap[getTemplateId()] = this
            pendingInfo[payload.getContextLayerInternal()] = this
            playSynchronizer.prepareSync(this)
        }
    }

    private fun executeCancelUnknownInfo(templateId: String, immediate: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] immediate: $immediate")
        val current = findInfoMatchWithTemplateId(currentInfo, templateId)
        val pending = findInfoMatchWithTemplateId(pendingInfo, templateId)
        if (current != null) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            val timer = contextLayerTimer?.get(current.payload.getContextLayerInternal())
            if (immediate) {
                timer?.stop(templateId)
                renderer?.clear(templateId, true)
            } else {
                timer?.stop(templateId)
                timer?.start(templateId, current.getDuration()) {
                    renderer?.clear(templateId, true)
                }
            }
        } else if (pending != null) {
            executeCancelPendingInfo(pending.payload.getContextLayerInternal())
        } else {
            templateDirectiveInfoMap[templateId]?.let {
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

        info.renderResultListener?.onFailure("Canceled by the other display info")
        info.renderResultListener = null
        templateDirectiveInfoMap.remove(info.directive.getMessageId())
        releaseSyncImmediately(info)
    }

    private fun findInfoMatchWithTemplateId(
        from: Map<DisplayAgentInterface.ContextLayer, TemplateDirectiveInfo>,
        templateId: String
    ): TemplateDirectiveInfo? {
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
            if (currentHighest == null) {
                highest = it
            } else {
                if (currentHighest.key.priority < it.key.priority) {
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
            info.renderResultListener?.onSuccess()
            info.renderResultListener = null
            templateDirectiveInfoMap.remove(info.directive.getMessageId())
            playSynchronizer.releaseWithoutSync(info)
            clearInfoIfCurrent(info)
        }
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

                it.renderResultListener?.onSuccess()
                it.renderResultListener = null
                it.playContext =  it.payload.playStackControl?.getPushPlayServiceId()?.let { pushPlayServiceId ->
                    PlayStackManagerInterface.PlayContext(pushPlayServiceId, System.currentTimeMillis())
                }
            }
        }
    }

    override fun displayCardRenderFailed(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onRenderFailed] ${it.getTemplateId()}")
                cleanupInfo(templateId, it)
                it.renderResultListener?.onFailure("failed at renderer")
                it.renderResultListener = null
            }
        }
    }

    override fun displayCardCleared(templateId: String) {
        executor.submit {
            templateDirectiveInfoMap[templateId]?.let {
                Logger.d(TAG, "[onCleared] ${it.getTemplateId()}")
                cleanupInfo(templateId, it)
            }
        }
    }

    private fun cleanupInfo(templateId: String, info: TemplateDirectiveInfo) {
        contextLayerTimer?.get(info.payload.getContextLayerInternal())?.stop(templateId)
        templateDirectiveInfoMap.remove(templateId)
        templateControllerMap.remove(templateId)
        releaseSyncImmediately(info)

        onDisplayCardCleared(info)

        if (clearInfoIfCurrent(info)) {
            val nextInfo =
                pendingInfo.remove(info.payload.getContextLayerInternal()) ?: return
            currentInfo[info.payload.getContextLayerInternal()] = nextInfo
            executeRender(nextInfo)
        }
    }

    private fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        pendingCloseSucceededEvents.remove(templateDirectiveInfo)?.onSuccess()
    }

    private fun clearInfoIfCurrent(info: AbstractDirectiveHandler.DirectiveInfo): Boolean {
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
        val directiveInfo = templateDirectiveInfoMap[templateId]
            ?: throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

        if(directiveInfo.payload.playServiceId.isNullOrBlank()) {
            throw IllegalStateException("empty playServiceId: $templateId")
        }

        return elementSelectedEventHandler.setElementSelected(directiveInfo.payload.playServiceId ,token, callback)
    }

    override fun notifyUserInteraction(templateId: String) {
        val matchedInfo = findInfoMatchWithTemplateId(currentInfo, templateId) ?: return
        contextLayerTimer?.get(matchedInfo.payload.getContextLayerInternal())?.reset(templateId)
    }

    override fun setRenderer(renderer: DisplayAgentInterface.Renderer?) {
        this.renderer = renderer
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

    private fun buildCompactContext() = JsonObject().apply {
        addProperty(
            "version",
            VERSION.toString()
        )
    }

    private fun buildContext(info: TemplateDirectiveInfo?): String = buildCompactContext().apply {
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
            val matchedCurrentRenderedInfo =
                findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId)
                    ?: return@Callable false

            val result =
                templateControllerMap[matchedCurrentRenderedInfo.getTemplateId()]?.controlFocus(
                    direction
                )
                    ?: false

            if (result) {
                contextLayerTimer?.get(matchedCurrentRenderedInfo.payload.getContextLayerInternal())
                    ?.reset(matchedCurrentRenderedInfo.getTemplateId())
            }

            return@Callable result
        })

        return future.get()
    }

    override fun controlScroll(playServiceId: String, direction: Direction): Boolean {
        val future: Future<Boolean> = executor.submit(Callable {
            val matchedCurrentRenderedInfo =
                findCurrentRenderedInfoMatchWithPlayServiceId(playServiceId)
                    ?: return@Callable false

            val result =
                templateControllerMap[matchedCurrentRenderedInfo.getTemplateId()]?.controlScroll(
                    direction
                )
                    ?: false

            if (result) {
                contextLayerTimer?.get(matchedCurrentRenderedInfo.payload.getContextLayerInternal())
                    ?.reset(matchedCurrentRenderedInfo.getTemplateId())
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

            executeCancelUnknownInfo(currentRenderedInfo.getTemplateId(), true)
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
                    it[currentDisplayInfo.payload.getContextLayerInternal()]?.reset(
                        currentDisplayInfo.getTemplateId()
                    )
                }
                listener.onSuccess()
            } else {
                listener.onFailure("no matched token (current:$currentToken / update:$token)")
            }
        }
    }

    override fun preRender(info: RenderDirectiveHandler.RenderDirectiveInfo) {
        executor.submit {
            executeCancelPendingInfo(info.payload.getContextLayerInternal())
            executePreparePendingInfo(info.info, info.payload)
        }
    }

    override fun render(
        messageId: String,
        listener: RenderDirectiveHandler.Controller.OnResultListener
    ) {
        executor.submit {
            val templateInfo = findInfoMatchWithTemplateId(pendingInfo, messageId)
            if (templateInfo == null || (messageId != templateInfo.getTemplateId())) {
                listener.onFailure("skip, maybe canceled display info")
                return@submit
            }

            pendingInfo.remove(templateInfo.payload.getContextLayerInternal())
            currentInfo[templateInfo.payload.getContextLayerInternal()] = templateInfo

            // attach listener
            templateInfo.renderResultListener = listener
            executeRender(templateInfo)
        }
    }

    override fun cancelRender(messageId: String) {
        executor.submit {
            executeCancelUnknownInfo(messageId, true)
        }
    }

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? {
        val playContext = findHighestLayerFrom(currentInfo)?.playContext
        Logger.d(TAG, "[getPlayContext] $playContext")
        return playContext
    }
}