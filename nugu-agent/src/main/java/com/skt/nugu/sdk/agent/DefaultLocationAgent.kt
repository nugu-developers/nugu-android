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
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.agent.location.LocationProvider
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
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

    private var isFirstContextUpdate = true
    private var lastUpdatedLocation: Location? = null
    private var contextUpdateLock = ReentrantLock()

    init {
        contextManager.setStateProvider(namespaceAndName, this, buildCompactContext().toString())
    }

    override fun getInterfaceName(): String = NAMESPACE

    override fun setLocationProvider(provider: LocationProvider) {
        locationProvider = provider
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextUpdateLock.withLock {
            val location = locationProvider?.getLocation()

            val context = if (isFirstContextUpdate || location != lastUpdatedLocation) {
                isFirstContextUpdate = false
                lastUpdatedLocation = location
                buildContext(location)
            } else {
                null
            }

            contextSetter.setState(
                namespaceAndName,
                context,
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    private fun buildCompactContext() = JsonObject().apply {
        addProperty(
            "version",
            VERSION.toString()
        )
    }

    private fun buildContext(location: Location?): String = JsonObject().apply {
        addProperty(
            "version",
            VERSION.toString()
        )

        location?.let {
            add("current", JsonObject().apply {
                addProperty("latitude", it.latitude)
                addProperty("longitude", it.longitude)
            })
        }
    }.toString()
}