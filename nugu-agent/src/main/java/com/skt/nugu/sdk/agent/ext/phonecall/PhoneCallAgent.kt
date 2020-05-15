package com.skt.nugu.sdk.agent.ext.phonecall

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.phonecall.handler.SendCandidatesDirectiveHandler
import com.skt.nugu.sdk.agent.ext.phonecall.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PhoneCallAgent(
    private val client: PhoneCallClient,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    contextGetter: ContextGetterInterface,
    messageSender: MessageSender,
    directiveSequencer: DirectiveSequencerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.Controller
{
    companion object {
        const val NAMESPACE = "PhoneCall"
        val VERSION = Version(1,0)
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()
    private var currentContext: Context? = null

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this, buildCompactContext().toString())

        directiveSequencer.apply {
            addDirectiveHandler(SendCandidatesDirectiveHandler(this@PhoneCallAgent, messageSender, contextGetter))
        }
    }

    private fun buildCompactContext(): JsonObject = JsonObject().apply {
        addProperty("version", VERSION.toString())
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            val context = client.getContext()
            if(currentContext != context) {
                val result = contextSetter.setState(namespaceAndName, buildCompactContext().apply {
                    addProperty("state", context.state.name)
                    context.intent?.let {
                        addProperty("intent", it.name)
                    }
                    context.callType?.let {
                        addProperty("callType",it.name)
                    }
                    context.candidates?.let {
                        add("candidates", JsonArray().apply {
                            it.forEach {
                                add(it.toJson())
                            }
                        })
                    }
                }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)

                if(result == ContextSetterInterface.SetStateResult.SUCCESS) {
                    currentContext = context
                }
            } else {
                contextSetter.setState(namespaceAndName, null, StateRefreshPolicy.ALWAYS, stateRequestToken)
            }
        }
    }

    override fun getCandidateList(payload: SendCandidatesPayload): List<Person>? {
        return executor.submit(Callable {
            client.getCandidateList(payload)
        }).get()
    }
}
