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
package com.skt.nugu.sdk.agent.battery

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*

class DefaultBatteryAgent(
    private val batteryStatusProvider: BatteryStatusProvider,
    contextStateProviderRegistry: ContextStateProviderRegistry
) : CapabilityAgent, SupportedInterfaceContextProvider {
    companion object {
        private const val TAG = "BatteryAgent"

        const val NAMESPACE = "Battery"
        private val VERSION = Version(1,0)
    }

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
    }

    override fun getInterfaceName(): String = NAMESPACE

    internal data class StateContext(
        val level: Int,
        val charging: Boolean
    ) : ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            addProperty("level", level)
            addProperty("charging", charging)
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        val level = batteryStatusProvider.getBatteryLevel().coerceIn(0, 100)
        val charging = batteryStatusProvider.isCharging() ?: false

        contextSetter.setState(
            namespaceAndName,
            StateContext(level, charging),
            StateRefreshPolicy.ALWAYS,
            stateRequestToken
        )
    }
}