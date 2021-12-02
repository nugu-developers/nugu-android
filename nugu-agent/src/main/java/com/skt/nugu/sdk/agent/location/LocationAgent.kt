/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent.location

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LocationAgent(
    contextStateProviderRegistry: ContextStateProviderRegistry,
    private val locationProvider: LocationProvider
) : CapabilityAgent,
    SupportedInterfaceContextProvider {
    companion object {
        private const val TAG = "DefaultLocationAgent"

        const val NAMESPACE = "Location"

        internal val VERSION = Version(1, 0)
    }

    private var contextUpdateLock = ReentrantLock()

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
    }

    internal data class StateContext(private val location: Location?) : BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            location?.let {
                add("current", JsonObject().apply {
                    addProperty("latitude", it.latitude)
                    addProperty("longitude", it.longitude)
                })
            }
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
        contextUpdateLock.withLock {
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                    locationProvider.getLocation()
                ),
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        }
    }
}