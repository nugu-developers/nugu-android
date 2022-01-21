/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
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
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

class DisplayAgent(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val elementSelectedEventHandler: ElementSelectedEventHandler,
    private val triggerChildEventSender: TriggerChildEventSender,
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
    , RedirectTriggerChildDirectiveHandler.Controller
    , PlayStackManagerInterface.PlayContextProvider {
    companion object {
        private const val TAG = "DisplayTemplateAgent"

        const val NAMESPACE = "Display"
        val VERSION = Version(1,9)
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
        val playStackControl: PlayStackControl?,
        @SerializedName("historyControl")
        val historyControl: HistoryControl?,
    ) {
        fun getContextLayerInternal() = contextLayer ?: DisplayAgentInterface.ContextLayer.INFO
    }

    private inner class TemplateDirectiveInfo(
        info: AbstractDirectiveHandler.DirectiveInfo,
        val payload: TemplatePayload
    ) : PlaySynchronizerInterface.SynchronizeObject
        , SessionManagerInterface.Requester
        , AbstractDirectiveHandler.DirectiveInfo by info {
        var clearRequested: Boolean = false
        var renderResultListener: RenderDirectiveHandler.Controller.OnResultListener? = null
        val dummyPlaySyncForTimer = object : PlaySynchronizerInterface.SynchronizeObject {
            override val playServiceId: String? = payload.playServiceId
            override val dialogRequestId: String = directive.getDialogRequestId()
        }

        val layerForInterLayerDisplayPolicy = object : InterLayerDisplayPolicyManager.DisplayLayer {
            override fun clear() {
                executor.submit {
                    executeCancelUnknownInfo(getTemplateId(), true)
                }
            }

            override fun refresh() {
                notifyUserInteraction(getTemplateId())
            }

            override val token: String? = payload.token
            override val historyControl: com.skt.nugu.sdk.core.interfaces.display.HistoryControl? =
                payload.historyControl?.let {
                    com.skt.nugu.sdk.core.interfaces.display.HistoryControl(
                        it.parent,
                        it.child,
                        it.parentToken
                    )
                }

            override fun getLayerType(): LayerType = when(payload.getContextLayerInternal()) {
                DisplayAgentInterface.ContextLayer.ALERT -> LayerType.ALERT
                DisplayAgentInterface.ContextLayer.CALL -> LayerType.CALL
                DisplayAgentInterface.ContextLayer.MEDIA -> LayerType.MEDIA
                else -> LayerType.INFO
            }

            override fun getDialogRequestId(): String = info.directive.getDialogRequestId()
            override fun getPushPlayServiceId(): String? = payload.playStackControl?.getPushPlayServiceId()
        }

        override val playServiceId: String? = payload.playServiceId
        override val dialogRequestId: String = directive.getDialogRequestId()

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
                val copyStarted = ArrayList(started)
                // remove self objects
                copyStarted.remove(this)
                copyStarted.remove(lastUpdateDirectivePlaySyncObject)
                // If other play and display exist and no plays equals dialogRequestIds, remove All
                val existDisplay = copyStarted.any { it.isDisplay() }
                val existEqualDialogRequestIdPlay =
                    copyStarted.any { it.dialogRequestId == this.dialogRequestId }
                if (existDisplay && !existEqualDialogRequestIdPlay) {
                    copyStarted.clear()
                }

                if(prepared.isEmpty() && copyStarted.isEmpty()) {
                    if(payload.token != null) {
                        if(currentInfo.any {
                                it.key != this.getTemplateId() && it.value.payload.historyControl?.parentToken == payload.token
                            }) {
                            Logger.d(TAG, "[onSyncStateChanged] has child display, skip clear (templateId: ${getTemplateId()})")
                            return@submit
                        }
                    }

                    executeCancelUnknownInfo(getTemplateId(), false)
                } else {
                    Logger.d(TAG, "[onSyncStateChanged] something synced again, so stop timer. (templateId: ${getTemplateId()})")
                    contextLayerTimer?.get(payload.getContextLayerInternal())?.stop(getTemplateId())
                }
            }
        }

        override fun isDisplay(): Boolean = true

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
        var lastUpdateDirectivePlaySyncObject: PlaySynchronizerInterface.SynchronizeObject? = null
        fun refreshUpdateDirectiveHeader(header: Header) {
            lastUpdateDirectiveHeader?.let {
                sessionManager.deactivate(it.dialogRequestId, this)
            }
            lastUpdateDirectivePlaySyncObject?.let {
                playSynchronizer.releaseSyncImmediately(it)
            }
            lastUpdateDirectiveHeader = header

            sessionManager.activate(header.dialogRequestId, this)

            lastUpdateDirectivePlaySyncObject = object : PlaySynchronizerInterface.SynchronizeObject {
                override val playServiceId: String? = payload.playServiceId
                override val dialogRequestId: String = header.dialogRequestId
            }.apply {
                playSynchronizer.prepareSync(this)
                playSynchronizer.startSync(this)
            }
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

    /**
     * prepared, but not yet render requested.
     */
    private val pendingInfo = HashMap<String, TemplateDirectiveInfo>()

    private val currentInfo = HashMap<String, TemplateDirectiveInfo>()
    private val renderedInfo = ArrayList<TemplateDirectiveInfo>()
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

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
        contextLayerTimer?.apply {
            EnumSet.allOf(DisplayAgentInterface.ContextLayer::class.java).forEach {
                put(it, DisplayTimer(TAG))
            }
        }
    }

    private fun executePreparePendingInfo(info: AbstractDirectiveHandler.DirectiveInfo, payload: TemplatePayload) {
        TemplateDirectiveInfo(info, payload).apply {
            templateDirectiveInfoMap[getTemplateId()] = this
            pendingInfo[getTemplateId()] = this
            playSynchronizer.let {
                it.prepareSync(this)
                it.prepareSync(this.dummyPlaySyncForTimer)
            }
        }
    }

    private fun executeCancelUnknownInfo(templateId: String, immediate: Boolean) {
        Logger.d(TAG, "[executeCancelUnknownInfo] immediate: $immediate")
        val current = currentInfo[templateId]
        val pending = pendingInfo[templateId]
        if (current != null) {
            Logger.d(TAG, "[executeCancelUnknownInfo] cancel current info")
            val timer = contextLayerTimer?.get(current.payload.getContextLayerInternal())
            if (immediate) {
                timer?.stop(templateId)
                current.clearRequested = true
                renderer?.clear(templateId, true)
            } else {
                timer?.stop(templateId)
                timer?.start(templateId, current.getDuration()) {
                    current.clearRequested = true
                    renderer?.clear(templateId, true)
                }
            }
        } else if (pending != null) {
            executeCancelPendingInfo(templateId)
        } else {
            templateDirectiveInfoMap[templateId]?.let {
                executeCancelInfoInternal(it)
            }
        }
    }

    private fun executeCancelPendingInfo(templateId: String) {
        val info = pendingInfo.remove(templateId)

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

    private fun releaseSyncImmediately(info: TemplateDirectiveInfo) {
        playSynchronizer.let {
            it.releaseSyncImmediately(info)
            it.releaseSyncImmediately(info.dummyPlaySyncForTimer)
            info.lastUpdateDirectivePlaySyncObject?.let {playSyncObject ->
                it.releaseSyncImmediately(playSyncObject)
            }
        }
    }

    private fun executeRender(info: TemplateDirectiveInfo) {
        val template = info.directive
        val layer = info.payload.contextLayer ?: DisplayAgentInterface.ContextLayer.INFO

        val parentToken = info.payload.historyControl?.parentToken

        val parentTemplateId: String? = if(!parentToken.isNullOrBlank()) {
            templateDirectiveInfoMap.filter {
                it.value.payload.token == parentToken
            }.keys.firstOrNull()
        } else {
            null
        }

        val willBeRender = renderer?.render(
            template.getMessageId(),
            "${template.getNamespace()}.${template.getName()}",
            template.payload,
            info.directive.header,
            layer,
            parentTemplateId
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
                if(renderedInfo.contains(it)) {
                    Logger.w(TAG, "[onRendered] already called for ${it.getTemplateId()}, so ignore this call.")
                    return@submit
                }
                pauseParentDisplayTimer(it)

                Logger.d(TAG, "[onRendered] ${it.getTemplateId()}, resultListener: ${it.renderResultListener}")
                renderedInfo.add(it)
                playSynchronizer.let {synchronizer ->
                    contextLayerTimer?.get(it.payload.getContextLayerInternal())?.stop(templateId)
                    synchronizer.startSync(it.dummyPlaySyncForTimer, object: PlaySynchronizerInterface.OnRequestSyncListener{
                        override fun onGranted() {
                            playSynchronizer.releaseSync(it.dummyPlaySyncForTimer)
                        }
                    })
                    synchronizer.startSync(it)
                }
                interLayerDisplayPolicyManager.onDisplayLayerRendered(it.layerForInterLayerDisplayPolicy)

                controller?.let { templateController ->
                    templateControllerMap[templateId] = templateController
                }

                it.renderResultListener?.onSuccess()
                it.renderResultListener = null
                sessionManager.activate(it.dialogRequestId, it)
                it.playContext =  it.payload.playStackControl?.getPushPlayServiceId()?.let { pushPlayServiceId ->
                    PlayStackManagerInterface.PlayContext(pushPlayServiceId, System.currentTimeMillis())
                }

                listeners.forEach { listener ->
                    listener.onRendered(templateId, it.dialogRequestId)
                }
            }
        }
    }

    private fun pauseParentDisplayTimer(child: TemplateDirectiveInfo) {
        val parentToken = child.payload.historyControl?.parentToken

        templateDirectiveInfoMap.filter {
            it.value.payload.token == parentToken
        }.forEach {
            contextLayerTimer?.get(it.value.payload.getContextLayerInternal())?.stop(it.key)
        }
    }

    private fun resumeParentDisplayTimer(child: TemplateDirectiveInfo) {
        val parentToken = child.payload.historyControl?.parentToken

        templateDirectiveInfoMap.filter {
            it.value.payload.token == parentToken
        }.forEach {
            contextLayerTimer?.get(it.value.payload.getContextLayerInternal())?.let { timer->
                val templateId = it.key
                val info = it.value
                timer.stop(templateId)
                timer.start(templateId, it.value.getDuration()) {
                    info.clearRequested = true
                    renderer?.clear(templateId, true)
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
                resumeParentDisplayTimer(it)
                cleanupInfo(templateId, it)
                interLayerDisplayPolicyManager.onDisplayLayerCleared(it.layerForInterLayerDisplayPolicy)

                listeners.forEach { listener ->
                    listener.onCleared(templateId, it.dialogRequestId, !it.clearRequested)
                }
            }
        }
    }

    private fun cleanupInfo(templateId: String, info: TemplateDirectiveInfo) {
        renderedInfo.remove(info)
        contextLayerTimer?.get(info.payload.getContextLayerInternal())?.stop(templateId)
        templateDirectiveInfoMap.remove(templateId)
        templateControllerMap.remove(templateId)
        sessionManager.deactivate(info.dialogRequestId, info)
        info.lastUpdateDirectiveHeader?.let {
            sessionManager.deactivate(it.dialogRequestId, info)
        }
        releaseSyncImmediately(info)

        onDisplayCardCleared(info)

        clearInfoIfCurrent(info)
    }

    private fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        pendingCloseSucceededEvents.remove(templateDirectiveInfo)?.onSuccess()
    }

    private fun clearInfoIfCurrent(info: AbstractDirectiveHandler.DirectiveInfo): Boolean {
        Logger.d(TAG, "[clearInfoIfCurrent]")
        return currentInfo.remove(info.directive.getMessageId()) != null
    }

    @Throws(IllegalStateException::class)
    override fun setElementSelected(
        templateId: String,
        token: String,
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String {
        val directiveInfo = templateDirectiveInfoMap[templateId]
            ?: throw IllegalStateException("[setElementSelected] invalid templateId: $templateId (maybe cleared or not rendered yet)")

        if(directiveInfo.payload.playServiceId.isNullOrBlank()) {
            throw IllegalStateException("[setElementSelected] empty playServiceId: $templateId")
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
            val matchedInfo = currentInfo[templateId] ?: return@submit
            contextLayerTimer?.get(matchedInfo.payload.getContextLayerInternal())?.reset(templateId)
        }
    }

    @Throws(IllegalStateException::class)
    override fun triggerChild(
        templateId: String,
        playServiceId: String,
        data: JsonObject,
        callback: DisplayAgentInterface.OnTriggerChildCallback?
    ) {
        val directiveInfo = templateDirectiveInfoMap[templateId]
            ?: throw IllegalStateException("[triggerChild] invalid templateId: $templateId (maybe cleared or not rendered yet)")

        val parentToken = directiveInfo.payload.token ?: throw IllegalStateException("[triggerChild] no token for templateId($templateId)")

        triggerChildEventSender.triggerChild(playServiceId, parentToken, data, directiveInfo.dialogRequestId, callback)
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
                    createDisplayContext(renderedInfo.lastOrNull())
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
            val matchedCurrentRenderedInfo = renderedInfo.findLast {
                it.payload.playServiceId == playServiceId
            } ?: return@Callable false

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
            val matchedCurrentRenderedInfo = renderedInfo.findLast {
                it.payload.playServiceId == playServiceId
            } ?: return@Callable false

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
            val currentRenderedInfo = renderedInfo.findLast {
                it.payload.playServiceId == playServiceId
            }
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
            val currentDisplayInfo = renderedInfo.findLast {
                it.payload.token == token
            }
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
            executeCancelPendingInfo(info.info.directive.getMessageId())
            executePreparePendingInfo(info.info, info.payload)
        }
    }

    override fun render(
        messageId: String,
        listener: RenderDirectiveHandler.Controller.OnResultListener
    ) {
        executor.submit {
            val templateInfo = pendingInfo.remove(messageId)
            if (templateInfo == null || (messageId != templateInfo.getTemplateId())) {
                listener.onFailure("skip, maybe canceled display info")
                return@submit
            }

            currentInfo[messageId] = templateInfo

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
            val playContext = renderedInfo.lastOrNull()?.playContext
            Logger.d(TAG, "[getPlayContext] $playContext")
            playContext
        }).get()

    override fun redirectTriggerChild(
        header: Header,
        payload: RedirectTriggerChildDirectiveHandler.Payload,
        result: RedirectTriggerChildDirectiveHandler.Controller.OnResultListener
    ) {
        executor.submit {
            triggerChildEventSender.triggerChild(payload.playServiceId, payload.parentToken, payload.data, header.dialogRequestId, object: DisplayAgentInterface.OnTriggerChildCallback {
                override fun onSuccess(dialogRequestId: String) {
                    result.onSuccess()
                }

                override fun onError(
                    dialogRequestId: String,
                    errorType: DisplayInterface.ErrorType
                ) {
                    result.onFailure(errorType.toString())
                }
            })
        }
    }
}