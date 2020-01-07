package com.skt.nugu.sdk.client.agent.factory

import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.capabilityagents.display.DisplayAudioPlayerAgent
import com.skt.nugu.sdk.core.capabilityagents.impl.*
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.interfaces.capability.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.core.interfaces.capability.light.AbstractLightAgent
import com.skt.nugu.sdk.core.interfaces.capability.location.AbstractLocationAgent
import com.skt.nugu.sdk.core.interfaces.capability.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.core.interfaces.capability.movement.AbstractMovementAgent
import com.skt.nugu.sdk.core.interfaces.capability.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.AbstractSystemAgent
import com.skt.nugu.sdk.core.interfaces.capability.text.AbstractTextAgent
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
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
                    getDialogUXStateAggregator().addListener(this)
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
                DefaultFocusChannel.CONTENT_CHANNEL_NAME
            ).apply {
                getVisualFocusManager()?.let {
                    val displayAudioPlayerAgent = DisplayAudioPlayerAgent(
                        it,
                        getContextManager(),
                        getMessageSender(),
                        getPlaySynchronizer(),
                        getInputManagerProcessor(),
                        DefaultFocusChannel.CONTENT_CHANNEL_NAME
                    )
                    setDisplayAgent(displayAudioPlayerAgent)
                    addListener(displayAudioPlayerAgent)
                    getDirectiveSequencer().addDirectiveHandler(displayAudioPlayerAgent)
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
            val focusManager = getVisualFocusManager()
            if(focusManager != null) {
                DefaultDisplayAgent(
                    focusManager,
                    getContextManager(),
                    getMessageSender(),
                    getPlaySynchronizer(),
                    getInputManagerProcessor(),
                    DefaultFocusChannel.DIALOG_CHANNEL_NAME
                ).apply {
                    getDirectiveSequencer().addDirectiveHandler(this)
                }
            } else {
                null
            }
        }
    }

    val EXTENSION = object : ExtensionAgentFactory {
        override fun create(container: SdkContainer): AbstractExtensionAgent? = with(container){
            val client = getExtensionClient()
            if(client != null) {
                DefaultExtensionAgent(
                    getContextManager(),
                    getMessageSender()
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
            )
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
                getInputManagerProcessor(),
                DefaultFocusChannel.DIALOG_CHANNEL_NAME
            ).apply {
                getDirectiveSequencer().addDirectiveHandler(this)
                getDialogUXStateAggregator().addListener(this)
            }
        }
    }
}