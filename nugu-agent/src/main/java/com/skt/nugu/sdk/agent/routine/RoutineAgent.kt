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

package com.skt.nugu.sdk.agent.routine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.routine.handler.ContinueDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.StartDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.StopDirectiveHandler
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.text.TextInputRequester
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*

class RoutineAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val directiveProcessor: DirectiveProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val seamlessFocusManager: SeamlessFocusManagerInterface,
    private val startDirectiveHandleController: StartDirectiveHandler.HandleController? = null,
    private val continueDirectiveHandleController: ContinueDirectiveHandler.HandleController? = null,
) : CapabilityAgent,
    RoutineAgentInterface,
    SupportedInterfaceContextProvider,
    StartDirectiveHandler.Controller,
    StopDirectiveHandler.Controller,
    ContinueDirectiveHandler.Controller,
    DisplayAgentInterface.Listener {
    companion object {
        private const val TAG = "RoutineAgent"

        private val VERSION = Version(1, 2)
        const val NAMESPACE = "Routine"
        const val EVENT_FAILED = "Failed"
    }

    internal data class StateContext(
        val context: RoutineAgentInterface.Context
    ) : BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            context.token?.let {
                addProperty("token", it)
            }
            addProperty("routineActivity", context.routineActivity.name)
            context.currentAction?.let {
                addProperty("currentAction", it)
            }
            context.actions?.let {
                add("actions", JsonArray().apply {
                    it.forEach {
                        add(it.toJsonObject())
                    }
                })
            }
        }.toString()
    }

    interface RoutineRequestListener {
        fun onCancel()
        fun onFinish()
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val listeners = CopyOnWriteArraySet<RoutineAgentInterface.RoutineListener>()

    private inner class RoutineRequest(
        val directive: StartDirectiveHandler.StartDirective,
        private val listener: RoutineRequestListener
    ) : SeamlessFocusManagerInterface.Requester {
        private var currentActionIndex: Int = 0
        private var currentActionHandlingListener: DirectiveGroupHandlingListener? = null
        var currentActionDialogRequestId: String? = null
        var isPaused = false
        private var isCanceled = false
        private var scheduledFutureForTryStartNextAction: ScheduledFuture<*>? = null
        private var scheduledFutureForCancelByInterrupt: ScheduledFuture<*>? = null

        fun start() {
            Logger.d(TAG, "[RoutineRequest] start")
            directive.payload.actions.firstOrNull()?.let {
                doAction(it)
            }
        }

        fun cancel(cancelCurrentAction: Boolean = true) {
            Logger.d(
                TAG,
                "[RoutineRequest] cancel: $isCanceled, cancelCurrentAction: $cancelCurrentAction"
            )
            if (isCanceled) {
                return
            }
            isCanceled = true
            if (cancelCurrentAction) {
                cancelCurrentAction()
            }
            listener.onCancel()
        }

        private fun cancelCurrentAction() {
            currentActionDialogRequestId?.let {
                directiveProcessor.cancelDialogRequestId(it)
            }
        }

        fun pause(cancelCurrentAction: Boolean = true) {
            Logger.d(
                TAG,
                "[RoutineRequest] pause - cancelCurrentAction: $cancelCurrentAction, isPaused: $isPaused"
            )
            if (cancelCurrentAction) {
                cancelCurrentAction()
            }
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction = null
            scheduledFutureForCancelByInterrupt?.cancel(true)
            scheduledFutureForCancelByInterrupt = executor.schedule({
                cancel(cancelCurrentAction)
            }, 60, TimeUnit.SECONDS)

            if (!isPaused) {
                isPaused = true
                state = RoutineAgentInterface.State.INTERRUPTED

                listeners.forEach {
                    it.onInterrupted(directive)
                }
            }
        }

        fun cancelIfCurrentRoutineDisplayCleared(dialogRequestId: String) {
            Logger.d(
                TAG,
                "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $currentActionDialogRequestId, $dialogRequestId"
            )
            if (currentActionDialogRequestId == dialogRequestId) {
                Logger.d(
                    TAG,
                    "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $dialogRequestId"
                )
                cancel()
            }
        }

        fun doContinue() {
            Logger.d(
                TAG,
                "[RoutineRequest] doContinue - isPaused: $isPaused, isCanceled: $isCanceled"
            )
            if (isCanceled) {
                return
            }
            isPaused = false
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction = null
            scheduledFutureForCancelByInterrupt?.cancel(true)
            scheduledFutureForCancelByInterrupt = null

            listeners.forEach {
                it.onPlaying(directive)
            }
            tryStartNextAction()
        }

        private fun tryStartNextAction() {
            Logger.d(
                TAG,
                "[RoutineRequest] tryStartNextAction - $currentActionIndex, isPaused: $isPaused"
            )
            if (isPaused || isCanceled) {
                return
            }

            currentActionIndex++
            if (currentActionIndex < directive.payload.actions.size) {
                doAction(directive.payload.actions[currentActionIndex])
            } else {
                listener.onFinish()
            }
        }

        fun getCurrentActionIndex(): Int = currentActionIndex

        private fun doAction(action: Action) {
            Logger.d(TAG, "[RoutineRequest] doAction - $action")
            if (action.type == Action.Type.TEXT && action.text != null) {
                doTextAction(action)
            } else if (action.type == Action.Type.DATA && action.data != null) {
                doDataAction(action)
            }
        }

        private fun doTextAction(action: Action) {
            textAgent?.textInput(
                TextInputRequester.Request.Builder(action.text!!)
                    .playServiceId(action.playServiceId).token(action.token)
                    .includeDialogAttribute(false), object : TextAgentInterface.RequestListener {
                    override fun onRequestCreated(dialogRequestId: String) {
                        Logger.d(TAG, "[onRequestCreated] dialogRequestId: $dialogRequestId")
                        textInputRequests.add(dialogRequestId)
                        currentActionDialogRequestId = dialogRequestId
                        currentActionHandlingListener = DirectiveGroupHandlingListener(
                            dialogRequestId,
                            directiveGroupProcessor,
                            directiveSequencer,
                            object : DirectiveGroupHandlingListener.OnDirectiveResultListener {
                                override fun onFinish(isExistCanceledOrFailed: Boolean) {
                                    Logger.d(TAG, "[onFinish] dialogRequestId: $dialogRequestId")
                                    if (isExistCanceledOrFailed) {
                                        pause()
                                    } else {
                                        scheduledFutureForTryStartNextAction?.cancel(true)
                                        scheduledFutureForTryStartNextAction = null
                                        if (action.postDelayInMilliseconds != null) {
                                            scheduledFutureForTryStartNextAction =
                                                executor.schedule(
                                                    {
                                                        tryStartNextAction()
                                                    },
                                                    action.postDelayInMilliseconds,
                                                    TimeUnit.MILLISECONDS
                                                )
                                        } else {
                                            tryStartNextAction()
                                        }
                                    }
                                }

                                override fun onCanceled(directive: Directive) {
                                    pause()
                                }

                                override fun onFailed(directive: Directive) {
                                    pause()
                                }
                            })
                    }

                    override fun onReceiveResponse(dialogRequestId: String) {
                    }

                    override fun onError(
                        dialogRequestId: String,
                        type: TextAgentInterface.ErrorType
                    ) {
                        currentActionDialogRequestId = null
                        currentActionHandlingListener = null
                    }
                })
        }

        private fun doDataAction(action: Action) {
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val request = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "ActionTriggered",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        action.playServiceId?.let {
                            addProperty("playServiceId", it)
                        }
                        add("data", action.data)
                    }.toString())
                        .referrerDialogRequestId(directive.header.referrerDialogRequestId ?: "")
                        .build()

                    Logger.d(
                        TAG,
                        "doDataAction - [onContext] dialogRequestId: ${request.dialogRequestId}"
                    )
                    currentActionDialogRequestId = request.dialogRequestId
                    currentActionHandlingListener = DirectiveGroupHandlingListener(
                        request.dialogRequestId,
                        directiveGroupProcessor,
                        directiveSequencer,
                        object : DirectiveGroupHandlingListener.OnDirectiveResultListener {
                            override fun onFinish(isExistCanceledOrFailed: Boolean) {
                                Logger.d(
                                    TAG,
                                    "doDataAction - [onFinish] dialogRequestId: ${request.dialogRequestId}"
                                )
                                if (isExistCanceledOrFailed) {
                                    pause()
                                } else {
                                    scheduledFutureForTryStartNextAction?.cancel(true)
                                    scheduledFutureForTryStartNextAction = null
                                    if (action.postDelayInMilliseconds != null) {
                                        scheduledFutureForTryStartNextAction = executor.schedule({
                                            tryStartNextAction()
                                        }, action.postDelayInMilliseconds, TimeUnit.MILLISECONDS)
                                    } else {
                                        tryStartNextAction()
                                    }
                                }
                            }

                            override fun onCanceled(directive: Directive) {
                                pause()
                            }

                            override fun onFailed(directive: Directive) {
                                pause()
                            }
                        })
                    messageSender.newCall(request).enqueue(object : MessageSender.Callback {
                        override fun onFailure(request: MessageRequest, status: Status) {
                            currentActionDialogRequestId = null
                            currentActionHandlingListener = null
                        }

                        override fun onSuccess(request: MessageRequest) {
                        }

                        override fun onResponseStart(request: MessageRequest) {
                        }
                    })
                }
            })
        }
    }

    private var state = RoutineAgentInterface.State.IDLE
    private var currentRoutineRequest: RoutineRequest? = null
    private var textInputRequests = HashSet<String>()
    private var causingPauseRequests = HashMap<String, EventMessageRequest>()
    var textAgent: TextAgentInterface? = null

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        directiveSequencer.addDirectiveHandler(StartDirectiveHandler(this, startDirectiveHandleController))
        directiveSequencer.addDirectiveHandler(StopDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(ContinueDirectiveHandler(this, continueDirectiveHandleController))
        contextManager.setStateProvider(namespaceAndName, this)
        messageSender.addOnSendMessageListener(object : MessageSender.OnSendMessageListener {
            override fun onPreSendMessage(request: MessageRequest) {
                if (request is EventMessageRequest) {
                    if (textInputRequests.remove(request.dialogRequestId)) {
                        // if my text request, skip pause.
                        return
                    }

                    currentRoutineRequest?.let {
                        if (shouldPauseRoutine(request)) {
                            causingPauseRequests[request.dialogRequestId] = request
                            Logger.d(TAG, "[onPreSendMessage] pause routine by: $request")
                            // If the request is caused by the current action, do not cancel current action.
                            it.pause(request.referrerDialogRequestId != it.currentActionDialogRequestId)
                        }
                    }
                }
            }

            override fun onPostSendMessage(request: MessageRequest, status: Status) {
                Logger.d(TAG, "[onPostSendMessage] request: $request, status: $status")
                if (!status.isOk() && request is EventMessageRequest) {
                    if (causingPauseRequests.remove(request.dialogRequestId) != null) {
                        currentRoutineRequest?.let {
                            Logger.d(
                                TAG,
                                "[onPostSendMessage] cancel current request (causing request: $request)"
                            )
                            it.cancel()
                        }
                    }
                }
            }

            private fun shouldPauseRoutine(request: EventMessageRequest): Boolean {
                return (request.namespace == "Text" && request.name == "TextInput") ||
                        (request.namespace == "Display" && request.name == "ElementSelected") ||
                        (request.namespace == "ASR" && request.name == "Recognize")
            }
        })

        directiveSequencer.addOnDirectiveHandlingListener(object :
            DirectiveSequencerInterface.OnDirectiveHandlingListener {
            override fun onRequested(directive: Directive) {
            }

            override fun onCompleted(directive: Directive) {
                if (directive.getNamespace() == "ASR" && directive.getName() == "ExpectSpeech") {
                    currentRoutineRequest?.let {
                        Logger.d(TAG, "pause routine by ASR.ExpectSpeech directive")
                        // If the request is caused by the current action, do not cancel current action.
                        it.pause(directive.getDialogRequestId() != currentRoutineRequest?.currentActionDialogRequestId)
                    }
                }
            }

            override fun onCanceled(directive: Directive) {
            }

            override fun onFailed(directive: Directive, description: String) {
            }
        })

        directiveGroupProcessor.addListener(object : DirectiveGroupProcessorInterface.Listener {
            override fun onPostProcessed(directives: List<Directive>) {
                directives.firstOrNull()?.getDialogRequestId()?.let { dialogRequestId ->
                    if (causingPauseRequests.remove(dialogRequestId) != null) {
                        if (!directives.any { it.getNamespaceAndName() == ContinueDirectiveHandler.CONTINUE || (it.getNamespace() == "ASR" && it.getName() == "NotifyResult") }) {
                            currentRoutineRequest?.cancel()
                        }
                    }
                }
            }
        })
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

        contextSetter.setState(
            namespaceAndName,
            StateContext(getContext()),
            StateRefreshPolicy.ALWAYS,
            contextType,
            stateRequestToken
        )
    }

    override fun start(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[start] $directive")
        val request = currentRoutineRequest
        if (request != null) {
            Logger.d(TAG, "[start] already started routine exist, so try cancel it.")
            request.cancel()
        }

        textAgent?.let {
            val countDownLatch = CountDownLatch(1)
            RoutineRequest(directive, object : RoutineRequestListener {
                override fun onCancel() {
                    Logger.d(TAG, "[start] onCancel()")
                    state = RoutineAgentInterface.State.STOPPED
                    currentRoutineRequest?.let {
                        seamlessFocusManager.cancel(it)
                    }
                    currentRoutineRequest = null
                    sendRoutineStopEvent(directive)
                    listeners.forEach {
                        it.onStopped(directive)
                    }
                }

                override fun onFinish() {
                    Logger.d(TAG, "[start] onFinish()")
                    state = RoutineAgentInterface.State.FINISHED
                    currentRoutineRequest?.let {
                        seamlessFocusManager.cancel(it)
                    }
                    currentRoutineRequest = null
                    sendRoutineFinishEvent(directive)
                    listeners.forEach {
                        it.onFinished(directive)
                    }
                }
            }).apply {
                currentRoutineRequest = this
                val prevState = state
                state = RoutineAgentInterface.State.PLAYING
                listeners.forEach {
                    it.onPlaying(directive)
                }

                contextManager.getContext(object : IgnoreErrorContextRequestor() {
                    override fun onContext(jsonContext: String) {
                        messageSender.newCall(
                            EventMessageRequest.Builder(
                                jsonContext,
                                NAMESPACE,
                                "Started",
                                VERSION.toString()
                            ).payload(JsonObject().apply {
                                addProperty("playServiceId", directive.payload.playServiceId)
                            }.toString())
                                .referrerDialogRequestId(directive.header.dialogRequestId)
                                .build()
                        ).enqueue(object : MessageSender.Callback {
                            override fun onFailure(request: MessageRequest, status: Status) {
                                countDownLatch.countDown()
                                state = prevState
                                currentRoutineRequest = null
                            }

                            override fun onSuccess(request: MessageRequest) {
                                countDownLatch.countDown()
                            }

                            override fun onResponseStart(request: MessageRequest) {
                                countDownLatch.countDown()
                                currentRoutineRequest?.let {
                                    seamlessFocusManager.prepare(it)
                                }
                                start()
                            }
                        })
                    }
                }, namespaceAndName)

                countDownLatch.await()
            }
        }

        return currentRoutineRequest != null
    }

    override fun failed(directive: StartDirectiveHandler.StartDirective, errorMessage: String) {
        Logger.d(TAG, "[failed] $directive")
        sendRoutineFailedEvent(directive.payload.playServiceId, errorMessage, directive.header.dialogRequestId)
    }

    private fun sendRoutineFailedEvent(playServiceId: String, errorMessage: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        EVENT_FAILED,
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                        addProperty("errorMessage", errorMessage)
                    }.toString())
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun sendRoutineStopEvent(directive: StartDirectiveHandler.StartDirective) {
        Logger.d(TAG, "[sendRoutineStopEvent]")
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "Stopped",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                    }.toString())
                        .referrerDialogRequestId(directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun sendRoutineFinishEvent(directive: StartDirectiveHandler.StartDirective) {
        Logger.d(TAG, "[sendRoutineFinishEvent]")
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "Finished",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                    }.toString())
                        .referrerDialogRequestId(directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    override fun stop(directive: StopDirectiveHandler.StopDirective): Boolean {
        Logger.d(TAG, "[stop] $directive")
        return stopInternal(directive.payload.token)
    }

    override fun doContinue(directive: ContinueDirectiveHandler.ContinueDirective): Boolean {
        Logger.d(TAG, "[doContinue] $directive, $causingPauseRequests")

        // cancel doContinue if paused by another request.
        return if (causingPauseRequests.isEmpty()) {
            resumeInternal(directive.payload.token)
        } else {
            false
        }
    }

    override fun failed(
        directive: ContinueDirectiveHandler.ContinueDirective,
        errorMessage: String
    ) {
        Logger.d(TAG, "[failed] $directive")
        sendRoutineFailedEvent(directive.payload.playServiceId, errorMessage, directive.header.dialogRequestId)
    }

    private fun resumeInternal(token: String): Boolean {
        return commandInternal(token) {
            it.doContinue()
        }
    }

    private fun pauseInternal(token: String): Boolean {
        return commandInternal(token) {
            it.pause()
        }
    }

    private fun stopInternal(token: String): Boolean {
        return commandInternal(token) {
            it.cancel()
        }
    }

    private fun commandInternal(
        token: String,
        command: (request: RoutineRequest) -> Unit
    ): Boolean {
        val request = currentRoutineRequest
        if (request != null) {
            if (request.directive.payload.token == token) {
                command(request)
                return true
            }
        }

        return false
    }

    override fun onRendered(templateId: String, dialogRequestId: String) {
    }

    override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
        if (canceled) {
            currentRoutineRequest?.cancelIfCurrentRoutineDisplayCleared(dialogRequestId)
        }
    }

    override fun getState(): RoutineAgentInterface.State = state
    override fun getContext(): RoutineAgentInterface.Context {
        val request = currentRoutineRequest
        return if (request == null) {
            RoutineAgentInterface.Context(null, state, null, null)
        } else {
            RoutineAgentInterface.Context(
                request.directive.payload.token,
                state,
                request.getCurrentActionIndex() + 1,
                request.directive.payload.actions
            )
        }
    }

    override fun addListener(listener: RoutineAgentInterface.RoutineListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: RoutineAgentInterface.RoutineListener) {
        listeners.remove(listener)
    }

    override fun resume(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[resume] $directive")
        return resumeInternal(directive.payload.token)
    }

    override fun pause(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[pause] $directive")
        return pauseInternal(directive.payload.token)
    }

    override fun stop(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[stop] $directive")
        return stopInternal(directive.payload.token)
    }
}