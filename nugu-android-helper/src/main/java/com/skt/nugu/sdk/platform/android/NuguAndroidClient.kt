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
package com.skt.nugu.sdk.platform.android

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.skt.nugu.sdk.agent.*
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.client.ClientHelperInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.agent.delegation.DelegationClient
import com.skt.nugu.sdk.agent.light.Light
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.microphone.Microphone
import com.skt.nugu.sdk.agent.movement.MovementController
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.agent.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.battery.BatteryStatusProvider
import com.skt.nugu.sdk.platform.android.log.AndroidLogger
import com.skt.nugu.sdk.platform.android.mediaplayer.AndroidMediaPlayer
import com.skt.nugu.sdk.platform.android.speaker.AndroidAudioSpeaker
import com.skt.nugu.sdk.external.jademarble.SpeexEncoder
import com.skt.nugu.sdk.external.silvertray.NuguOpusPlayer
import com.skt.nugu.sdk.client.NuguClient
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.battery.DefaultBatteryAgent
import com.skt.nugu.sdk.agent.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.agent.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.agent.display.*
import com.skt.nugu.sdk.client.NuguClientInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.platform.android.mediaplayer.IntegratedMediaPlayer
import com.skt.nugu.sdk.platform.android.battery.AndroidBatteryStatusProvider
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.agent.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.agent.light.AbstractLightAgent
import com.skt.nugu.sdk.agent.location.AbstractLocationAgent
import com.skt.nugu.sdk.agent.location.LocationAgentInterface
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.agent.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.agent.movement.AbstractMovementAgent
import com.skt.nugu.sdk.agent.screen.AbstractScreenAgent
import com.skt.nugu.sdk.agent.screen.Screen
import com.skt.nugu.sdk.agent.speaker.*
import com.skt.nugu.sdk.agent.text.AbstractTextAgent
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.agent.factory.*
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregator
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.platform.android.focus.AudioFocusInteractor
import com.skt.nugu.sdk.platform.android.focus.AndroidAudioFocusInteractor
import com.skt.nugu.sdk.platform.android.focus.AudioFocusInteractorFactory
import java.util.concurrent.Future

/**
 * Implementation of [ClientHelperInterface] for Android
 *
 * This class is a basic client to interact with NUGU at Android.
 *
 * [android.app.Application]'s onCreate is a good place to initialize.
 *
 * It require many components to initialize. so, we provide a [Builder] to create and the most of components set to default.
 *
 */
class NuguAndroidClient private constructor(
    builder: Builder
) : ClientHelperInterface
    , NuguClientInterface {
    companion object {
        private const val TAG = "ANuguClient"
    }

    /**
     * The builder for [NuguAndroidClient]
     * @param context the android context (recommend to use application's context)
     * @param authDelegate the delegate implementation for authorization
     * @param defaultAudioProvider the default audio provider which used as default and to answer(ExpectSpeech).
     */
    data class Builder(
        internal val context: Context,
        internal val authDelegate: AuthDelegate,
        internal val defaultAudioProvider: AudioProvider
    ) {
        internal var playerFactory: PlayerFactory = object :
            PlayerFactory {
            override fun createSpeakPlayer(): MediaPlayerInterface = IntegratedMediaPlayer(
                AndroidMediaPlayer(context, MediaPlayer()),
                NuguOpusPlayer(AudioManager.STREAM_MUSIC)
            )

            override fun createAudioPlayer(): MediaPlayerInterface = IntegratedMediaPlayer(
                AndroidMediaPlayer(context, MediaPlayer()),
                NuguOpusPlayer(AudioManager.STREAM_MUSIC)
            )

            override fun createAlertsPlayer(): MediaPlayerInterface = IntegratedMediaPlayer(
                AndroidMediaPlayer(context, MediaPlayer()),
                NuguOpusPlayer(AudioManager.STREAM_MUSIC)
            )

            override fun createBeepPlayer(): UriSourcePlayablePlayer =
                AndroidMediaPlayer(context, MediaPlayer())
        }
        internal var speakerFactory: SpeakerFactory = object : SpeakerFactory {
            override fun createNuguSpeaker(): Speaker =
                object : AndroidAudioSpeaker(context, AudioManager.STREAM_MUSIC) {
                    override fun getSpeakerType() = Speaker.Type.NUGU
                }

            override fun createAlarmSpeaker(): Speaker =
                object : AndroidAudioSpeaker(context, AudioManager.STREAM_ALARM) {
                    override fun getSpeakerType() = Speaker.Type.ALARM
                }

            override fun createCallSpeaker(): Speaker? = null
            override fun createExternalSpeaker(): Speaker? = null
        }
        internal var defaultEpdTimeoutMillis: Long = 10000L
        internal var transportFactory: TransportFactory = GrpcTransportFactory()
        internal var endPointDetector: AudioEndPointDetector? = null
        internal var batteryStatusProvider: BatteryStatusProvider? =
            AndroidBatteryStatusProvider(context)
        internal var defaultMicrophone: Microphone? = null
        internal var delegationClient: DelegationClient? = null
        internal var extensionClient: ExtensionAgentInterface.Client? = null
        internal var movementController: MovementController? = null
        internal var light: Light? = null
        internal var screen: Screen? = null
        internal var audioFocusInteractorFactory: AudioFocusInteractorFactory? =
            AndroidAudioFocusInteractor.Factory(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        internal val agentFactoryMap = HashMap<String, AgentFactory<*>>()
        internal var asrAgentFactory: ASRAgentFactory = DefaultAgentFactory.ASR

        /**
         * @param factory the player factory to create players used at NUGU
         */
        fun playerFactory(factory: PlayerFactory) = apply { playerFactory = factory }

        /**
         * @param factory the speaker factory to create speakers controlled by NUGU
         */
        fun speakerFactory(factory: SpeakerFactory) =
            apply { speakerFactory = factory }

        /**
         * @param epdTimeoutMillis the default timeout of EPD
         */
        fun defaultEpdTimeoutMillis(epdTimeoutMillis: Long) =
            apply { defaultEpdTimeoutMillis = epdTimeoutMillis }

        /**
         * @param endPointDetector the end point detector used to speech recognizing at NUGU
         */
        fun endPointDetector(endPointDetector: AudioEndPointDetector?) =
            apply { this.endPointDetector = endPointDetector }

        /**
         * @param batteryStatusProvider the batter status provider
         */
        fun batteryStatusProvider(batteryStatusProvider: BatteryStatusProvider?) =
            apply { this.batteryStatusProvider = batteryStatusProvider }

        /**
         * @param microphone the default microphone which controlled by NUGU
         */
        fun defaultMicrophone(microphone: Microphone?) =
            apply { defaultMicrophone = microphone }

        /**
         * @param client the client which delegate directive of Delegate
         */
        fun delegationClient(client: DelegationClient?) =
            apply { delegationClient = client }

        /**
         * @param client the client which do directive of Extension
         */
        fun extensionClient(client: ExtensionAgentInterface.Client?) =
            apply { extensionClient = client }

        /**
         * @param controller the controller which control move directive
         */
        fun movementController(controller: MovementController?) =
            apply { movementController = controller }

        /**
         * @param light the light to be controlled by NUGU
         */
        fun light(light: Light?) = apply { this.light = light }

        /**
         * @param screen the screen to be controlled by NUGU
         */
        fun screen(screen: Screen?) = apply { this.screen = screen }

        /**
         * @param factory the transport factory for network
         */
        fun transportFactory(factory: TransportFactory) = apply { transportFactory = factory }

        /**
         * @param factory the audio focus interactor factory
         */
        fun audioFocusInteractorFactory(factory: AudioFocusInteractorFactory?) =
            apply { audioFocusInteractorFactory = factory }

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) =
            apply { agentFactoryMap[namespace] = factory }

        fun build(): NuguAndroidClient {
            return NuguAndroidClient(this)
        }
    }

    private val dialogUXStateAggregator =
        DialogUXStateAggregator()

    private val client: NuguClient = NuguClient.Builder(
        builder.playerFactory,
        builder.authDelegate,
        builder.endPointDetector,
        builder.defaultAudioProvider,
        SpeexEncoder()
    ).logger(AndroidLogger())
        .defaultEpdTimeoutMillis(builder.defaultEpdTimeoutMillis)
        .transportFactory(builder.transportFactory)
        .sdkVersion(BuildConfig.VERSION_NAME)
        .apply {
            builder.agentFactoryMap.forEach {
                addAgentFactory(it.key, it.value)
            }
            addAgentFactory(AbstractSpeakerAgent.NAMESPACE, object : SpeakerAgentFactory {
                override fun create(container: SdkContainer): AbstractSpeakerAgent =
                    with(container) {
                        DefaultSpeakerAgent(
                            getContextManager(),
                            getMessageSender()
                        ).apply {
                            getDirectiveSequencer().addDirectiveHandler(this)
                            builder.speakerFactory.let {
                                addSpeaker(it.createNuguSpeaker())
                                addSpeaker(it.createAlarmSpeaker())
                                it.createCallSpeaker()?.let { speaker ->
                                    addSpeaker(speaker)
                                }
                                it.createExternalSpeaker()?.let { speaker ->
                                    addSpeaker(speaker)
                                }
                            }
                        }
                    }
            })

            addAgentFactory(AbstractAudioPlayerAgent.NAMESPACE, object: AudioPlayerAgentFactory {
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
            })

            builder.batteryStatusProvider?.let {
                addAgentFactory(
                    DefaultBatteryAgent.NAMESPACE,
                    object : AgentFactory<DefaultBatteryAgent> {
                        override fun create(container: SdkContainer): DefaultBatteryAgent =
                            DefaultBatteryAgent(it, container.getContextManager())
                    })
            }
            builder.defaultMicrophone?.let {
                addAgentFactory(
                    AbstractMicrophoneAgent.NAMESPACE,
                    object : AgentFactory<AbstractMicrophoneAgent> {
                        override fun create(container: SdkContainer): DefaultMicrophoneAgent =
                            with(container) {
                                DefaultMicrophoneAgent(
                                    getMessageSender(),
                                    getContextManager(),
                                    it
                                ).apply {
                                    getDirectiveSequencer().addDirectiveHandler(this)
                                }
                            }
                    })
            }
            builder.light?.let {
                addAgentFactory(AbstractLightAgent.NAMESPACE, object : LightAgentFactory {
                    override fun create(container: SdkContainer): AbstractLightAgent =
                        with(container) {
                            DefaultLightAgent(
                                getMessageSender(),
                                getContextManager(),
                                it
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                            }
                        }
                })
            }
            builder.movementController?.let {
                addAgentFactory(AbstractMovementAgent.NAMESPACE, object : MovementAgentFactory {
                    override fun create(container: SdkContainer): AbstractMovementAgent =
                        with(container) {
                            DefaultMovementAgent(
                                getContextManager(),
                                getMessageSender(),
                                it
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                            }
                        }
                })
            }
            builder.screen?.let {
                addAgentFactory(DefaultScreenAgent.NAMESPACE, object : ScreenAgentFactory {
                    override fun create(container: SdkContainer): AbstractScreenAgent =
                        with(container) {
                            DefaultScreenAgent(
                                getContextManager(),
                                getMessageSender(),
                                it
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                            }
                        }
                })
            }
            builder.delegationClient?.let {
                addAgentFactory(AbstractDelegationAgent.NAMESPACE, object : DelegationAgentFactory {
                    override fun create(container: SdkContainer): AbstractDelegationAgent =
                        with(container) {
                            DefaultDelegationAgent(
                                getContextManager(),
                                getMessageSender(),
                                getInputManagerProcessor(),
                                it
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
                        }
                })
            }
            builder.extensionClient?.let {
                addAgentFactory(AbstractExtensionAgent.NAMESPACE, object : ExtensionAgentFactory {
                    override fun create(container: SdkContainer): AbstractExtensionAgent =
                        with(container) {
                            DefaultExtensionAgent(
                                getContextManager(),
                                getMessageSender(),
                                getInputManagerProcessor()
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                                setClient(it)
                            }
                        }
                })
            }
            addAgentFactory(AbstractLocationAgent.NAMESPACE, object: LocationAgentFactory {
                override fun create(container: SdkContainer): AbstractLocationAgent = with(container) {
                    DefaultLocationAgent().apply {
                        getContextManager().setStateProvider(namespaceAndName, this)
                    }
                }
            })
            addAgentFactory(AbstractTextAgent.NAMESPACE, object: TextAgentFactory {
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
            })

            addAgentFactory(DefaultDisplayAgent.NAMESPACE, object: DisplayAgentFactory {
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
            })

            asrAgentFactory(builder.asrAgentFactory)
        }
        .build()

    override val audioPlayerAgent: AbstractAudioPlayerAgent?
        get() = try {
            client.getAgent(AbstractAudioPlayerAgent.NAMESPACE) as AbstractAudioPlayerAgent
        } catch (th: Throwable) {
            null
        }
    override val ttsAgent: TTSAgentInterface? = client.ttsAgent
    override val displayAgent: DisplayAgentInterface?
        get() = try {
            client.getAgent(DefaultDisplayAgent.NAMESPACE) as DisplayAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val extensionAgent: ExtensionAgentInterface?
        get() = try {
            client.getAgent(AbstractExtensionAgent.NAMESPACE) as ExtensionAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val asrAgent: ASRAgentInterface? = client.asrAgent
    override val textAgent: TextAgentInterface?
        get() = try {
            client.getAgent(AbstractTextAgent.NAMESPACE) as TextAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val locationAgent: LocationAgentInterface?
        get() = try {
            client.getAgent(AbstractLocationAgent.NAMESPACE) as LocationAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val delegationAgent: DelegationAgentInterface?
        get() = try {
            client.getAgent(AbstractDelegationAgent.NAMESPACE) as DelegationAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val systemAgent: SystemAgentInterface = client.systemAgent
    override val networkManager: NetworkManagerInterface = client.networkManager

    private val displayAggregator: DisplayAggregator?

    private val audioFocusInteractor: AudioFocusInteractor?

    init {
        audioFocusInteractor = builder.audioFocusInteractorFactory?.create(client.audioFocusManager)

        val tempDisplayAgent = displayAgent
        val tempAudioPlayerAgent = audioPlayerAgent
        displayAggregator = if (tempDisplayAgent != null && tempAudioPlayerAgent != null) {
            DisplayAggregator(
                tempDisplayAgent,
                tempAudioPlayerAgent
            )
        } else {
            null
        }

        ttsAgent?.addListener(dialogUXStateAggregator)
        asrAgent?.addOnStateChangeListener(dialogUXStateAggregator)
        client.getDialogSessionManager().addListener(dialogUXStateAggregator)
    }

    override fun connect() {
        client.connect()
    }

    override fun disconnect() {
        client.disconnect()
    }

    override fun addConnectionListener(listener: ConnectionStatusListener) {
        client.addConnectionListener(listener)
    }

    override fun removeConnectionListener(listener: ConnectionStatusListener) {
        client.removeConnectionListener(listener)
    }

    override fun getSpeakerManager(): SpeakerManagerInterface? {
        return try {
            client.getAgent(AbstractSpeakerAgent.NAMESPACE) as SpeakerManagerInterface
        } catch (th: Throwable) {
            null
        }
    }

    override fun addSpeakerListener(listener: SpeakerManagerObserver) {
        getSpeakerManager()?.addSpeakerManagerObserver(listener)
    }

    override fun removeSpeakerListener(listener: SpeakerManagerObserver) {
        getSpeakerManager()?.removeSpeakerManagerObserver(listener)
    }

    override fun getPlaybackRouter(): PlaybackRouter = client.getPlaybackRouter()

    override fun addAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent?.addListener(listener)
    }

    override fun removeAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent?.removeListener(listener)
    }

    override fun addDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.addListener(listener)
    }

    override fun removeDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.removeListener(listener)
    }

    override fun addASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        client.addASRListener(listener)
    }

    override fun removeASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        client.removeASRListener(listener)
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakewordStartPosition: Long?,
        wakewordEndPosition: Long?,
        wakewordDetectPosition: Long?
    ): Future<Boolean> {
        return client.startRecognition(
            audioInputStream,
            audioFormat,
            wakewordStartPosition,
            wakewordEndPosition,
            wakewordDetectPosition
        )
    }

    override fun stopRecognition() {
        client.stopRecognition()
    }

    override fun addASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        client.addASRResultListener(listener)
    }

    override fun removeASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        client.removeASRResultListener(listener)
    }

    override fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener?) {
        textAgent?.requestTextInput(text, listener)
    }

    override fun requestTTS(
        text: String,
        playServiceId: String,
        listener: TTSAgentInterface.OnPlaybackListener?
    ) {
        client.requestTTS(text, playServiceId, listener)
    }

    override fun localStopTTS() {
        client.localStopTTS()
    }

    override fun cancelTTSAndOthers() {
        client.cancelTTSAndOthers()
    }

    override fun getDisplay(): DisplayAggregatorInterface? = displayAggregator

    override fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        displayAggregator?.setRenderer(renderer)
    }

    override fun shutdown() {
        audioPlayerAgent?.shutdown()
        client.shutdown()
    }

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        client.setStateProvider(namespaceAndName, stateProvider)
    }

    override fun addSystemAgentListener(listener: SystemAgentInterface.Listener) {
        client.addSystemAgentListener(listener)
    }

    override fun removeSystemAgentListener(listener: SystemAgentInterface.Listener) {
        client.removeSystemAgentListener(listener)
    }
}