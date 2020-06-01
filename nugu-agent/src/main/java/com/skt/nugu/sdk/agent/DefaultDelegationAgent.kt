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
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.agent.delegation.DelegationClient
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextState
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DefaultDelegationAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val defaultClient: DelegationClient
) : AbstractCapabilityAgent(NAMESPACE), DelegationAgentInterface, InputProcessor {

    internal data class DelegatePayload(
        @SerializedName("appId")
        val appId: String,
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("data")
        val data: JsonObject
    )

    companion object {
        private const val TAG = "DelegationAgent"

        const val NAMESPACE = "Delegation"
        private val VERSION = Version(1,1)

        private const val NAME_DELEGATE = "Delegate"
        private const val NAME_REQUEST = "Request"

        private val DELEGATE = NamespaceAndName(
            NAMESPACE,
            NAME_DELEGATE
        )
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val requestListenerMap =
        ConcurrentHashMap<String, DelegationAgentInterface.OnRequestListener>()

    init {
        contextManager.setStateProvider(namespaceAndName, this)
        // update delegate initial state
        contextManager.setState(
            namespaceAndName,
            StateContext(null),
            StateRefreshPolicy.SOMETIMES,
            0
        )
    }

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
                payload.data.toString(),
                info.directive.header.dialogRequestId
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

    internal data class StateContext(private val appContext: DelegationClient.Context?): ContextState {
        companion object {
            private fun buildCompactContext() = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE = buildCompactContext().toString()
        }
        override fun toFullJsonString(): String {
            if (appContext == null) {
                return ""
            }

            val jsonData = try {
                JsonParser.parseString(appContext.data).asJsonObject
            } catch (e: Exception) {
                Logger.e(TAG, "[buildContext] invalid : ${appContext.data}", e)
                null
            } ?: return ""

            return buildCompactContext().apply {
                addProperty("playServiceId", appContext.playServiceId)
                add("data", jsonData)
            }.toString()
        }

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                StateContext(defaultClient.getAppContext()),
                StateRefreshPolicy.SOMETIMES,
                stateRequestToken
            )
        }
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun request(
        playServiceId: String,
        data: String,
        listener: DelegationAgentInterface.OnRequestListener?
    ): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        val jsonData = try {
            JsonParser.parseString(data).asJsonObject
        } catch (th: Throwable) {
            throw IllegalArgumentException("data is not jsonObject", th)
        }

        listener?.let {
            requestListenerMap[dialogRequestId] = it
        }

        executor.submit {
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    messageSender.sendMessage(
                        EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_REQUEST,
                            VERSION.toString()
                        ).payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                            add("data", jsonData)
                        }.toString()).dialogRequestId(dialogRequestId).build()
                    )
                    onSendEventFinished(dialogRequestId)
                }
            })
        }

        return dialogRequestId
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        requestListenerMap.remove(dialogRequestId)?.onSuccess()
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        requestListenerMap.remove(dialogRequestId)?.onError(DelegationAgentInterface.Error.TIMEOUT)
    }
}