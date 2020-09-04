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
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.display.LayerType
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

class DefaultDisplayAgent(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val elementSelectedEventHandler: ElementSelectedEventHandler,
    private val sessionManager: SessionManagerInterface,
    private val interLayerDisplayPolicyManager: InterLayerDisplayPolicyManager,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    enableDisplayLifeCycleManagement: Boolean,
    private val defaultDuration: Long
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
        val VERSION = Version(1,6)
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
        , SessionManagerInterface.Requester
        , AbstractDirectiveHandler.DirectiveInfo by info {
        var renderResultListener: RenderDirectiveHandler.Controller.OnResultListener? = null
        val dummyPlaySyncForTimer = object : PlaySynchronizerInterface.SynchronizeObject {
            override fun getPlayServiceId(): String? = payload.playServiceId

            override fun getDialogRequestId(): String = directive.getDialogRequestId()
            override fun requestReleaseSync() {
                // TemplateDirectiveInfo.requestReleaseSync also called.
                // so we skip this.
            }

            override fun onSyncStateChanged(
                prepared: List<PlaySynchronizerInterface.SynchronizeObject>,
                started: List<PlaySynchronizerInterface.SynchronizeObject>
            ) {
                // TemplateDirectiveInfo.requestReleaseSync also called.
                // so we skip this.
            }
        }

        val layerForInterLayerDisplayPolicy = object : InterLayerDisplayPolicyManager.DisplayLayer {
            override fun getPlayServiceId(): String? = payload.playServiceId

            override fun clear() {
                executor.submit {
                    executeCancelUnknownInfo(getTemplateId(), true)
                }
            }

            override fun getLayerType(): LayerType = when(payload.getContextLayerInternal()) {
                DisplayAgentInterface.ContextLayer.ALERT -> LayerType.ALERT
                DisplayAgentInterface.ContextLayer.CALL -> LayerType.CALL
                DisplayAgentInterface.ContextLayer.MEDIA -> LayerType.MEDIA
                else -> LayerType.INFO
            }

            override fun getDialogRequestId(): String = info.directive.getDialogRequestId()
        }

        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                Logger.d(TAG, "[onReleaseCallback] granted : $this")
            }

            override fun onDenied() {
            }
        }

        override fun getPlayServiceId(): String? = payload.playServiceId

        override fun getDialogRequestId(): String = directive.getDialogRequestId()

        override fun requestReleaseSync() {
            executor.submit {
                executeCancelUnknownInfo(getTemplateId(), true)
            }
        }

        override fun onSyncStateChanged(
            prepared: List<PlaySynchronizerInterface.SynchronizeObject>,
            started: List<PlaySynchronizerInterface.SynchronizeObject>
        ) {
            executor.submit {
                if(prepared.isEmpty() && started.size == 1) {
                    executeCancelUnknownInfo(getTemplateId(), false)
                } else {
                    Logger.d(TAG, "[onSyncStateChanged] something synced again, so stop timer.")
                    contextLayerTimer?.get(payload.getContextLayerInternal())?.stop(getTemplateId())
                }
            }
        }

        fun getTemplateId(): String = directive.getMessageId()

        fun getDuration(): Long {
            return when (payload.duration) {
                "MID" -> 15000L
                "LONG" -> 30000L
                "LONGEST" -> 60 * 1000 * 10L // 10min
                "SHORT" -> 7000L
                else -> defaultDuration
            }
        }

        var lastUpdateDirectiveHeader: Header? = null
        fun refreshUpdateDirectiveHeader(header: Header) {
            lastUpdateDirectiveHeader?.let {
                sessionManager.deactivate(it.dialogRequestId, this)
            }
            lastUpdateDirectiveHeader = header
            sessionManager.activate(header.dialogRequestId, this)
        }

        var playContext: PlayStackManagerInterface.PlayContext? = null
    }

    data class DisplayContext(
        val playServiceId: String?,
        val token: String?,
        val focusedItemToken: String?,
        val visibleTokenList: List<String>?
    ) {
        companion object {
            val EMPTY_CONTEXT = DisplayContext(null, null, null, null)
        }
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

    private val listeners = LinkedHashSet<DisplayAgentInterface.Listener>()

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
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
            playSynchronizer.let {
                it.prepareSync(this)
                it.prepareSync(this.dummyPlaySyncForTimer)
            }
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
        sessionManager.deactivate(info.directive.getDialogRequestId(), info)
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
        playSynchronizer.let {
            it.releaseSyncImmediately(info, info.onReleaseCallback)
            it.releaseSyncImmediately(info.dummyPlaySyncForTimer, info.onReleaseCallback)
        }
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
            playSynchronizer.let {
                it.releaseSync(info, null)
                it.releaseSync(info.dummyPlaySyncForTimer, null)
            }
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
                playSynchronizer.let {synchronizer ->
                    contextLayerTimer?.get(it.payload.getContextLayerInternal())?.stop(templateId)
                    synchronizer.startSync(it.dummyPlaySyncForTimer, object: PlaySynchronizerInterface.OnRequestSyncListener{
                        override fun onGranted() {
                            playSynchronizer.releaseSync(it.dummyPlaySyncForTimer, it.onReleaseCallback)
                        }

                        override fun onDenied() {
                        }
                    })
                    synchronizer.startSync(it, null)
                }
                interLayerDisplayPolicyManager.onDisplayLayerRendered(it.layerForInterLayerDisplayPolicy)

                controller?.let { templateController ->
                    templateControllerMap[templateId] = templateController
                }

                it.renderResultListener?.onSuccess()
                it.renderResultListener = null
                sessionManager.activate(it.getDialogRequestId(), it)
                it.playContext =  it.payload.playStackControl?.getPushPlayServiceId()?.let { pushPlayServiceId ->
                    PlayStackManagerInterface.PlayContext(pushPlayServiceId, System.currentTimeMillis())
                }

                listeners.forEach { listener ->
                    listener.onRendered(templateId)
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
                interLayerDisplayPolicyManager.onDisplayLayerCleared(it.layerForInterLayerDisplayPolicy)

                listeners.forEach { listener ->
                    listener.onCleared(templateId)
                }
            }
        }
    }

    private fun cleanupInfo(templateId: String, info: TemplateDirectiveInfo) {
        contextLayerTimer?.get(info.payload.getContextLayerInternal())?.stop(templateId)
        templateDirectiveInfoMap.remove(templateId)
        templateControllerMap.remove(templateId)
        sessionManager.deactivate(info.getDialogRequestId(), info)
        info.lastUpdateDirectiveHeader?.let {
            sessionManager.deactivate(it.dialogRequestId, info)
        }
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
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String {
        val directiveInfo = templateDirectiveInfoMap[templateId]
            ?: throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

        if(directiveInfo.payload.playServiceId.isNullOrBlank()) {
            throw IllegalStateException("empty playServiceId: $templateId")
        }

        return elementSelectedEventHandler.setElementSelected(directiveInfo.payload.playServiceId ,token, postback, callback)
    }

    override fun addListener(listener: DisplayAgentInterface.Listener) {
        executor.submit {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: DisplayAgentInterface.Listener) {
        executor.submit {
            listeners.remove(listener)
        }
    }

    override fun notifyUserInteraction(templateId: String) {
        executor.submit {
            val matchedInfo = findInfoMatchWithTemplateId(currentInfo, templateId) ?: return@submit
            contextLayerTimer?.get(matchedInfo.payload.getContextLayerInternal())?.reset(templateId)
        }
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

    internal data class StateContext(private val displayContext: DisplayContext): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            displayContext.playServiceId?.let {
                addProperty("playServiceId", it)
            }
            displayContext.token?.let {
                addProperty("token", it)
            }
            displayContext.focusedItemToken?.let {
                addProperty("focusedItemToken", it)
            }
            displayContext.visibleTokenList?.let {
                add("visibleTokenList", JsonArray().apply {
                    it.forEach { token ->
                        add(token)
                    }
                })
            }
        }.toString()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                    createDisplayContext(findHighestLayerFrom(currentInfo))
                ),
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        }
    }

    private fun createDisplayContext(info: TemplateDirectiveInfo?): DisplayContext {
        return if(info == null) {
            DisplayContext.EMPTY_CONTEXT
        } else {
            with(info.payload) {
                DisplayContext(playServiceId, token, templateControllerMap[info.getTemplateId()]?.getFocusedItemToken(), templateControllerMap[info.getTemplateId()]?.getVisibleTokenList())
            }
        }
    }

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
        header: Header,
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
                currentDisplayInfo.refreshUpdateDirectiveHeader(header)
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

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? =
        executor.submit(Callable {
            val playContext = findHighestLayerFrom(currentInfo)?.playContext
            Logger.d(TAG, "[getPlayContext] $playContext")
            playContext
        }).get()
}