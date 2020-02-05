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

import com.skt.nugu.sdk.agent.*
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.agent.display.AudioPlayerTemplateHandler
import com.skt.nugu.sdk.agent.asr.AbstractASRAgent
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.display.AbstractDisplayAgent
import com.skt.nugu.sdk.agent.display.ControlFocusDirectiveHandler
import com.skt.nugu.sdk.agent.display.ControlScrollDirectiveHandler
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.tts.AbstractTTSAgent

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

    val TEMPLATE = object : DisplayAgentFactory {
        override fun create(container: SdkContainer): AbstractDisplayAgent = with(container) {
            DefaultDisplayAgent(
                getContextManager(),
                getMessageSender(),
                getPlaySynchronizer(),
                getDisplayPlayStackManager(),
                getInputManagerProcessor()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)

                ControlFocusDirectiveHandler(this, getContextManager(), getMessageSender(), namespaceAndName).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }
                ControlScrollDirectiveHandler(this, getContextManager(), getMessageSender(), namespaceAndName).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
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
                getContextManager()
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
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