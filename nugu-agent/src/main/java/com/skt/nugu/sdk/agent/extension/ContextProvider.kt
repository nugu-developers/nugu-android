/**
 * Copyright (c) 2022 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.extension

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger

internal abstract class ContextProvider: SupportedInterfaceContextProvider {
    companion object {
        private const val TAG = "ExtensionAgent::ContextProvider"
    }

    internal data class StateContext(val data: String?): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", ExtensionAgent.VERSION.toString())
            }

            val CompactContextState = object : BaseContextState {
                private val value: String by lazy {
                    buildCompactContext().toString()
                }

                override fun value(): String = value
            }
        }

        override fun value(): String = buildCompactContext().apply {
            data?.let {
                try {
                    add("data", JsonParser.parseString(it).asJsonObject)
                } catch (th: Throwable) {
                    Logger.e(TAG, "[buildContext] error to create data json object.", th)
                }
            }
        }.toString()
    }

    override val namespaceAndName: NamespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE,
        ExtensionAgent.NAMESPACE
    )

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        contextSetter.setState(
            namespaceAndName,
            if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                getClient()?.getData()
            ),
            StateRefreshPolicy.ALWAYS,
            contextType,
            stateRequestToken
        )
    }

    abstract fun getClient(): ExtensionAgentInterface.Client?
}