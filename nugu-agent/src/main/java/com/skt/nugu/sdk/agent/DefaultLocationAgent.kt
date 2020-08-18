/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.location.Location
import com.skt.nugu.sdk.agent.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.agent.location.LocationProvider
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DefaultLocationAgent(
    contextManager: ContextManagerInterface
) : CapabilityAgent, LocationAgentInterface,
    SupportedInterfaceContextProvider {
    companion object {
        private const val TAG = "DefaultLocationAgent"

        const val NAMESPACE = "Location"

        private val VERSION = Version(1,0)
    }

    private var locationProvider: LocationProvider? = null

    private var contextUpdateLock = ReentrantLock()

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun getInterfaceName(): String = NAMESPACE

    override fun setLocationProvider(provider: LocationProvider) {
        locationProvider = provider
    }

    internal data class StateContext(private val location: Location?): ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            location?.let {
                add("current", JsonObject().apply {
                    addProperty("latitude", it.latitude)
                    addProperty("longitude", it.longitude)
                })
            }
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        contextUpdateLock.withLock {
            contextSetter.setState(
                namespaceAndName,
                StateContext(locationProvider?.getLocation()),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }
}