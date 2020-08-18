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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.agent.sound.SoundProvider
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
import java.util.concurrent.Executors

class DefaultSoundAgent(
    private val mediaPlayer: UriSourcePlayablePlayer,
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val soundProvider: SoundProvider
) : AbstractCapabilityAgent(NAMESPACE) {

    companion object {
        private const val TAG = "DefaultSoundAgent"

        const val NAMESPACE = "Sound"
        private val VERSION = Version(1, 0)

        /** directives */
        private const val NAME_BEEP = "Beep"

        /** events */
        private const val NAME_BEEP_SUCCEEDED = "BeepSucceeded"
        private const val NAME_BEEP_FAILED = "BeepFailed"

        private val BEEP = NamespaceAndName(NAMESPACE, NAME_BEEP)
        private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"
    }

    internal data class SoundPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("beepName")
        val beepName: String
    )

    private val executor = Executors.newSingleThreadExecutor()

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")

        executor.submit {
            soundProvider.let {
                contextSetter.setState(
                    namespaceAndName,
                    object: ContextState {
                        val state = JsonObject().apply {
                            addProperty("version", VERSION.toString())
                        }.toString()

                        override fun toFullJsonString(): String = state
                        override fun toCompactJsonString(): String = state
                    },
                    StateRefreshPolicy.NEVER,
                    stateRequestToken
                )
            }
        }
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getName()) {
            NAME_BEEP -> handleBeep(info)
            else -> {
                // nothing to do
                Logger.d(TAG, "[handleDirective] unexpected directive: ${info.directive}")
            }
        }
    }

    private fun handleBeep(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, SoundPayload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[handleBeep] unexpected payload: ${info.directive.payload}")
            setHandlingFailed(
                info,
                "[handleBeep] unexpected payload: ${info.directive.payload}"
            )
            return
        }
        setHandlingCompleted(info)

        executor.submit {
            val playServiceId = payload.playServiceId
            val referrerDialogRequestId = info.directive.header.dialogRequestId
            val beepName = try {
                SoundProvider.BeepName.valueOf(payload.beepName)
            } catch (e: IllegalArgumentException) {
                Logger.d(TAG, "[handleBeep] No enum constant : ${payload.beepName}")
                sendBeepFailedEvent(playServiceId, referrerDialogRequestId)
                return@submit
            }
            val sourceId = mediaPlayer.setSource(soundProvider.getContentUri(beepName))
            if (!sourceId.isError() && mediaPlayer.play(sourceId)) {
                sendBeepSucceededEvent(playServiceId, referrerDialogRequestId)
            } else {
                sendBeepFailedEvent(playServiceId, referrerDialogRequestId)
            }
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
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
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[BEEP] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )

        return configuration
    }

    private fun sendBeepSucceededEvent(playServiceId: String, referrerDialogRequestId: String) {
        sendEvent(NAME_BEEP_SUCCEEDED, playServiceId, referrerDialogRequestId)
    }

    private fun sendBeepFailedEvent(playServiceId: String, referrerDialogRequestId: String) {
        sendEvent(NAME_BEEP_FAILED, playServiceId, referrerDialogRequestId)
    }

    private fun sendEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request =
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
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
}
