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
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.Executors

class DefaultDelegationAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val defaultClient: DelegationClient
) : AbstractCapabilityAgent(NAMESPACE), DelegationAgentInterface {

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

    init {
        contextManager.setStateProvider(namespaceAndName, this)
        // update delegate initial state
        contextManager.setState(
            namespaceAndName,
            StateContext(null),
            StateRefreshPolicy.SOMETIMES,
            ContextType.FULL,
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
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[DELEGATE] = BlockingPolicy.sharedInstanceFactory.get()
    }

    internal data class StateContext(private val appContext: DelegationClient.Context?): BaseContextState {
        companion object {
            private fun buildCompactContext() = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }
        override fun value(): String {
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
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                    defaultClient.getAppContext()
                ),
                StateRefreshPolicy.SOMETIMES,
                contextType,
                stateRequestToken
            )
        }
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

        executor.submit {
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    messageSender.newCall(
                        EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_REQUEST,
                            VERSION.toString()
                        ).payload(JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                            add("data", jsonData)
                        }.toString()).dialogRequestId(dialogRequestId).build()
                    ).enqueue(object : MessageSender.Callback {
                        override fun onFailure(request: MessageRequest, status: Status) {
                            if(status.isTimeout()) {
                                listener?.onError(DelegationAgentInterface.Error.TIMEOUT)
                            } else {
                                listener?.onError(DelegationAgentInterface.Error.UNKNOWN)
                            }
                        }

                        override fun onSuccess(request: MessageRequest) {
                        }

                        override fun onResponseStart(request: MessageRequest) {
                            listener?.onSuccess()
                        }
                    })
                }
            })
        }

        return dialogRequestId
    }
}