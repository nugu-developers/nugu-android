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
package com.skt.nugu.sdk.agent.text

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractCapabilityAgent
import com.skt.nugu.sdk.agent.common.InteractionControl
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class TextAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val dialogAttributeStorage: DialogAttributeStorageInterface,
    private val textSourceHandler: TextAgentInterface.TextSourceHandler?,
    private val textRedirectHandler: TextAgentInterface.TextRedirectHandler?,
    private val expectTypingController: ExpectTypingHandlerInterface.Controller,
    private val interactionControlManager: InteractionControlManagerInterface,
    directiveSequencer: DirectiveSequencerInterface,
    interLayerDisplayPolicyManager: InterLayerDisplayPolicyManager
) : AbstractCapabilityAgent(NAMESPACE)
    , TextAgentInterface
{
    internal data class TextSourcePayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("text")
        val text: String,
        @SerializedName("token")
        val token: String,
        @SerializedName("source")
        val source: String?
    )

    internal data class TextRedirectPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("text")
        val text: String,
        @SerializedName("token")
        val token: String,
        @SerializedName("targetPlayServiceId")
        val targetPlayServiceId: String?,
        @SerializedName("interactionControl")
        val interactionControl: InteractionControl?
    )

    companion object {
        private const val TAG = "TextAgent"

        const val NAMESPACE = "Text"
        private val VERSION = Version(1,7)

        private const val NAME_TEXT_SOURCE = "TextSource"
        private const val NAME_TEXT_REDIRECT = "TextRedirect"
        private const val NAME_FAILED = "Failed"

        val TEXT_SOURCE = NamespaceAndName(
            NAMESPACE,
            NAME_TEXT_SOURCE
        )

        val TEXT_REDIRECT = NamespaceAndName(
            NAMESPACE,
            NAME_TEXT_REDIRECT
        )

        const val NAME_TEXT_INPUT = "TextInput"

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val internalTextSourceHandleListeners = CopyOnWriteArraySet<TextAgentInterface.InternalTextSourceHandlerListener>()
    private val internalTextRedirectHandleListeners = CopyOnWriteArraySet<TextAgentInterface.InternalTextRedirectHandlerListener>()
    private val executor = Executors.newSingleThreadExecutor()

    private val expectTypingDirectiveController = object : ExpectTypingHandlerInterface.Controller {
        override fun expectTyping(
            directive: ExpectTypingHandlerInterface.Directive
        ) {
            executor.submit {
                expectTypingController.expectTyping(directive)
            }
        }
    }

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    init {
        contextManager.setStateProvider(namespaceAndName, this)

        directiveSequencer.addDirectiveHandler(this)
        directiveSequencer.addDirectiveHandler(
            ExpectTypingDirectiveHandler(
                dialogAttributeStorage,
                expectTypingDirectiveController
            ).apply {
                interLayerDisplayPolicyManager.addListener(this)
            })
    }

    override fun addInternalTextSourceHandlerListener(listener: TextAgentInterface.InternalTextSourceHandlerListener) {
        internalTextSourceHandleListeners.add(listener)
    }

    override fun removeInternalTextSourceHandlerListener(listener: TextAgentInterface.InternalTextSourceHandlerListener) {
        internalTextSourceHandleListeners.remove(listener)
    }

    override fun addInternalTextRedirectHandlerListener(listener: TextAgentInterface.InternalTextRedirectHandlerListener) {
        internalTextRedirectHandleListeners.add(listener)
    }

    override fun removeInternalTextRedirectHandlerListener(listener: TextAgentInterface.InternalTextRedirectHandlerListener) {
        internalTextRedirectHandleListeners.remove(listener)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
        Logger.d(TAG, "[preHandleDirective] info: $info")
    }

    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleDirective] info: $info")
        executor.submit {
            when(info.directive.getNamespaceAndName()) {
                TEXT_SOURCE -> executeHandleTextSourceDirective(info)
                TEXT_REDIRECT -> executeHandleTextRedirectDirective(info)
            }
        }
    }

    private fun executeHandleTextSourceDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandleTextSourceDirective] info: $info")
        val payload =
            MessageFactory.create(info.directive.payload, TextSourcePayload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[executeHandleTextSourceDirective] invalid payload: ${info.directive.payload}")
            executeSetHandlingFailed(
                info,
                "[executeHandleTextSourceDirective] invalid payload: ${info.directive.payload}"
            )
            return
        }

        executeSetHandlingCompleted(info)
        val result = textSourceHandler?.shouldExecuteDirective(info.directive.payload, info.directive.header) ?: TextAgentInterface.TextSourceHandler.Result.OK
        if(result != TextAgentInterface.TextSourceHandler.Result.OK) {
            Logger.d(TAG, "[executeHandleTextSourceDirective] result returned: $result")
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    messageSender.newCall(
                        EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            "$NAME_TEXT_SOURCE$NAME_FAILED",
                            VERSION.toString()
                        ).referrerDialogRequestId(info.directive.getDialogRequestId())
                            .payload(JsonObject().apply {
                                payload.playServiceId?.let {
                                    addProperty("playServiceId", it)
                                }
                                addProperty("token", payload.token)
                                addProperty("errorCode", result.name)
                            }.toString())
                            .build()
                    ).enqueue(null)
                }
            }, namespaceAndName)
        } else {
            val dialogRequestId = executeSendTextInputEventInternal(TextInputRequester.Request.Builder(payload.text)
                .includeDialogAttribute(payload.playServiceId == null)
                .playServiceId(payload.playServiceId).token(payload.token)
                .source(payload.source)
                .referrerDialogRequestId(info.directive.header.dialogRequestId).build(), handleSourceDirectiveRequestListener)
            internalTextSourceHandleListeners.forEach {
                it.onRequested(dialogRequestId)
            }
        }
    }

    private val handleSourceDirectiveRequestListener by lazy {
        object : TextAgentInterface.RequestListener {
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
        }
    }

    private fun executeHandleTextRedirectDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandleTextRedirectDirective] info: $info")
        val payload =
            MessageFactory.create(info.directive.payload, TextRedirectPayload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[executeHandleTextRedirectDirective] invalid payload: ${info.directive.payload}")
            executeSetHandlingFailed(
                info,
                "[executeHandleTextRedirectDirective] invalid payload: ${info.directive.payload}"
            )
            return
        }

        executeSetHandlingCompleted(info)


        val interactionControl = if(payload.interactionControl != null) {
            object : com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl {
                override fun getMode(): InteractionControlMode = when(payload.interactionControl.mode) {
                    InteractionControl.Mode.MULTI_TURN -> InteractionControlMode.MULTI_TURN
                    InteractionControl.Mode.NONE -> InteractionControlMode.NONE
                }
            }
        } else {
            null
        }

        interactionControl?.let {
            interactionControlManager.start(it)
        }

        val targetPlayServiceId = payload.targetPlayServiceId
        val result = textRedirectHandler?.shouldExecuteDirective(info.directive.payload, info.directive.header) ?: TextAgentInterface.TextRedirectHandler.Result.OK
        if(result != TextAgentInterface.TextRedirectHandler.Result.OK) {
            Logger.d(TAG, "[executeHandleTextRedirectDirective] result returned: $result")
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    if(!messageSender.newCall(
                            EventMessageRequest.Builder(
                                jsonContext,
                                NAMESPACE,
                                "$NAME_TEXT_REDIRECT$NAME_FAILED",
                                VERSION.toString()
                            ).referrerDialogRequestId(info.directive.getDialogRequestId())
                                .payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    addProperty("errorCode", result.name)
                                    payload.interactionControl?.let {
                                        add("interactionControl", it.toJsonObject())
                                    }
                                }.toString())
                                .build()
                        ).enqueue(object : MessageSender.Callback{
                            override fun onFailure(request: MessageRequest, status: Status) {
                                interactionControl?.let {
                                    interactionControlManager.finish(it)
                                }
                            }

                            override fun onSuccess(request: MessageRequest) {
                            }

                            override fun onResponseStart(request: MessageRequest) {
                                interactionControl?.let {
                                    interactionControlManager.finish(it)
                                }
                            }
                        })) {
                        interactionControl?.let {
                            interactionControlManager.finish(it)
                        }
                    }
                }
            }, namespaceAndName)
        } else {
            val dialogRequestId = executeSendTextInputEventInternal(
                TextInputRequester.Request.Builder(payload.text)
                    .includeDialogAttribute(targetPlayServiceId == null)
                    .playServiceId(targetPlayServiceId).token(payload.token)
                    .referrerDialogRequestId(info.directive.header.dialogRequestId).build(),
                object : TextAgentInterface.RequestListener {
                    override fun onRequestCreated(dialogRequestId: String) {
                        internalTextRedirectHandleListeners.forEach {
                            it.onRequestCreated(dialogRequestId)
                        }
                    }

                    override fun onReceiveResponse(dialogRequestId: String) {
                        interactionControl?.let {
                            interactionControlManager.finish(it)
                        }

                        internalTextRedirectHandleListeners.forEach {
                            it.onReceiveResponse(dialogRequestId)
                        }
                    }

                    override fun onError(
                        dialogRequestId: String,
                        type: TextAgentInterface.ErrorType
                    ) {
                        interactionControl?.let {
                            interactionControlManager.finish(it)
                        }

                        internalTextRedirectHandleListeners.forEach {
                            it.onError(dialogRequestId, type)
                        }
                    }
                })

            internalTextRedirectHandleListeners.forEach {
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

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val nonBlockingPolicy = BlockingPolicy.sharedInstanceFactory.get()
        this[TEXT_SOURCE] = nonBlockingPolicy
        this[TEXT_REDIRECT] = nonBlockingPolicy
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
        playServiceId: String?,
        token: String?,
        source: String?,
        referrerDialogRequestId: String?,
        includeDialogAttribute: Boolean,
        listener: TextAgentInterface.RequestListener?
    ): String {
        return textInput(TextInputRequester.Request.Builder(text).playServiceId(playServiceId).token(token)
            .source(source).referrerDialogRequestId(referrerDialogRequestId)
            .includeDialogAttribute(includeDialogAttribute), listener)
    }

    override fun textInput(
        request: TextInputRequester.Request,
        listener: TextAgentInterface.RequestListener?
    ): String {
        Logger.d(TAG, "[textInput] request: $request, listener: $listener")
        return executeSendTextInputEventInternal(request, listener)
    }

    override fun textInput(
        requestBuilder: TextInputRequester.Request.Builder,
        listener: TextAgentInterface.RequestListener?
    ): String = textInput(requestBuilder.build(), listener)

    private fun createMessage(context: String, request: TextInputRequester.Request, dialogRequestId: String) =
        EventMessageRequest.Builder(
            context,
            NAMESPACE,
            NAME_TEXT_INPUT,
            VERSION.toString()
        ).dialogRequestId(dialogRequestId)
            .payload(
                JsonObject().apply
                {
                    addProperty("text", request.text)
                    request.token?.let {
                        addProperty("token", it)
                    }
                    request.source?.let {
                        addProperty("source", it)
                    }

                    request.playServiceId?.let {
                        addProperty("playServiceId", it)
                    }

                    if(request.includeDialogAttribute) {
                        dialogAttributeStorage.getRecentAttribute()?.let { attr ->
                            attr.playServiceId?.let {
                                addProperty("playServiceId", it)
                            }
                            attr.domainTypes?.let {
                                add("domainTypes", JsonArray().apply {
                                    it.forEach {
                                        add(it)
                                    }
                                })
                            }
                        }
                    }
                }.toString()
            ).referrerDialogRequestId(request.referrerDialogRequestId ?: "").build()

    private fun executeSendTextInputEventInternal(
        request: TextInputRequester.Request,
        listener: TextAgentInterface.RequestListener?
    ): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                Logger.d(TAG, "[onContextAvailable] jsonContext: $jsonContext")
                executor.submit {
                    createMessage(jsonContext, request, dialogRequestId).let {
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
                            }

                            override fun onResponseStart(request: MessageRequest) {
                                listener?.onReceiveResponse(dialogRequestId)
                            }
                        })
                    }
                }
            }
        })

        return dialogRequestId
    }
}
