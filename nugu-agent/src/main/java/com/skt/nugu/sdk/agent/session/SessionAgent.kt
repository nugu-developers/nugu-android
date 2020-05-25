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
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import java.util.concurrent.Executors

class SessionAgent(
    contextStateProviderRegistry: ContextStateProviderRegistry,
    private val sessionManager: SessionManagerInterface
)
    : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SetDirectiveHandler.Controller {

    companion object {
        const val NAMESPACE = "Session"
        private val VERSION = Version(1,0)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var currentSessions: List<SessionManagerInterface.Session>? = null

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this, buildCompactContext().toString())
    }

    override fun getInterfaceName(): String = NAMESPACE

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            val sessions = sessionManager.getActiveSessions()
            if (sessions == currentSessions) {
                contextSetter.setState(
                    namespaceAndName,
                    null,
                    StateRefreshPolicy.ALWAYS,
                    stateRequestToken
                )
            } else {
                contextSetter.setState(
                    namespaceAndName,
                    buildContext(),
                    StateRefreshPolicy.ALWAYS,
                    stateRequestToken
                )
            }
            currentSessions = sessions
        }
    }

    private fun buildContext() = buildCompactContext().apply {
        add("list", JsonArray().apply {
            sessionManager.getActiveSessions().forEach {
                add(JsonObject().apply {
                    addProperty("sessionId", it.sessionId)
                    addProperty("playServiceId", it. playServiceId)
                })
            }
        })
    }.toString()

    private fun buildCompactContext(): JsonObject = JsonObject().apply {
        addProperty("version", VERSION.toString())
    }

    override fun set(directive: SetDirectiveHandler.SetDirective) {
        executor.submit {
            sessionManager.set(directive.header.dialogRequestId,
                SessionManagerInterface.Session(
                    directive.payload.sessionId,
                    directive.payload.playServiceId
                )
            )
        }
    }
}