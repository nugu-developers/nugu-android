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

package com.skt.nugu.sdk.agent.ext.phonecall

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.phonecall.handler.*
import com.skt.nugu.sdk.agent.ext.phonecall.payload.*
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PhoneCallAgent(
    private val client: PhoneCallClient,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val focusManager: FocusManagerInterface,
    private val channelName: String,
    private val focusObserver: ChannelObserver? = null,
    private val enableSendEvent: Boolean = true,
    directiveSequencer: DirectiveSequencerInterface,
    interactionControlManager: InteractionControlManagerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.Controller
    , MakeCallDirectiveHandler.Controller
    , EndCallDirectiveHandler.Controller
    , AcceptCallDirectiveHandler.Controller
    , BlockIncomingCallDirectiveHandler.Controller
    , PhoneCallClient.OnStateChangeListener
    , ChannelObserver
{
    companion object {
        private const val TAG = "PhoneCallAgent"

        const val NAMESPACE = "PhoneCall"
        val VERSION = Version(1, 0)

        private const val NAME_CALL_ARRIVED = "CallArrived"
        private const val NAME_CALL_ENDED = "CallEnded"
        private const val NAME_CALL_ESTABLISHED = "CallEstablished"
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()
    private var state = State.IDLE
    private var focusState = FocusState.NONE

    data class StateContext(val context: Context) : ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            addProperty("state", context.state.name)
            context.template?.let {
                add("template", it.toJson())
            }
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    init {
        contextStateProviderRegistry.setStateProvider(
            namespaceAndName,
            this
        )

        directiveSequencer.apply {
            addDirectiveHandler(
                SendCandidatesDirectiveHandler(
                    this@PhoneCallAgent,
                    messageSender,
                    contextGetter,
                    interactionControlManager,
                    namespaceAndName
                )
            )
            addDirectiveHandler(
                MakeCallDirectiveHandler(
                    this@PhoneCallAgent,
                    messageSender,
                    contextGetter,
                    namespaceAndName
                )
            )
            addDirectiveHandler(EndCallDirectiveHandler(this@PhoneCallAgent))
            addDirectiveHandler(AcceptCallDirectiveHandler(this@PhoneCallAgent))
            addDirectiveHandler(BlockIncomingCallDirectiveHandler(this@PhoneCallAgent))
        }

        client.addOnStateChangeListener(this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(namespaceAndName, StateContext(client.getContext()), StateRefreshPolicy.ALWAYS, stateRequestToken)
        }
    }

    override fun sendCandidates(
        payload: SendCandidatesPayload,
        callback: SendCandidatesDirectiveHandler.Callback
    ) {
        executor.submit(Callable {
            client.sendCandidates(payload, callback)
        })
    }

    override fun makeCall(payload: MakeCallPayload, callback: MakeCallDirectiveHandler.Callback) {
        executor.submit {
            client.makeCall(payload, callback)
        }
    }

    override fun endCall(payload: EndCallPayload) {
        executor.submit {
            client.endCall(payload)
        }
    }

    override fun acceptCall(payload: AcceptCallPayload) {
        executor.submit {
            client.acceptCall(payload)
        }
    }

    override fun blockIncomingCall(payload: BlockIncomingCallPayload) {
        executor.submit {
            client.blockIncomingCall(payload)
        }
    }

    override fun onIdle(playServiceId: String) {
        executor.submit {
            focusManager.releaseChannel(channelName,this)

            if (state == State.IDLE) {
                return@submit
            }

            state = State.IDLE

            // CallEnded
            if(enableSendEvent) {
                sendCallEndedEvent(playServiceId)
            }
        }
    }

    override fun onOutgoing() {
        executor.submit {
            focusManager.acquireChannel(channelName, this, NAMESPACE)

            if (state == State.IDLE) {

            } else {
                // Invalid Transition
            }
            state = State.OUTGOING
        }
    }

    override fun onEstablished(playServiceId: String) {
        executor.submit {
            focusManager.acquireChannel(channelName, this, NAMESPACE)

            if (state == State.OUTGOING || state == State.INCOMING) {
                // CallEstablished
                if(enableSendEvent) {
                    sendCallEstablishedEvent(playServiceId)
                }
            } else {
                // Invalid Transition
            }
            state = State.ESTABLISHED
        }
    }

    override fun onIncoming(
        playServiceId: String,
        caller: Caller
    ) {
        executor.submit {
            focusManager.acquireChannel(channelName, this, NAMESPACE)

            if (state == State.IDLE) {
                // CallArrived
                if(enableSendEvent) {
                    sendCallArrivedEvent(playServiceId, caller)
                }
            } else {
                // Invalid Transition
            }
            state = State.INCOMING
        }
    }

    private fun sendCallArrivedEvent(
        playServiceId: String,
        caller: Caller
    ) {
        Logger.d(TAG, "[sendCallArrivedEvent] playServiceId: $playServiceId, caller: $caller")
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, NAME_CALL_ARRIVED, VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                            add("caller", caller.toJson())
                        }.toString())
                        .build()
                ).enqueue(object : MessageSender.Callback{
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }
                })
            }
        })
    }

    private fun sendCallEndedEvent(
        playServiceId: String
    ) {
        Logger.d(TAG, "[sendCallEndedEvent] playServiceId: $playServiceId")
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, NAME_CALL_ENDED, VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                        }.toString())
                        .build()
                ).enqueue(object : MessageSender.Callback{
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)
    }

    private fun sendCallEstablishedEvent(
        playServiceId: String
    ) {
        Logger.d(TAG, "[sendCallEstablishedEvent] playServiceId: $playServiceId")
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, NAME_CALL_ESTABLISHED, VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                        }.toString())
                        .build()
                    ).enqueue(object : MessageSender.Callback{
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)
    }

    override fun onFocusChanged(newFocus: FocusState) {
        executor.submit {
            Logger.d(TAG, "[onFocusChanged] focusState: $focusState, newFocus: $newFocus")
            if(focusState == newFocus) {
                return@submit
            }
            focusState = newFocus

            focusObserver?.onFocusChanged(newFocus)
        }
    }
}
