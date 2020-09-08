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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class DefaultTextAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val dialogAttributeStorage: DialogAttributeStorageInterface,
    private val textSourceHandler: TextAgentInterface.TextSourceHandler?
) : AbstractCapabilityAgent(NAMESPACE)
    , InputProcessor
    , TextAgentInterface{
    internal data class TextSourcePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("text")
        val text: String,
        @SerializedName("token")
        val token: String
    )

    companion object {
        private const val TAG = "TextAgent"

        const val NAMESPACE = "Text"
        private val VERSION = Version(1,3)

        private const val NAME_TEXT_SOURCE = "TextSource"

        val TEXT_SOURCE = NamespaceAndName(
            NAMESPACE,
            NAME_TEXT_SOURCE
        )

        const val NAME_TEXT_INPUT = "TextInput"

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val internalTextSourceHandleListeners = CopyOnWriteArraySet<TextAgentInterface.InternalTextSourceHandlerListener>()
    private val executor = Executors.newSingleThreadExecutor()

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    init {
        contextManager.setStateProvider(namespaceAndName, this)

        provideState(contextManager, namespaceAndName, ContextType.FULL, 0)
        provideState(contextManager, namespaceAndName, ContextType.COMPACT, 0)
    }

    override fun addInternalTextSourceHandlerListener(listener: TextAgentInterface.InternalTextSourceHandlerListener) {
        internalTextSourceHandleListeners.add(listener)
    }

    override fun removeInternalTextSourceHandlerListener(listener: TextAgentInterface.InternalTextSourceHandlerListener) {
        internalTextSourceHandleListeners.remove(listener)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
        Logger.d(TAG, "[preHandleDirective] info: $info")
    }

    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleDirective] info: $info")
        executor.submit {
            executeHandleDirective(info)
        }
    }

    private fun executeHandleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandleDirective] info: $info")
        val payload =
            MessageFactory.create(info.directive.payload, TextSourcePayload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[executeHandleDirective] invalid payload: ${info.directive.payload}")
            executeSetHandlingFailed(
                info,
                "[executeHandleDirective] invalid payload: ${info.directive.payload}"
            )
            return
        }

        executeSetHandlingCompleted(info)
        if(textSourceHandler?.handleTextSource(info.directive.payload, info.directive.header) == true) {
            Logger.d(TAG, "[executeHandleDirective] handled at TextSourceHandler($textSourceHandler)")
        } else {
            val dialogRequestId = executeSendTextInputEventInternal(payload.text,
                payload.playServiceId == null, payload.playServiceId, payload.token, info.directive.header.dialogRequestId, object: TextAgentInterface.RequestListener {
                override fun onRequestCreated(dialogRequestId: String) {
                    internalTextSourceHandleListeners.forEach {
                        it.onRequestCreated(dialogRequestId)
                    }
                }

                override fun onReceiveResponse(dialogRequestId: String) {
                    internalTextSourceHandleListeners.forEach {
                        it.onReceiveResponse(dialogRequestId)
                    }
                }

                override fun onError(dialogRequestId: String, type: TextAgentInterface.ErrorType) {
                    internalTextSourceHandleListeners.forEach {
                        it.onError(dialogRequestId, type)
                    }
                }
            })
            internalTextSourceHandleListeners.forEach {
                it.onRequested(dialogRequestId)
            }
        }
    }

    private fun executeSetHandlingCompleted(info: DirectiveInfo) {
        Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
        info.result.setCompleted()
    }

    private fun executeSetHandlingFailed(info: DirectiveInfo, msg: String) {
        Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
        info.result.setFailed(msg)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[TEXT_SOURCE] = nonBlockingPolicy

        return configuration
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        contextSetter.setState(namespaceAndName, contextState, StateRefreshPolicy.NEVER, contextType, stateRequestToken)
    }

    override fun requestTextInput(
        text: String,
        includeDialogAttribute: Boolean,
        listener: TextAgentInterface.RequestListener?
    ): String {
        Logger.d(TAG, "[requestTextInput] text: $text")
        return executeSendTextInputEventInternal(text, includeDialogAttribute, null,null, null, listener)
    }

    private fun createMessage(text: String, includeDialogAttribute: Boolean, context: String, playServiceId: String?, token: String?, dialogRequestId: String, referrerDialogRequestId: String?) =
        EventMessageRequest.Builder(
            context,
            NAMESPACE,
            NAME_TEXT_INPUT,
            VERSION.toString()
        ).dialogRequestId(dialogRequestId)
            .payload(
            JsonObject().apply
            {
                addProperty("text", text)
                token?.let {
                    addProperty("token", it)
                }

                playServiceId?.let {
                    addProperty("playServiceId", it)
                }

                if(includeDialogAttribute) {
                    dialogAttributeStorage.getAttributes()?.let { attrs ->
                        attrs.forEach { attr ->
                            add(attr.key, Gson().toJsonTree(attr.value))
                        }
                    }
                }
            }.toString()
        ).referrerDialogRequestId(referrerDialogRequestId ?: "").build()

    private fun executeSendTextInputEventInternal(
        text: String,
        includeDialogAttribute: Boolean,
        playServiceId: String?,
        token: String?,
        referrerDialogRequestId: String?,
        listener: TextAgentInterface.RequestListener?
    ): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                Logger.d(TAG, "[onContextAvailable] jsonContext: $jsonContext")
                executor.submit {
                    createMessage(text, includeDialogAttribute, jsonContext, playServiceId, token, dialogRequestId, referrerDialogRequestId).let {
                        listener?.onRequestCreated(dialogRequestId)

                        val call = messageSender.newCall(it)
                        call.enqueue(object : MessageSender.Callback{
                            override fun onFailure(request: MessageRequest, status: Status) {
                                if(status.isTimeout()) {
                                    Logger.d(TAG, "[onResponseTimeout] $dialogRequestId")
                                    listener?.onError(dialogRequestId, TextAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT)
                                } else {
                                    listener?.onError(
                                        dialogRequestId,
                                        TextAgentInterface.ErrorType.ERROR_NETWORK
                                    )
                                }
                            }

                            override fun onSuccess(request: MessageRequest) {
                                listener?.onReceiveResponse(dialogRequestId)
                            }

                            override fun onResponseStart(request: MessageRequest) {
                            }
                        })
                    }
                }
            }
        })

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
