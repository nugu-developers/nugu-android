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
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CountDownLatch

class RoutineAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val directiveProcessor: DirectiveProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    StartDirectiveHandler.Controller,
    StopDirectiveHandler.Controller,
    ContinueDirectiveHandler.Controller {
    companion object {
        private const val TAG = "RoutineAgent"

        private val VERSION = Version(1,0)
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

    private inner class RoutineRequest(
        val directive: StartDirectiveHandler.StartDirective,
        val listener: RoutineRequestListener
    ) {
        private var currentActionIndex: Int = 0
        private var currentActionHandlingListener : DirectiveGroupHandlingListener? = null
        private var currentActionDialogRequestId: String? = null

        fun start() {
            directive.payload.actions.firstOrNull()?.let {
                doAction(it)
            }
        }

        fun cancel() {
            currentActionDialogRequestId?.let {
                directiveProcessor.cancelDialogRequestId(it)
            }
            listener.onCancel()
        }

        fun doContinue() {
            currentActionDialogRequestId?.let {
                directiveProcessor.cancelDialogRequestId(it)
            }
//            tryStartNextAction()
        }

        private fun tryStartNextAction() {
            Logger.d(TAG, "[tryStartNextAction] $currentActionIndex")
            currentActionIndex++
            if(currentActionIndex < directive.payload.actions.size) {
                doAction(directive.payload.actions[currentActionIndex])
            } else {
                listener.onFinish()
            }
        }

        fun getCurrentActionIndex(): Int = currentActionIndex

        private fun doAction(action: Action) {
            Logger.d(TAG, "[doAction] $action")
            if(action.type == Action.Type.TEXT && action.text != null) {
                doTextAction(action)
            } else if(action.type == Action.Type.DATA && action.data != null){
                doDataAction(action.playServiceId, action.data)
            }
        }

        private fun doTextAction(action: Action) {
            textAgent?.requestTextInput(action.text!!, action.playServiceId, action.token, null,false, object : TextAgentInterface.RequestListener {
                override fun onRequestCreated(dialogRequestId: String) {
                    Logger.d(TAG, "[onRequestCreated] dialogRequestId: $dialogRequestId")
                    currentActionDialogRequestId = dialogRequestId
                    currentActionHandlingListener = DirectiveGroupHandlingListener(dialogRequestId, directiveGroupProcessor,directiveSequencer, object:  DirectiveGroupHandlingListener.OnFinishListener {
                        override fun onFinish() {
                            Logger.d(TAG, "[onFinish] dialogRequestId: $dialogRequestId")
                            tryStartNextAction()
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

        private fun doDataAction(playServiceId: String, data: JsonObject) {
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val request = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "ActionTriggered",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                        add("data", data)
                    }.toString())
                        .referrerDialogRequestId(directive.header.referrerDialogRequestId)
                        .build()

                    Logger.d(TAG, "doDataAction - [onContext] dialogRequestId: ${request.dialogRequestId}")
                    currentActionDialogRequestId = request.dialogRequestId
                    currentActionHandlingListener = DirectiveGroupHandlingListener(request.dialogRequestId, directiveGroupProcessor,directiveSequencer, object:  DirectiveGroupHandlingListener.OnFinishListener {
                        override fun onFinish() {
                            Logger.d(TAG, "doDataAction - [onFinish] dialogRequestId: ${request.dialogRequestId}")
                            tryStartNextAction()
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
    var textAgent: TextAgentInterface? = null

    override fun getInterfaceName(): String = NAMESPACE

    init {
        directiveSequencer.addDirectiveHandler(StartDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(StopDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(ContinueDirectiveHandler(this))
        contextManager.setStateProvider(namespaceAndName,this)
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
        val context = if(request == null) {
            StateContext(
                null, state, null, null
            )
        } else {
            StateContext(
                request.directive.payload.token, state, request.getCurrentActionIndex() + 1, request.directive.payload.actions
            )
        }

        contextSetter.setState(namespaceAndName, context, StateRefreshPolicy.ALWAYS, contextType, stateRequestToken)
    }

    override fun start(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[start] $directive")
        val request = currentRoutineRequest
        if(request != null) {
            Logger.w(TAG, "[start] failed: already started routine exist")
            return false
        } else {
            textAgent?.let {
                val countDownLatch = CountDownLatch(1)
                RoutineRequest(directive,object: RoutineRequestListener {
                    override fun onCancel() {
                        Logger.d(TAG, "[start] onCancel()")
                        state = RoutineAgentInterface.State.STOPPED
                        currentRoutineRequest = null
                        sendRoutineStopEvent(directive)
                    }

                    override fun onFinish() {
                        Logger.d(TAG, "[start] onFinish()")
                        state = RoutineAgentInterface.State.FINISHED
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
            if (request.directive.payload.playServiceId == directive.payload.playServiceId &&
                request.directive.payload.token == directive.payload.token
            ) {
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
            if (request.directive.payload.playServiceId == directive.payload.playServiceId &&
                request.directive.payload.token == directive.payload.token
            ) {
                request.doContinue()
                return true
            }
        }

        return false
    }
}