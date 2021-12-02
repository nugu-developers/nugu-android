/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.ext.message

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.common.tts.TTSScenarioPlayer
import com.skt.nugu.sdk.agent.ext.message.handler.GetMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.ReadMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.SendCandidatesDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.SendMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.payload.GetMessagePayload
import com.skt.nugu.sdk.agent.ext.message.payload.ReadMessageDirective
import com.skt.nugu.sdk.agent.ext.message.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.ext.message.payload.SendMessagePayload
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class MessageAgent(
    private val client: MessageClient,
    private val ttsScenarioPlayer: TTSScenarioPlayer,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    contextGetter: ContextGetterInterface,
    messageSender: MessageSender,
    directiveSequencer: DirectiveSequencerInterface,
    interactionControlManager: InteractionControlManagerInterface
) : CapabilityAgent
    , MessageAgentInterface
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.AgentController
    , SendMessageDirectiveHandler.Controller
    , GetMessageDirectiveHandler.Controller {
    companion object {
        private const val TAG = "MessageAgent"
        const val NAMESPACE = "Message"

        val VERSION = Version(1, 5)
    }

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    private val listeners = LinkedHashSet<MessageAgentInterface.OnPlaybackListener>()
    private val executor = Executors.newSingleThreadExecutor()

    private val readMessageController = object : ReadMessageDirectiveHandler.Controller, TTSScenarioPlayer.Listener {
            private val directives = HashMap<String, ReadMessageSource>()

            var state: TTSAgentInterface.State = TTSAgentInterface.State.IDLE
            var token: String? = null

            init {
                ttsScenarioPlayer.addListener(this)
            }

            private inner class ReadMessageSource(
                val directive: ReadMessageDirective,
                private val attachmentReader: Attachment.Reader,
                val callback: ReadMessageDirectiveHandler.Callback
            ) : TTSScenarioPlayer.Source() {
                override fun getReader(): Attachment.Reader? = attachmentReader

                override fun onCanceled() {
                    callback.onError("Canceled")
                }

                override fun getPushPlayServiceId(): String? =
                    directive.payload.playStackControl?.getPushPlayServiceId()

                override val playServiceId: String? = directive.payload.playServiceId
                override val dialogRequestId: String = directive.header.dialogRequestId

                override fun requestReleaseSync() {
                    cancel(directive.header.messageId)
                }
            }

            override fun prepare(
                directive: ReadMessageDirective,
                reader: Attachment.Reader,
                callback: ReadMessageDirectiveHandler.Callback
            ) {
                ReadMessageSource(directive, reader, callback).apply {
                    directives[directive.header.messageId] = this
                    ttsScenarioPlayer.prepare(this)
                }
            }

            override fun start(messageId: String) {
                directives[messageId]?.let { it ->
                    ttsScenarioPlayer.start(it)
                }
            }

            override fun cancel(messageId: String) {
                directives[messageId]?.let { it ->
                    ttsScenarioPlayer.cancel(it)
                }
            }

            override fun onPlaybackStarted(source: TTSScenarioPlayer.Source) {
                // no-op
                state = TTSAgentInterface.State.PLAYING
                directives.filter { it.value.dialogRequestId == source.dialogRequestId }
                    .map { it.value }
                    .firstOrNull()?.let { readSource ->
                        token = readSource.directive.payload.token

                        executor.submit {
                            listeners.forEach {
                                it.onPlaybackStarted(readSource.directive)
                            }
                        }
                    }
            }

            override fun onPlaybackFinished(source: TTSScenarioPlayer.Source) {
                state = TTSAgentInterface.State.FINISHED
                directives.filter { it.value.dialogRequestId == source.dialogRequestId }
                    .map { it.value }
                    .firstOrNull()?.let { readSource ->
                        readSource.callback.onFinish()
                        directives.remove(readSource.directive.header.messageId)

                        executor.submit {
                            listeners.forEach {
                                it.onPlaybackFinished(readSource.directive)
                            }
                        }
                    }
            }

            override fun onPlaybackStopped(source: TTSScenarioPlayer.Source) {
                state = TTSAgentInterface.State.STOPPED
                directives.filter { it.value.dialogRequestId == source.dialogRequestId }
                    .map { it.value }
                    .firstOrNull()?.let { readSource ->
                        readSource.callback.onStop(true)
                        directives.remove(readSource.directive.header.messageId)

                        executor.submit {
                            listeners.forEach {
                                it.onPlaybackStopped(readSource.directive)
                            }
                        }
                    }
            }

            override fun onPlaybackError(
                source: TTSScenarioPlayer.Source,
                type: ErrorType,
                error: String
            ) {
                state = TTSAgentInterface.State.STOPPED
                directives.filter { it.value.dialogRequestId == source.dialogRequestId }
                    .map { it.value }
                    .firstOrNull()?.let { readSource ->
                        readSource.callback.onError("type: $type, error: $error")
                        directives.remove(readSource.directive.header.messageId)

                        executor.submit {
                            listeners.forEach {
                                it.onPlaybackError(readSource.directive, type, error)
                            }
                        }
                    }
            }
    }

    init {
        contextStateProviderRegistry.setStateProvider(
            namespaceAndName,
            this
        )

        directiveSequencer.apply {
            addDirectiveHandler(
                SendCandidatesDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter,
                    interactionControlManager,
                    namespaceAndName
                )
            )
            addDirectiveHandler(
                SendMessageDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(
                GetMessageDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter,
                    interactionControlManager
                )
            )

            addDirectiveHandler(
                ReadMessageDirectiveHandler(
                    readMessageController,
                    messageSender,
                    contextGetter,
                    namespaceAndName
                )
            )
        }
    }

    data class StateContext(
        private val context: Context,
        private val readActivity: TTSAgentInterface.State,
        private val token: String?
    ) : BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            addProperty("readActivity", readActivity.name)
            token?.let {
                addProperty("token", token)
            }
            context.template?.let { template ->
                add("template", template.toJson())
            }
        }.toString()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else
                    StateContext(
                        client.getContext(),
                        readMessageController.state,
                        readMessageController.token
                    ),
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        }
    }

    override fun sendCandidates(
        payload: SendCandidatesPayload,
        callback: SendCandidatesDirectiveHandler.AgentCallback
    ) {
        executor.submit {
            client.sendCandidates(payload, object: SendCandidatesDirectiveHandler.Callback {
                override fun onSuccess(context: Context) {
                    callback.onSuccess(StateContext(
                        context,
                        readMessageController.state,
                        readMessageController.token
                    ))
                }

                override fun onFailure() {
                    callback.onFailure()
                }
            })
        }
    }

    override fun sendMessage(payload: SendMessagePayload, callback: EventCallback) {
        executor.submit {
            client.sendMessage(payload, callback)
        }
    }

    override fun getMessageList(
        payload: GetMessagePayload,
        callback: GetMessageDirectiveHandler.Callback
    ) {
        executor.submit {
            client.getMessageList(payload, callback)
        }
    }

    override fun addOnPlaybackListener(listener: MessageAgentInterface.OnPlaybackListener) {
        executor.submit {
            listeners.add(listener)
        }
    }

    override fun removeOnPlaybackListener(listener: MessageAgentInterface.OnPlaybackListener) {
        executor.submit {
            listeners.remove(listener)
        }
    }
}