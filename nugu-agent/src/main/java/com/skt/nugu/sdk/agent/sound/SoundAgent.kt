/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent.sound

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractCapabilityAgent
import com.skt.nugu.sdk.agent.beep.BeepPlaybackController
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class SoundAgent(
    private val mediaPlayer: UriSourcePlayablePlayer,
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val soundProvider: SoundProvider,
    private val focusChannelName: String,
    private val focusManager: FocusManagerInterface,
    private val beepDirectiveDelegate: BeepDirectiveDelegate?,
    private val beepPlaybackController: BeepPlaybackController,
    private val beepPlaybackPriority: Int
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

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    private abstract inner class BeepPlaybackControllerSource : BeepPlaybackController.Source {
        override val priority: Int = beepPlaybackPriority
    }

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
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )

        contextSetter.setState(
            namespaceAndName,
            contextState,
            StateRefreshPolicy.NEVER,
            contextType,
            stateRequestToken
        )
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
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
        val payload = BeepDirective.Payload.fromJson(info.directive.payload)
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

            val focusInterfaceName = "$NAMESPACE${info.directive.getMessageId()}"
            focusManager.acquireChannel(focusChannelName, object : ChannelObserver {
                var isHandled = false

                override fun onFocusChanged(newFocus: FocusState) {
                    val channelObserver = this

                    executor.submit {
                        Logger.d(
                            TAG,
                            "[onFocusChanged] focus: $newFocus, name: $focusInterfaceName"
                        )
                        when (newFocus) {
                            FocusState.BACKGROUND,
                            FocusState.FOREGROUND -> {
                                if (!isHandled) {
                                    isHandled = true
                                    object: BeepPlaybackControllerSource() {
                                        override fun play() {
                                            val success = if (beepDirectiveDelegate != null) {
                                                beepDirectiveDelegate.beep(
                                                    mediaPlayer,
                                                    soundProvider,
                                                    BeepDirective(info.directive.header, payload)
                                                )
                                            } else {
                                                val sourceId =
                                                    mediaPlayer.setSource(
                                                        soundProvider.getContentUri(
                                                            payload.beepName
                                                        ), null
                                                    )
                                                !sourceId.isError() && mediaPlayer.play(sourceId)
                                            }

                                            if (success) {
                                                sendBeepSucceededEvent(
                                                    playServiceId,
                                                    referrerDialogRequestId
                                                )
                                            } else {
                                                sendBeepFailedEvent(playServiceId, referrerDialogRequestId)
                                            }

                                            beepPlaybackController.removeSource(this)
                                            focusManager.releaseChannel(focusChannelName, channelObserver)
                                        }
                                    }.let {
                                        beepPlaybackController.addSource(it)
                                    }
                                }
                            }
                            FocusState.NONE -> {
                                if (!isHandled) {
                                    sendBeepFailedEvent(playServiceId, referrerDialogRequestId)
                                }
                            }
                        }
                    }
                }
            }, focusInterfaceName)
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
        this[BEEP] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
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
                ).enqueue(null)
            }
        }, namespaceAndName)
    }
}
