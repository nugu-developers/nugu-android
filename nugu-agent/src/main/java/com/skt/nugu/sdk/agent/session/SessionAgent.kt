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

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
        directiveSequencer.addDirectiveHandler(SetDirectiveHandler(this))
    }

    internal data class StateContext(
        val sessions: Set<SessionManagerInterface.Session>
    ): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            add("list", JsonArray().apply {
                sessions.forEach { session ->
                    add(JsonObject().apply {
                        addProperty("sessionId", session.sessionId)
                        addProperty("playServiceId", session.playServiceId)
                    })
                }
            })
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
            val state = if(contextType == ContextType.COMPACT) {
                StateContext.CompactContextState
            } else {
                StateContext(HashSet(sessionManager.getActiveSessions().values))
            }

            contextSetter.setState(
                namespaceAndName,
                state,
                StateRefreshPolicy.ALWAYS,
                contextType,
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