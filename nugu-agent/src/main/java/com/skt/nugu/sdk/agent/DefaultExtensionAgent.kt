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
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.agent.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextState
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DefaultExtensionAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender
) : AbstractCapabilityAgent(NAMESPACE)
    , ExtensionAgentInterface
    , InputProcessor {

    internal data class ExtensionPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("data")
        val data: JsonObject
    )

    companion object {
        private const val TAG = "DefaultExtensionAgent"

        const val NAMESPACE = "Extension"
        private val VERSION = Version(1,1)

        private const val NAME_ACTION = "Action"
        private const val NAME_ACTION_SUCCEEDED = "ActionSucceeded"
        private const val NAME_ACTION_FAILED = "ActionFailed"
        private const val NAME_COMMAND_ISSUED = "CommandIssued"

        private val ACTION = NamespaceAndName(
            NAMESPACE,
            NAME_ACTION
        )

        private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"
        private const val PAYLOAD_DATA = "data"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var client: ExtensionAgentInterface.Client? = null


    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun setClient(client: ExtensionAgentInterface.Client) {
        this.client = client
    }

    internal data class StateContext(val data: String?): ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            data?.let {
                try {
                    add("data", JsonParser.parseString(it).asJsonObject)
                } catch (th: Throwable) {
                    Logger.e(TAG, "[buildContext] error to create data json object.", th)
                }
            }
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(
            namespaceAndName,
            StateContext(client?.getData()),
            StateRefreshPolicy.ALWAYS,
            stateRequestToken
        )
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getName()) {
            NAME_ACTION -> handleActionDirective(info)
        }
    }

    private fun handleActionDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, ExtensionPayload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[handleActionDirective] invalid payload: ${info.directive.payload}")
            setHandlingFailed(
                info,
                "[handleActionDirective] invalid payload: ${info.directive.payload}"
            )
            return
        }

        val data = payload.data
        val playServiceId = payload.playServiceId

        executor.submit {
            val currentClient = client
            if (currentClient != null) {
                val referrerDialogRequestId = info.directive.header.dialogRequestId
                if (currentClient.action(data.toString(), playServiceId, info.directive.header.dialogRequestId)) {
                    sendActionEvent(NAME_ACTION_SUCCEEDED, playServiceId, referrerDialogRequestId)
                } else {
                    sendActionEvent(NAME_ACTION_FAILED, playServiceId, referrerDialogRequestId)
                }
            } else {
                Logger.w(
                    TAG,
                    "[handleActionDirective] no current client. set client using setClient()."
                )
            }
        }
        setHandlingCompleted(info)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[ACTION] = nonBlockingPolicy

        return configuration
    }

    private fun sendActionEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        Logger.d(TAG, "[sendEvent] name: $name, playServiceId: $playServiceId")
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
                    .payload(JsonObject().apply {
                        addProperty(PAYLOAD_PLAY_SERVICE_ID, playServiceId)
                    }.toString())
                    .referrerDialogRequestId(referrerDialogRequestId)
                    .build()

                messageSender.newCall(
                    request
                ).enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }
                    override fun onSuccess(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)
    }

    override fun issueCommand(
        playServiceId: String,
        data: String,
        callback: ExtensionAgentInterface.OnCommandIssuedCallback?
    ): String {
        Logger.d(TAG, "[issueCommand] playServiceId: $playServiceId, data: $data, callback: $callback")
        val jsonData = try {
            JsonParser.parseString(data).asJsonObject
        } catch(th: Throwable) {
            throw IllegalArgumentException(th)
        }

        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, NAME_COMMAND_ISSUED, VERSION.toString())
                    .dialogRequestId(dialogRequestId)
                    .payload(JsonObject().apply {
                        addProperty(PAYLOAD_PLAY_SERVICE_ID, playServiceId)
                        add(PAYLOAD_DATA, jsonData)
                    }.toString()).build()

                messageSender.newCall(
                    request
                ).enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {

                        if(status.isTimeout()) {
                            callback?.onError(
                                dialogRequestId,
                                ExtensionAgentInterface.ErrorType.RESPONSE_TIMEOUT
                            )
                        } else {
                            callback?.onError(
                                dialogRequestId,
                                ExtensionAgentInterface.ErrorType.REQUEST_FAIL
                            )
                        }
                    }
                    override fun onSuccess(request: MessageRequest) {
                        callback?.onSuccess(dialogRequestId)
                    }
                })
            }
        }, namespaceAndName)

        return dialogRequestId
    }

    override fun onSendEventFinished(dialogRequestId: String) {
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
    }
}