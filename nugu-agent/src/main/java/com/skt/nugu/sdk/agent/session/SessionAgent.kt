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

package com.skt.nugu.sdk.agent.session

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.session.handler.SetDirectiveHandler
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class SessionAgent(
    contextStateProviderRegistry: ContextStateProviderRegistry,
    directiveSequencer: DirectiveSequencerInterface,
    private val sessionManager: SessionManagerInterface
)
    : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SetDirectiveHandler.Controller {

    companion object {
        private const val TAG = "SessionAgent"
        const val NAMESPACE = "Session"
        val VERSION = Version(1,0)
    }

    private val executor = Executors.newSingleThreadExecutor()

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
        directiveSequencer.addDirectiveHandler(SetDirectiveHandler(this))
    }

    override fun getInterfaceName(): String = NAMESPACE

    internal data class StateContext(
        val sessions: List<SessionManagerInterface.Session>
    ): ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            add("list", JsonArray().apply {
                sessions.forEach {
                    add(JsonObject().apply {
                        addProperty("sessionId", it.sessionId)
                        addProperty("playServiceId", it. playServiceId)
                    })
                }
            })
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                StateContext(sessionManager.getActiveSessions()),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    override fun set(directive: SetDirectiveHandler.SetDirective) {
        executor.submit {
            Logger.d(TAG, "[set] $directive")
            sessionManager.set(directive.header.dialogRequestId,
                SessionManagerInterface.Session(
                    directive.payload.sessionId,
                    directive.payload.playServiceId
                )
            )
        }
    }
}