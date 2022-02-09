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

package com.skt.nugu.sdk.agent.permission

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.message.Header
import java.util.*
import java.util.concurrent.Executors

class PermissionAgent(
    contextManager: ContextManagerInterface,
    private val delegate: PermissionDelegate
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    RequestPermissionDirectiveHandler.Controller {
    companion object {
        private const val TAG = "PermissionAgent"
        const val NAMESPACE = "Permission"

        val VERSION = Version(1, 1)
    }

    private val executor = Executors.newSingleThreadExecutor()

    internal data class StateContext(private val permissions: Map<PermissionType, PermissionState>): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            add("permissions", JsonArray().apply {
                permissions.forEach {
                    add(JsonObject().apply {
                        addProperty("name", it.key.name)
                        addProperty("state", it.value.name)
                    })
                }
            })
        }.toString()
    }

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

        init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        executor.submit {
            if(contextType == ContextType.COMPACT) {
                contextSetter.setState(
                    namespaceAndName,
                    StateContext.CompactContextState,
                    StateRefreshPolicy.NEVER,
                    contextType,
                    stateRequestToken
                )
            } else {
                val permissions = HashMap<PermissionType, PermissionState>().apply {
                    delegate.supportedPermissions.forEach {
                        put(it, delegate.getPermissionState(it))
                    }
                }

                contextSetter.setState(
                    namespaceAndName,
                    StateContext(permissions),
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    override fun requestPermission(
        header: Header,
        payload: RequestPermissionDirectiveHandler.Payload
    ) {
        executor.submit {
            delegate.requestPermissions(payload.permissions)
        }
    }
}