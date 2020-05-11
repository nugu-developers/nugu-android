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

package com.skt.nugu.sdk.agent.ext.navigation

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
import java.util.concurrent.Executors

class NavigationAgent(
    private val client: NavigationClient,
    contextStateProviderRegistry: ContextStateProviderRegistry
) : CapabilityAgent
    , SupportedInterfaceContextProvider
{
    companion object {
        const val NAMESPACE = "Navigation"
        val VERSION = Version(1,0)
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()
    private var currentContext: Context? = null

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this, buildCompactContext().toString())
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
                    add("destination", context.destination.toJsonObject())
                    add("route", context.route.toJsonObject())
                    add("mode", context.mode.toJsonObject())
                    addProperty("carrier", context.carrier)
                }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)

                if(result == ContextSetterInterface.SetStateResult.SUCCESS) {
                    currentContext = context
                }
            } else {
                contextSetter.setState(namespaceAndName, null, StateRefreshPolicy.ALWAYS, stateRequestToken)
            }
        }
    }
}