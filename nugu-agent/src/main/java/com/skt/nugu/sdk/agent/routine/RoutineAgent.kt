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
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RoutineAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val directiveProcessor: DirectiveProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val seamlessFocusManager: SeamlessFocusManagerInterface,
    private val playSynchronizer: PlaySynchronizerInterface
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    StartDirectiveHandler.Controller,
    StopDirectiveHandler.Controller,
    ContinueDirectiveHandler.Controller,
    DisplayAgentInterface.Listener {
    companion object {
        private const val TAG = "RoutineAgent"

        private val VERSION = Version(1,1)
        const val NAMESPACE = "Routine"
    }

    internal data class StateContext(
        private val token: String?,
        private val routineActivity: RoutineAgentInterface.State,
        private val currentAction: Int?,
        private val actions: Array<Action>?
    ): BaseContextState {
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
            token?.let {
                addProperty("token", it)
            }
            addProperty("routineActivity", routineActivity.name)
            currentAction?.let {
                addProperty("currentAction", it)
            }
            actions?.let {
                add("actions", JsonArray().apply {
                    it.forEach {
                        add(it.toJsonObject())
                    }
                })
            }
        }.toString()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StateContext

            if (token != other.token) return false
            if (routineActivity != other.routineActivity) return false
            if (currentAction != other.currentAction) return false
            if (actions != null) {
                if (other.actions == null) return false
                if (!actions.contentEquals(other.actions)) return false
            } else if (other.actions != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = token?.hashCode() ?: 0
            result = 31 * result + routineActivity.hashCode()
            result = 31 * result + (currentAction?.hashCode() ?: 0)
            result = 31 * result + (actions?.contentHashCode() ?: 0)
            return result
        }
    }

    interface RoutineRequestListener {
        fun onCancel()
        fun onFinish()
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private inner class RoutineRequest(
        val directive: StartDirectiveHandler.StartDirective,
        val listener: RoutineRequestListener
    ): SeamlessFocusManagerInterface.Requester {
        private var currentActionIndex: Int = 0
        private var currentActionHandlingListener : DirectiveGroupHandlingListener? = null
        private var currentActionDialogRequestId: String? = null
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

        fun cancel() {
            Logger.d(TAG, "[RoutineRequest] cancel: $isCanceled")
            if(isCanceled) {
                return
            }
            isCanceled = true
            cancelCurrentAction()
            listener.onCancel()
        }

        private fun cancelCurrentAction() {
            currentActionDialogRequestId?.let {
                directiveProcessor.cancelDialogRequestId(it)
                playSynchronizer.cancelSync(it)
            }
        }

        fun pause() {
            Logger.d(TAG, "[RoutineRequest] pause")
            cancelCurrentAction()
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction = null
            scheduledFutureForCancelByInterrupt?.cancel(true)
            scheduledFutureForCancelByInterrupt = executor.schedule({
                cancel()
            }, 60, TimeUnit.SECONDS)
            isPaused = true
        }

        fun cancelIfCurrentRoutineDisplayCleared(dialogRequestId: String) {
            Logger.d(TAG, "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $currentActionDialogRequestId, $dialogRequestId")
            if(currentActionDialogRequestId == dialogRequestId) {
                Logger.d(TAG, "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $dialogRequestId")
                cancel()
            }
        }

        fun doContinue() {
            Logger.d(TAG, "[RoutineRequest] doContinue")
            isPaused = false
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction = null
            scheduledFutureForCancelByInterrupt?.cancel(true)
            scheduledFutureForCancelByInterrupt = null
            tryStartNextAction()
        }

        private fun tryStartNextAction() {
            Logger.d(TAG, "[RoutineRequest] tryStartNextAction - $currentActionIndex, isPaused: $isPaused")
            if(isPaused || isCanceled) {
                return
            }

            currentActionIndex++
            if(currentActionIndex < directive.payload.actions.size) {
                doAction(directive.payload.actions[currentActionIndex])
            } else {
                listener.onFinish()
            }
        }

        fun getCurrentActionIndex(): Int = currentActionIndex

        private fun doAction(action: Action) {
            Logger.d(TAG, "[RoutineRequest] doAction - $action")
            if(action.type == Action.Type.TEXT && action.text != null) {
                doTextAction(action)
            } else if(action.type == Action.Type.DATA && action.data != null){
                doDataAction(action)
            }
        }

        private fun doTextAction(action: Action) {
            textAgent?.requestTextInput(action.text!!, action.playServiceId, action.token, null,false, object : TextAgentInterface.RequestListener {
                override fun onRequestCreated(dialogRequestId: String) {
                    Logger.d(TAG, "[onRequestCreated] dialogRequestId: $dialogRequestId")
                    textInputRequests.add(dialogRequestId)
                    currentActionDialogRequestId = dialogRequestId
                    currentActionHandlingListener = DirectiveGroupHandlingListener(dialogRequestId, directiveGroupProcessor,directiveSequencer, object:  DirectiveGroupHandlingListener.OnDirectiveResultListener {
                        override fun onFinish(isExistCanceledOrFailed: Boolean) {
                            Logger.d(TAG, "[onFinish] dialogRequestId: $dialogRequestId")
                            if(isExistCanceledOrFailed) {
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
                        .referrerDialogRequestId(directive.header.referrerDialogRequestId)
                        .build()

                    Logger.d(TAG, "doDataAction - [onContext] dialogRequestId: ${request.dialogRequestId}")
                    currentActionDialogRequestId = request.dialogRequestId
                    currentActionHandlingListener = DirectiveGroupHandlingListener(request.dialogRequestId, directiveGroupProcessor,directiveSequencer, object:  DirectiveGroupHandlingListener.OnDirectiveResultListener {
                        override fun onFinish(isExistCanceledOrFailed: Boolean) {
                            Logger.d(
                                TAG,
                                "doDataAction - [onFinish] dialogRequestId: ${request.dialogRequestId}"
                            )
                            if(isExistCanceledOrFailed) {
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

    override fun getInterfaceName(): String = NAMESPACE

    init {
        directiveSequencer.addDirectiveHandler(StartDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(StopDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(ContinueDirectiveHandler(this))
        contextManager.setStateProvider(namespaceAndName,this)
        messageSender.addOnSendMessageListener(object : MessageSender.OnSendMessageListener {
            override fun onPreSendMessage(request: MessageRequest) {
                if (request is EventMessageRequest) {
                    if(textInputRequests.remove(request.dialogRequestId)) {
                        // if my text request, skip pause.
                        return
                    }

                    if(shouldPauseRoutine(request)) {
                        causingPauseRequests[request.dialogRequestId] = request
                        currentRoutineRequest?.let {
                            Logger.d(TAG, "[onPreSendMessage] pause routine by: $request")
                            it.pause()
                        }
                    }
                }
            }

            override fun onPostSendMessage(request: MessageRequest, status: Status) {
                Logger.d(TAG, "[onPostSendMessage] request: $request, status: $status")
                if(!status.isOk() && request is EventMessageRequest) {
                    if(causingPauseRequests.remove(request.dialogRequestId) != null) {
                        currentRoutineRequest?.let {
                            Logger.d(TAG, "[onPostSendMessage] cancel current request (causing request: $request)")
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

        directiveSequencer.addOnDirectiveHandlingListener(object : DirectiveSequencerInterface.OnDirectiveHandlingListener {
            override fun onRequested(directive: Directive) {
            }

            override fun onCompleted(directive: Directive) {
                if(directive.getNamespace() == "ASR" && directive.getName() == "ExpectSpeech") {
                    currentRoutineRequest?.let {
                        Logger.d(TAG, "pause routine by ASR.ExpectSpeech directive")
                        it.pause()
                    }
                }
            }

            override fun onCanceled(directive: Directive) {
            }

            override fun onFailed(directive: Directive, description: String) {
            }
        })

        directiveGroupProcessor.addPostProcessedListener(object : DirectiveGroupProcessorInterface.Listener {
            override fun onReceiveDirectives(directives: List<Directive>) {
                directives.firstOrNull()?.getDialogRequestId()?.let {dialogRequestId ->
                    if(causingPauseRequests.remove(dialogRequestId) != null) {
                        if(!directives.any { it.getNamespaceAndName() ==  ContinueDirectiveHandler.CONTINUE || (it.getNamespace() == "ASR" && it.getName() == "NotifyResult")}) {
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

        val request = currentRoutineRequest
        val context = if (request == null) {
            StateContext(
                null, state, null, null
            )
        } else {
            StateContext(
                request.directive.payload.token,
                if (!request.isPaused) state else RoutineAgentInterface.State.INTERRUPTED,
                request.getCurrentActionIndex() + 1,
                request.directive.payload.actions
            )
        }

        contextSetter.setState(namespaceAndName, context, StateRefreshPolicy.ALWAYS, contextType, stateRequestToken)
    }

    override fun start(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[start] $directive")
        val request = currentRoutineRequest
        if(request != null) {
            Logger.d(TAG, "[start] already started routine exist, so try cancel it.")
            request.cancel()
        }

        textAgent?.let {
            val countDownLatch = CountDownLatch(1)
            RoutineRequest(directive,object: RoutineRequestListener {
                override fun onCancel() {
                    Logger.d(TAG, "[start] onCancel()")
                    state = RoutineAgentInterface.State.STOPPED
                    currentRoutineRequest?.let {
                        seamlessFocusManager.cancel(it)
                    }
                    currentRoutineRequest = null
                    sendRoutineStopEvent(directive)
                }

                override fun onFinish() {
                    Logger.d(TAG, "[start] onFinish()")
                    state = RoutineAgentInterface.State.FINISHED
                    currentRoutineRequest?.let {
                        seamlessFocusManager.cancel(it)
                    }
                    currentRoutineRequest = null
                    sendRoutineFinishEvent(directive)
                }
            }).apply {
                currentRoutineRequest = this
                val prevState = state
                state = RoutineAgentInterface.State.PLAYING

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
                ).enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }

                    override fun onResponseStart(request: MessageRequest) {
                    }
                })
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
                ).enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }

                    override fun onResponseStart(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)
    }

    override fun stop(directive: StopDirectiveHandler.StopDirective): Boolean {
        Logger.d(TAG, "[stop] $directive")
        val request = currentRoutineRequest
        if(request != null) {
            if (request.directive.payload.token == directive.payload.token) {
                request.cancel()
                return true
            }
        }

        return false
    }

    override fun doContinue(directive: ContinueDirectiveHandler.ContinueDirective): Boolean {
        Logger.d(TAG, "[doContinue] $directive")
        val request = currentRoutineRequest
        if(request != null) {
            if (request.directive.payload.token == directive.payload.token) {
                request.doContinue()
                return true
            }
        }

        return false
    }

    override fun onRendered(templateId: String, dialogRequestId: String) {
    }

    override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
        if(canceled) {
            currentRoutineRequest?.cancelIfCurrentRoutineDisplayCleared(dialogRequestId)
        }
    }
}