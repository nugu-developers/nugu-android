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
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationClient
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import java.util.HashMap
import java.util.concurrent.Executors

object DefaultDelegationAgent {
    private const val TAG = "DelegationAgent"

    val FACTORY = object : DelegationAgentFactory {
        override fun create(
            defaultClient: DelegationClient
        ): AbstractDelegationAgent =
            Impl(
                defaultClient
            )
    }

    internal data class DelegatePayload(
        @SerializedName("appId")
        val appId: String,
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("data")
        val data: JsonObject
    )

    internal class Impl(
        defaultClient: DelegationClient
    ) : AbstractDelegationAgent(defaultClient) {
        companion object {
            private const val NAME_DELEGATE = "Delegate"
            private val DELEGATE = NamespaceAndName(
                NAMESPACE,
                NAME_DELEGATE
            )
        }

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)
        private val executor = Executors.newSingleThreadExecutor()

        override fun preHandleDirective(info: DirectiveInfo) {
            // no-op
        }

        override fun handleDirective(info: DirectiveInfo) {
            when (info.directive.getName()) {
                NAME_DELEGATE -> handleDelegate(info)
            }
        }

        private fun handleDelegate(info: DirectiveInfo) {
            Logger.d(TAG, "[handleDelegate] info: $info")
            val payload = MessageFactory.create(info.directive.payload, DelegatePayload::class.java)
            if (payload == null) {
                Logger.d(TAG, "[handleDelegate] invalid payload: ${info.directive.payload}")
                setHandlingFailed(
                    info,
                    "[handleDelegate] invalid payload: ${info.directive.payload}"
                )
                return
            }

            executor.submit {
                defaultClient.onReceive(
                    payload.appId,
                    payload.playServiceId,
                    payload.data.toString()
                )
                setHandlingCompleted(info)
            }
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            info.result.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, description: String) {
            info.result.setFailed(description)
            removeDirective(info)
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[DELEGATE] = nonBlockingPolicy

            return configuration
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            executor.submit {
                contextSetter.setState(
                    namespaceAndName, buildContext(defaultClient.getAppContext())?.toString() ?: "",
                    StateRefreshPolicy.SOMETIMES, stateRequestToken
                )
            }
        }

        private fun buildContext(appContext: DelegationClient.Context?): JsonObject? {
            if (appContext == null) {
                return null
            }

            val jsonData = try {
                JsonParser().parse(appContext.data).asJsonObject
            } catch (e: Exception) {
                Logger.e(TAG, "[buildContext] invalid : ${appContext.data}", e)
                null
            } ?: return null

            return JsonObject().apply {
                addProperty("version", VERSION)
                addProperty("playServiceId", appContext.playServiceId)
                add("data", jsonData)
            }
        }

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }
    }
}