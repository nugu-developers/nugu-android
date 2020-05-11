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

package com.skt.nugu.sdk.agent.ext.message

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.message.handler.SendCandidatesDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.SendMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.ext.message.payload.SendMessagePayload
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MessageAgent(
    private val client: MessageClient,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    contextGetter: ContextGetterInterface,
    messageSender: MessageSender,
    directiveSequencer: DirectiveSequencerInterface
)
    : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.Controller
    , SendMessageDirectiveHandler.Controller {
    companion object {
        private const val TAG = "MessageAgent"
        const val NAMESPACE = "Message"

        val VERSION = Version(1,0)
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()
    private var currentContext: Context? = null

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this, buildCompactContext().toString())

        directiveSequencer.apply {
            addDirectiveHandler(SendCandidatesDirectiveHandler(this@MessageAgent, messageSender, contextGetter))
            addDirectiveHandler(SendMessageDirectiveHandler(this@MessageAgent, messageSender, contextGetter))
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
                currentContext = context
                    contextSetter.setState(namespaceAndName, buildCompactContext().apply {
                        context.candidates?.let {
                            add("candidates", JsonArray().apply {
                                it.forEach {
                                    add(it.toJsonObject())
                                }
                            })
                        }
                    }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
            } else {
                contextSetter.setState(namespaceAndName, null, StateRefreshPolicy.ALWAYS, stateRequestToken)
            }
        }
    }

    override fun getCandidateList(payload: SendCandidatesPayload): List<Candidate>? {
        return executor.submit(Callable {
            client.getCandidateList(payload)
        }).get()
    }

    override fun sendMessage(payload: SendMessagePayload, callback: EventCallback) {
        executor.submit {
            client.sendMessage(payload, callback)
        }
    }
}