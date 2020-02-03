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
package com.skt.nugu.sdk.client.agent.factory

import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.DefaultDelegationAgent
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.DefaultExtensionAgent
import com.skt.nugu.sdk.agent.DefaultLightAgent
import com.skt.nugu.sdk.agent.DefaultLocationAgent
import com.skt.nugu.sdk.agent.DefaultMicrophoneAgent
import com.skt.nugu.sdk.agent.DefaultMovementAgent
import com.skt.nugu.sdk.agent.DefaultSpeakerAgent
import com.skt.nugu.sdk.agent.DefaultSystemAgent
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.DefaultTextAgent
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.agent.display.AudioPlayerTemplateHandler
import com.skt.nugu.sdk.agent.asr.AbstractASRAgent
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.agent.display.AbstractDisplayAgent
import com.skt.nugu.sdk.agent.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.agent.light.AbstractLightAgent
import com.skt.nugu.sdk.agent.location.AbstractLocationAgent
import com.skt.nugu.sdk.agent.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.agent.movement.AbstractMovementAgent
import com.skt.nugu.sdk.agent.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.text.AbstractTextAgent
import com.skt.nugu.sdk.agent.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy

object DefaultAgentFactory {
    val ASR = object : ASRAgentFactory {
        override fun create(container: SdkContainer): AbstractASRAgent {
            return with(container) {
                DefaultASRAgent(
                    getInputManagerProcessor(),
                    getAudioFocusManager(),
                    getMessageSender(),
                    getContextManager(),
                    getDialogSessionManager(),
                    getAudioProvider(),
                    getAudioEncoder(),
                    getEndPointDetector(),
                    getEpdTimeoutMillis(),
                    DefaultFocusChannel.DIALOG_CHANNEL_NAME
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                    getDialogSessionManager().addListener(this)
                }
            }
        }
    }

    val AUDIO_PLAYER = object : AudioPlayerAgentFactory {
        override fun create(container: SdkContainer): AbstractAudioPlayerAgent = with(container) {
            DefaultAudioPlayerAgent(
                getPlayerFactory().createAudioPlayer(),
                getMessageSender(),
                getAudioFocusManager(),
                getContextManager(),
                getPlaybackRouter(),
                getPlaySynchronizer(),
                getAudioPlayStackManager(),
                DefaultFocusChannel.CONTENT_CHANNEL_NAME
            ).apply {
                val audioPlayerMetadataDirectiveHandler = AudioPlayerMetadataDirectiveHandler()
                    .apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }

                AudioPlayerLyricsDirectiveHandler(getContextManager(), getMessageSender(), this, this).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }

                AudioPlayerTemplateHandler(
                    getPlaySynchronizer(),
                    getDisplayPlayStackManager()
                ).apply {
                    setDisplay(this)
                    getDirectiveSequencer().addDirectiveHandler(this)
                    getDirectiveGroupProcessor().addDirectiveGroupPreprocessor(
                        AudioPlayerDirectivePreProcessor()
                    )
                    audioPlayerMetadataDirectiveHandler.addListener(this)
                }


                getDirectiveSequencer().addDirectiveHandler(this)
            }
        }
    }

    val DELEGATION = object : DelegationAgentFactory {
        override fun create(container: SdkContainer): AbstractDelegationAgent? = with(container) {
            val client = getDelegationClient()
            if(client != null) {
                DefaultDelegationAgent(
                    getContextManager(),
                    getMessageSender(),
                    getInputManagerProcessor(),
                    client
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                    getContextManager().setStateProvider(namespaceAndName, this)
                    // update delegate initial state
                    getContextManager().setState(
                        namespaceAndName,
                        "",
                        StateRefreshPolicy.SOMETIMES,
                        0
                    )
                }
            } else {
                null
            }
        }
    }


    val TEMPLATE = object : DisplayAgentFactory {
        override fun create(container: SdkContainer): AbstractDisplayAgent? = with(container) {
            DefaultDisplayAgent(
                getContextManager(),
                getMessageSender(),
                getPlaySynchronizer(),
                getDisplayPlayStackManager(),
                getInputManagerProcessor()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
            }
        }
    }

    val EXTENSION = object : ExtensionAgentFactory {
        override fun create(container: SdkContainer): AbstractExtensionAgent? = with(container){
            val client = getExtensionClient()
            if(client != null) {
                DefaultExtensionAgent(
                    getContextManager(),
                    getMessageSender(),
                    getInputManagerProcessor()
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                    setClient(client)
                }
            } else {
                null
            }
        }
    }

    val LIGHT = object : LightAgentFactory {
        override fun create(container: SdkContainer): AbstractLightAgent? = with(container) {
            val light = getLight()
            if(light != null) {
                DefaultLightAgent(
                    getMessageSender(),
                    getContextManager(),
                    light
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }
            } else {
                null
            }
        }
    }

    val LOCATION = object : LocationAgentFactory {
        override fun create(container: SdkContainer): AbstractLocationAgent = with(container) {
            DefaultLocationAgent().apply {
                getContextManager().setStateProvider(namespaceAndName, this)
            }
        }
    }

    val MICROPHONE = object : MicrophoneAgentFactory {
        override fun create(container: SdkContainer): AbstractMicrophoneAgent = with(container) {
            DefaultMicrophoneAgent(
                getMessageSender(),
                getContextManager(),
                getMicrophone()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
            }
        }
    }

    val MOVEMENT = object : MovementAgentFactory {
        override fun create(container: SdkContainer): AbstractMovementAgent? = with(container) {
            val controller = getMovementController()
            if(controller != null) {
                DefaultMovementAgent(
                    getContextManager(),
                    getMessageSender(),
                    controller
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }
            } else {
                null
            }
        }
    }

    val SPEAKER = object : SpeakerAgentFactory {
        override fun create(container: SdkContainer): AbstractSpeakerAgent = with(container) {
            DefaultSpeakerAgent(
                getContextManager(),
                getMessageSender()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
                getSpeakerFactory().let {
                    addSpeaker(it.createNuguSpeaker())
                    addSpeaker(it.createAlarmSpeaker())
                    it.createCallSpeaker()?.let {speaker->
                        addSpeaker(speaker)
                    }
                    it.createExternalSpeaker()?.let {speaker->
                        addSpeaker(speaker)
                    }
                }
            }
        }
    }

    val SYSTEM = object : SystemAgentFactory {
        /**
         * Create an instance of Impl
         * initializing is performed at default initializer
         */
        override fun create(container: SdkContainer): AbstractSystemAgent = with(container) {
            DefaultSystemAgent(
                getMessageSender(),
                getConnectionManager(),
                getContextManager(),
                getBatteryStatusProvider()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
            }
        }
    }

    val TEXT = object : TextAgentFactory {
        override fun create(container: SdkContainer): AbstractTextAgent = with(container) {
            DefaultTextAgent(
                getMessageSender(),
                getContextManager(),
                getInputManagerProcessor()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
                getDialogSessionManager().addListener(this)
            }
        }
    }

    val TTS = object : TTSAgentFactory {
        override fun create(container: SdkContainer): AbstractTTSAgent = with(container) {
            DefaultTTSAgent(
                getPlayerFactory().createSpeakPlayer(),
                getMessageSender(),
                getAudioFocusManager(),
                getContextManager(),
                getPlaySynchronizer(),
                getAudioPlayStackManager(),
                getInputManagerProcessor(),
                DefaultFocusChannel.DIALOG_CHANNEL_NAME
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
            }
        }
    }
}