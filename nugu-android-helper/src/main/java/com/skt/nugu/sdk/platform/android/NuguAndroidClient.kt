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
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.microphone.Microphone
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
import com.skt.nugu.sdk.agent.asr.AbstractASRAgent
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.battery.DefaultBatteryAgent
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface
import com.skt.nugu.sdk.agent.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.agent.bluetooth.BluetoothProvider
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
import com.skt.nugu.sdk.agent.location.AbstractLocationAgent
import com.skt.nugu.sdk.agent.location.LocationAgentInterface
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.agent.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.agent.screen.Screen
import com.skt.nugu.sdk.agent.speaker.*
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.agent.factory.*
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregator
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.utils.ImmediateBooleanFuture
import com.skt.nugu.sdk.core.utils.Logger
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
            override fun createNuguSpeaker(): Speaker? =
                object : AndroidAudioSpeaker(context, AudioManager.STREAM_MUSIC) {
                    override fun getSpeakerType() = Speaker.Type.NUGU
                }

            override fun createAlarmSpeaker(): Speaker? =
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
        internal var screen: Screen? = null
        internal var audioFocusInteractorFactory: AudioFocusInteractorFactory? =
            AndroidAudioFocusInteractor.Factory(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        internal var bluetoothProvider: BluetoothProvider? = null

        internal var dialogUXStateTransitionDelay: Long = 200L

        internal var enableDisplayLifeCycleManagement = true

        internal val agentFactoryMap = HashMap<String, AgentFactory<*>>()

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

        /**
         * @param bluetoothProvider the bluetooth to be controlled by NUGU
         */
        fun bluetoothProvider(bluetoothProvider: BluetoothProvider?) = apply { this.bluetoothProvider = bluetoothProvider }

        /**
         * @param delay (milliseconds) the transition delay for idle state of DialogUXState
         */
        fun dialogUXStateTransitionDelay(delay: Long) = apply { this.dialogUXStateTransitionDelay = delay }

        /**
         * @param enable the flag to enable or disable managing lifecycle for display.
         * If want to manage the display's lifecycle yourself, disable (set to false).
         */
        fun enableDisplayLifeCycleManagement(enable: Boolean) = apply {this.enableDisplayLifeCycleManagement = enable}

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) =
            apply { agentFactoryMap[namespace] = factory }

        fun build(): NuguAndroidClient {
            return NuguAndroidClient(this)
        }
    }

    private val dialogUXStateAggregator =
        DialogUXStateAggregator(builder.dialogUXStateTransitionDelay)

    private val playbackRouter: PlaybackRouter = com.skt.nugu.sdk.agent.playback.impl.PlaybackRouter()

    private val client: NuguClient = NuguClient.Builder(
        builder.authDelegate
    ).logger(AndroidLogger())
        .transportFactory(builder.transportFactory)
        .sdkVersion(BuildConfig.VERSION_NAME)
        .apply {
            builder.agentFactoryMap.forEach {
                addAgentFactory(it.key, it.value)
            }
            addAgentFactory(AbstractASRAgent.NAMESPACE, object : ASRAgentFactory {
                override fun create(container: SdkContainer): AbstractASRAgent {
                    return with(container) {
                        DefaultASRAgent(
                            getInputManagerProcessor(),
                            getAudioFocusManager(),
                            getMessageSender(),
                            getContextManager(),
                            getDialogSessionManager(),
                            builder.defaultAudioProvider,
                            SpeexEncoder(),
                            builder.endPointDetector,
                            builder.defaultEpdTimeoutMillis,
                            DefaultFocusChannel.DIALOG_CHANNEL_NAME
                        ).apply {
                            getDirectiveSequencer().addDirectiveHandler(this)
                            getDialogSessionManager().addListener(this)
                            dialogUXStateAggregator.addListener(this)
                        }
                    }
                }
            })

            addAgentFactory(DefaultSpeakerAgent.NAMESPACE, object : AgentFactory<DefaultSpeakerAgent> {
                override fun create(container: SdkContainer): DefaultSpeakerAgent =
                    with(container) {
                        DefaultSpeakerAgent(
                            getContextManager(),
                            getMessageSender()
                        ).apply {
                            getDirectiveSequencer().addDirectiveHandler(this)
                            builder.speakerFactory.let {
                                it.createNuguSpeaker()?.let { speaker ->
                                    addSpeaker(speaker)
                                }
                                it.createAlarmSpeaker()?.let { speaker ->
                                    addSpeaker(speaker)
                                }
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
                        builder.playerFactory.createAudioPlayer(),
                        getMessageSender(),
                        getAudioFocusManager(),
                        getContextManager(),
                        playbackRouter,
                        getPlaySynchronizer(),
                        getAudioPlayStackManager(),
                        DefaultFocusChannel.CONTENT_CHANNEL_NAME,
                        DefaultFocusChannel.CONTENT_CHANNEL_PRIORITY,
                        builder.enableDisplayLifeCycleManagement
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
                            getDisplayPlayStackManager(),
                            DefaultFocusChannel.CONTENT_CHANNEL_PRIORITY
                        ).apply {
                            setDisplay(this)
                            getDirectiveSequencer().addDirectiveHandler(this)
                            getDirectiveGroupProcessor().addDirectiveGroupPreprocessor(
                                AudioPlayerDirectivePreProcessor()
                            )
                            audioPlayerMetadataDirectiveHandler.addListener(this)
                        }


                        getDirectiveGroupProcessor().addListener(this)
                        getDirectiveSequencer().addDirectiveHandler(this)
                    }
                }
            })

            addAgentFactory(DefaultTTSAgent.NAMESPACE, object : AgentFactory<DefaultTTSAgent> {
                override fun create(container: SdkContainer): DefaultTTSAgent = with(container) {
                    DefaultTTSAgent(
                        builder.playerFactory.createSpeakPlayer(),
                        getMessageSender(),
                        getAudioFocusManager(),
                        getContextManager(),
                        getPlaySynchronizer(),
                        getAudioPlayStackManager(),
                        getInputManagerProcessor(),
                        DefaultFocusChannel.DIALOG_CHANNEL_NAME,
                        DefaultFocusChannel.DIALOG_CHANNEL_PRIORITY
                    ).apply {
                        getDirectiveSequencer().addDirectiveHandler(this)
                        dialogUXStateAggregator.addListener(this)
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
            builder.screen?.let {
                addAgentFactory(DefaultScreenAgent.NAMESPACE, object : AgentFactory<DefaultScreenAgent> {
                    override fun create(container: SdkContainer): DefaultScreenAgent =
                        with(container) {
                            DefaultScreenAgent(
                                getContextManager(),
                                getMessageSender(),
                                it
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                                getContextManager().setStateProvider(namespaceAndName, this)
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
                addAgentFactory(DefaultExtensionAgent.NAMESPACE, object : AgentFactory<DefaultExtensionAgent> {
                    override fun create(container: SdkContainer): DefaultExtensionAgent =
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
            addAgentFactory(DefaultTextAgent.NAMESPACE, object: AgentFactory<DefaultTextAgent> {
                override fun create(container: SdkContainer): DefaultTextAgent = with(container) {
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
                        getInputManagerProcessor(),
                        DefaultFocusChannel.DIALOG_CHANNEL_PRIORITY,
                        builder.enableDisplayLifeCycleManagement
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

            builder.bluetoothProvider?.let {
                addAgentFactory(DefaultBluetoothAgent.NAMESPACE, object : AgentFactory<DefaultBluetoothAgent> {
                    override fun create(container: SdkContainer): DefaultBluetoothAgent =
                        with(container) {
                            DefaultBluetoothAgent(
                                getMessageSender(),
                                getContextManager(),
                                it
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                            }
                        }
                })
            }
        }
        .build()

    override val audioPlayerAgent: AbstractAudioPlayerAgent?
        get() = try {
            client.getAgent(AbstractAudioPlayerAgent.NAMESPACE) as AbstractAudioPlayerAgent
        } catch (th: Throwable) {
            null
        }
    override val ttsAgent: TTSAgentInterface?
        get() = try {
            client.getAgent(DefaultTTSAgent.NAMESPACE) as TTSAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val displayAgent: DisplayAgentInterface?
        get() = try {
            client.getAgent(DefaultDisplayAgent.NAMESPACE) as DisplayAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val extensionAgent: ExtensionAgentInterface?
        get() = try {
            client.getAgent(DefaultExtensionAgent.NAMESPACE) as ExtensionAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val asrAgent: ASRAgentInterface?
        get() = try {
            client.getAgent(AbstractASRAgent.NAMESPACE) as ASRAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val textAgent: TextAgentInterface?
        get() = try {
            client.getAgent(DefaultTextAgent.NAMESPACE) as TextAgentInterface
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
    override val bluetoothAgent: BluetoothAgentInterface?
        get() = try {
            client.getAgent(DefaultBluetoothAgent.NAMESPACE) as BluetoothAgentInterface
        } catch (th: Throwable) {
            null
        }
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
            ).apply {
                client.getSdkContainer().getDirectiveGroupProcessor().addListener(this)
            }
        } else {
            null
        }

        ttsAgent?.addListener(dialogUXStateAggregator)
        asrAgent?.addOnStateChangeListener(dialogUXStateAggregator)
        client.getSdkContainer().getDialogSessionManager().addListener(dialogUXStateAggregator)
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
            client.getAgent(DefaultSpeakerAgent.NAMESPACE) as SpeakerManagerInterface
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

    override fun getPlaybackRouter(): PlaybackRouter = playbackRouter

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
        asrAgent?.addOnStateChangeListener(listener)
    }

    override fun removeASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        asrAgent?.removeOnStateChangeListener(listener)
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakeupInfo: WakeupInfo?
    ): Future<Boolean> {
        Logger.d(
            TAG,
            "[startRecognition] wakeupInfo: $wakeupInfo"
        )

        return asrAgent?.startRecognition(
            audioInputStream,
            audioFormat,
            wakeupInfo
        ) ?: ImmediateBooleanFuture(false)
    }

    override fun stopRecognition() {
        Logger.d(TAG, "[stopRecognition]")
        asrAgent?.stopRecognition()
    }

    override fun addASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent?.addOnResultListener(listener)
    }

    override fun removeASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent?.removeOnResultListener(listener)
    }

    override fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener?) {
        textAgent?.requestTextInput(text, listener)
    }

    override fun requestTTS(
        text: String,
        playServiceId: String,
        listener: TTSAgentInterface.OnPlaybackListener?
    ) {
        ttsAgent?.requestTTS(text, playServiceId, listener)
    }

    override fun localStopTTS() {
        ttsAgent?.stopTTS(false)
    }

    override fun cancelTTSAndOthers() {
        ttsAgent?.stopTTS(true)
    }

    override fun getDisplay(): DisplayAggregatorInterface? = displayAggregator

    override fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        displayAggregator?.setRenderer(renderer)
    }

    override fun shutdown() {
        ttsAgent?.stopTTS(true)
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