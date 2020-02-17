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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.text.AbstractTextAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.util.getValidReferrerDialogRequestId
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.concurrent.Executors

class DefaultTextAgent(
    messageSender: MessageSender,
    contextManager: ContextManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface
) : AbstractTextAgent(messageSender, contextManager, inputProcessorManager) {
    internal data class TextSourcePayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("text")
        val text: String,
        @SerializedName("token")
        val token: String
    )

    companion object {
        private const val TAG = "TextAgent"

        private const val NAME_TEXT_SOURCE = "TextSource"

        val TEXT_SOURCE = NamespaceAndName(
            NAMESPACE,
            NAME_TEXT_SOURCE
        )

        const val NAME_TEXT_INPUT = "TextInput"
    }

    private val requestListeners = HashMap<String, TextAgentInterface.RequestListener>()
    private val executor = Executors.newSingleThreadExecutor()
    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    init {
        contextManager.setStateProvider(namespaceAndName, this)
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

        executeSendTextInputEvent(payload.text, payload.token, info, null)
    }

    private fun executeSetHandlingCompleted(info: DirectiveInfo) {
        Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun executeSetHandlingFailed(info: DirectiveInfo, msg: String) {
        Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
        info.result.setFailed(msg)
        removeDirective(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[TEXT_SOURCE] = nonBlockingPolicy

        return configuration
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(namespaceAndName, JsonObject().apply {
            addProperty("version", VERSION)
        }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
    }

    override fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener?) {
        Logger.d(TAG, "[requestTextInput] text: $text")
        executeSendTextInputEventInternal(text, null, null, listener)
    }

    private fun createMessage(text: String, context: String, token: String?, referrerDialogRequestId: String?) =
        EventMessageRequest.Builder(
            context,
            NAMESPACE,
            NAME_TEXT_INPUT,
            VERSION
        ).payload(
            JsonObject().apply
            {
                addProperty("text", text)
                token?.let {
                    addProperty("token", it)
                }

                dialogSessionInfo?.let { info ->
                    addProperty("sessionId", info.sessionId)
                    info.playServiceId?.let {
                        addProperty("playServiceId", it)
                    }
                    info.property?.let {
                        addProperty("property", it)
                    }

                    info.domainTypes?.let { domainTypes ->
                        add("domainTypes", JsonArray().apply {
                            domainTypes.forEach {
                                add(it)
                            }
                        })
                    }
                }
            }.toString()
        ).referrerDialogRequestId(referrerDialogRequestId ?: "").build()

    private fun executeSendTextInputEvent(
        text: String,
        token: String,
        info: DirectiveInfo,
        listener: TextAgentInterface.RequestListener?
    ) {
        Logger.d(TAG, "[executeSendTextInputEvent] text: $text, token: $token")
        executeSetHandlingCompleted(info)
        executeSendTextInputEventInternal(text, token, info.directive.header.getValidReferrerDialogRequestId(), listener)
    }

    private fun executeSendTextInputEventInternal(
        text: String,
        token: String?,
        referrerDialogRequestId: String?,
        listener: TextAgentInterface.RequestListener?
    ) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                Logger.d(TAG, "[onContextAvailable] jsonContext: $jsonContext")
                executor.submit {
                    createMessage(text, jsonContext, token, referrerDialogRequestId).let {
                        messageSender.sendMessage(it)
                        if (listener != null) {
                            requestListeners[it.dialogRequestId] = listener
                        }
                        onSendEventFinished(it.dialogRequestId)
                    }
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                Logger.d(TAG, "[onContextFailure] error: $error")
            }
        })
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        executor.submit {
            requestListeners.remove(dialogRequestId)?.onReceiveResponse()
        }
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        Logger.d(TAG, "[onResponseTimeout] $dialogRequestId")
        executor.submit {
            requestListeners.remove(dialogRequestId)
                ?.onError(TextAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT)
        }
    }

    private var dialogSessionInfo: DialogSessionManagerInterface.DialogSessionInfo? = null

    override fun onSessionOpened(
        sessionId: String,
        property: String?,
        domainTypes: Array<String>?,
        playServiceId: String?
    ) {
        dialogSessionInfo =
            DialogSessionManagerInterface.DialogSessionInfo(
                sessionId,
                property,
                domainTypes,
                playServiceId
            )
    }

    override fun onSessionClosed(sessionId: String) {
        dialogSessionInfo = null
    }
}
