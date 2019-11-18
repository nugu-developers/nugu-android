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
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.sound.AbstractSoundAgent
import com.skt.nugu.sdk.core.interfaces.capability.sound.SoundAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.sound.SoundProvider
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.mediaplayer.UriSourcePlayablePlayer
import java.util.HashMap
import java.util.concurrent.Executors

object DefaultSoundAgent {
    private const val TAG = "DefaultSoundAgent"

    val FACTORY = object : SoundAgentFactory {
        override fun create(
            mediaPlayer: UriSourcePlayablePlayer,
            contextManager: ContextManagerInterface,
            messageSender: MessageSender,
            soundProvider: SoundProvider
        ): AbstractSoundAgent =
            Impl(
                mediaPlayer,
                contextManager,
                messageSender,
                soundProvider
            )
    }

    internal data class SoundPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("beepName")
        val beepName: String
    )

    internal class Impl constructor(
        mediaPlayer: UriSourcePlayablePlayer,
        contextManager: ContextManagerInterface,
        messageSender: MessageSender,
        soundProvider: SoundProvider
    ) : AbstractSoundAgent(mediaPlayer, contextManager, messageSender, soundProvider) {
        companion object {
            private const val NAME_BEEP = "Beep"
            private const val NAME_BEEP_SUCCEEDED = "BeepSucceeded"
            private const val NAME_BEEP_FAILED = "BeepFailed"

            private val BEEP = NamespaceAndName(NAMESPACE, NAME_BEEP)
            private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"
        }

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)
        private val executor = Executors.newSingleThreadExecutor()

        init {
            contextManager.setStateProvider(namespaceAndName, this)
            contextManager.setState(namespaceAndName, buildContext(), StateRefreshPolicy.NEVER)
        }

        private fun buildContext(): String = JsonObject().apply {
            addProperty("version", VERSION)
        }.toString()

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            contextSetter.setState(
                namespaceAndName,
                buildContext(),
                StateRefreshPolicy.NEVER,
                stateRequestToken
            )
        }

        override fun preHandleDirective(info: DirectiveInfo) {
        }

        override fun handleDirective(info: DirectiveInfo) {
            when (info.directive.getName()) {
                NAME_BEEP -> handleSoundBeepDirective(info)
                else -> {
                    // nothing to do
                    Logger.d(TAG, "[handleDirective] unknown directive name: ${info.directive}")
                }
            }
        }

        private fun handleSoundBeepDirective(info: DirectiveInfo) {
            val payload = MessageFactory.create(info.directive.payload, SoundPayload::class.java)
            if (payload == null) {
                Logger.d(
                    TAG,
                    "[handleSoundBeepDirective] invalid payload: ${info.directive.payload}"
                )
                setHandlingFailed(
                    info,
                    "[handleSoundBeepDirective] invalid payload: ${info.directive.payload}"
                )
                return
            }
            val beepName = payload.beepName
            val playServiceId = payload.playServiceId

            executor.submit {
                val beep = SoundProvider.Beep.values().find { it.value == beepName }
                    ?: SoundProvider.Beep.UNKNOWN
                val sourceId = mediaPlayer.setSource(soundProvider.getContentUri(beep))
                if (!sourceId.isError() && mediaPlayer.play(sourceId)) {
                    sendBeepSucceededEvent(playServiceId)
                } else {
                    sendBeepFailedEvent(playServiceId)
                }
                setHandlingCompleted(info)
            }
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            info.result?.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, description: String) {
            info.result?.setFailed(description)
            removeDirective(info)
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[BEEP] = BlockingPolicy(
                BlockingPolicy.MEDIUM_AUDIO,
                true
            )

            return configuration
        }

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }

        private fun sendBeepSucceededEvent(playServiceId: String) {
            sendEvent(NAME_BEEP_SUCCEEDED, playServiceId)
        }

        private fun sendBeepFailedEvent(playServiceId: String) {
            sendEvent(NAME_BEEP_FAILED, playServiceId)
        }

        private fun sendEvent(name: String, playServiceId: String) {
            Logger.d(TAG, "[sendEvent] name: $name, playServiceId: $playServiceId")
            val request = EventMessageRequest.Builder(
                contextManager.getContextWithoutUpdate(namespaceAndName),
                NAMESPACE,
                name,
                VERSION
            ).payload(JsonObject().apply {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, playServiceId)
            }.toString()).build()
            messageSender.sendMessage(request)
        }
    }
}