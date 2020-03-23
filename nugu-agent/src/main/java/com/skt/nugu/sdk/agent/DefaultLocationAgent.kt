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
import com.skt.nugu.sdk.agent.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.agent.location.LocationProvider
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider

class DefaultLocationAgent : CapabilityAgent, LocationAgentInterface, ContextStateProvider {
    companion object {
        private const val TAG = "DefaultLocationAgent"

        const val NAMESPACE = "Location"

        private val VERSION = Version(1,0)
    }

    private var locationProvider: LocationProvider? = null

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    override fun setLocationProvider(provider: LocationProvider) {
        locationProvider = provider
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(
            namespaceAndName,
            buildContext(),
            StateRefreshPolicy.ALWAYS,
            stateRequestToken
        )
    }

    private fun buildContext(): String = JsonObject().apply {
        addProperty(
            "version",
            VERSION.toString()
        )
        val location = locationProvider?.getLocation()
        if (location != null) {
            add("current", JsonObject().apply {
                addProperty("latitude", location.latitude)
                addProperty("longitude", location.longitude)
            })
        }
    }.toString()
}