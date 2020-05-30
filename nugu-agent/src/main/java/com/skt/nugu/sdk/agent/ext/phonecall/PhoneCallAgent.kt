package com.skt.nugu.sdk.agent.ext.phonecall

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.phonecall.handler.*
import com.skt.nugu.sdk.agent.ext.phonecall.payload.*
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PhoneCallAgent(
    private val client: PhoneCallClient,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    directiveSequencer: DirectiveSequencerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.Controller
    , MakeCallDirectiveHandler.Controller
    , EndCallDirectiveHandler.Controller
    , AcceptCallDirectiveHandler.Controller
    , BlockingIncomingCallDirectiveHandler.Controller
    , PhoneCallClient.OnStateChangeListener {
    companion object {
        const val NAMESPACE = "PhoneCall"
        val VERSION = Version(1, 0)
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()
    private var state = State.IDLE

    data class StateContext(val context: Context, val state: State) : ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            addProperty("state", state.name)
            context.intent?.let {
                addProperty("intent", it.name)
            }
            context.callType?.let {
                addProperty("callType", it.name)
            }
            context.candidates?.let {
                add("candidates", JsonArray().apply {
                    it.forEach {
                        add(it.toJson())
                    }
                })
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
                    contextGetter
                )
            )
            addDirectiveHandler(
                MakeCallDirectiveHandler(
                    this@PhoneCallAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(EndCallDirectiveHandler(this@PhoneCallAgent))
            addDirectiveHandler(AcceptCallDirectiveHandler(this@PhoneCallAgent))
            addDirectiveHandler(BlockingIncomingCallDirectiveHandler(this@PhoneCallAgent))
        }

        client.addOnStateChangeListener(this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(namespaceAndName, StateContext(client.getContext(), state), StateRefreshPolicy.ALWAYS, stateRequestToken)
        }
    }

    override fun getCandidateList(payload: SendCandidatesPayload): List<Person>? {
        return executor.submit(Callable {
            client.getCandidateList(payload)
        }).get()
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

    override fun blockingIncomingCall(payload: BlockingIncomingCallPayload) {
        executor.submit {
            client.blockingIncomingCall(payload)
        }
    }

    override fun onIdle(playServiceId: String) {
        executor.submit {
            if (state == State.IDLE) {
                return@submit
            }

            state = State.IDLE

            // CallEnded
            sendCallEndedEvent(playServiceId)
        }
    }

    override fun onOutgoing() {
        executor.submit {
            if (state == State.IDLE) {

            } else {
                // Invalid Transition
            }
            state = State.OUTGOING
        }
    }

    override fun onEstablished(playServiceId: String) {
        executor.submit {
            if (state == State.OUTGOING || state == State.INCOMING) {
                // CallEstablished
                sendCallEstablishedEvent(playServiceId)
            } else {
                // Invalid Transition
            }
            state = State.ESTABLISHED
        }
    }

    override fun onIncoming(
        playServiceId: String,
        callerName: String,
        missedInCallHistory: String
    ) {
        executor.submit {
            if (state == State.IDLE) {
                // CallArrived
                sendCallArrivedEvent(playServiceId, callerName, missedInCallHistory)
            } else {
                // Invalid Transition
            }
            state = State.INCOMING
        }
    }

    private fun sendCallArrivedEvent(
        playServiceId: String,
        callerName: String,
        missedInCallHistory: String
    ) {
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, "CallArrived", VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                            addProperty("callerName", callerName)
                            addProperty("missedInCallHistory", missedInCallHistory)
                        }.toString())
                        .build()
                )
            }
        })
    }

    private fun sendCallEndedEvent(
        playServiceId: String
    ) {
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, "CallEnded", VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                        }.toString())
                        .build()
                )
            }
        })
    }

    private fun sendCallEstablishedEvent(
        playServiceId: String
    ) {
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, "CallEstablished", VERSION.toString())
                        .payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                        }.toString())
                        .build()
                )
            }
        })
    }
}
