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
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import com.skt.nugu.sdk.agent.*
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.CancelRecognizeDirectiveHandler
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.battery.BatteryStatusProvider
import com.skt.nugu.sdk.agent.battery.DefaultBatteryAgent
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface
import com.skt.nugu.sdk.agent.bluetooth.BluetoothProvider
import com.skt.nugu.sdk.agent.chips.ChipsAgent
import com.skt.nugu.sdk.agent.chips.ChipsAgentInterface
import com.skt.nugu.sdk.agent.common.tts.TTSScenarioPlayer
import com.skt.nugu.sdk.agent.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.agent.delegation.DelegationClient
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregator
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.display.*
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.MessageClient
import com.skt.nugu.sdk.agent.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.agent.location.LocationAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.agent.microphone.Microphone
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.screen.Screen
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.session.SessionAgent
import com.skt.nugu.sdk.agent.sound.BeepDirectiveDelegate
import com.skt.nugu.sdk.agent.sound.SoundProvider
import com.skt.nugu.sdk.agent.speaker.Speaker
import com.skt.nugu.sdk.agent.speaker.SpeakerFactory
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.agent.system.ExceptionDirectiveDelegate
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.tts.handler.StopDirectiveHandler
import com.skt.nugu.sdk.client.ClientHelperInterface
import com.skt.nugu.sdk.client.NuguClient
import com.skt.nugu.sdk.client.NuguClientInterface
import com.skt.nugu.sdk.client.SdkContainer
import com.skt.nugu.sdk.client.agent.factory.AgentFactory
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.client.port.transport.DefaultTransportFactory
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.OsContextProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.preferences.PreferencesInterface
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.external.jademarble.SpeexEncoder
import com.skt.nugu.sdk.external.silvertray.NuguOpusPlayer
import com.skt.nugu.sdk.platform.android.NuguAndroidClient.Builder
import com.skt.nugu.sdk.platform.android.battery.AndroidBatteryStatusProvider
import com.skt.nugu.sdk.platform.android.beep.AsrBeepPlayer
import com.skt.nugu.sdk.platform.android.beep.AsrBeepResourceProvider
import com.skt.nugu.sdk.platform.android.content.AndroidPreferences
import com.skt.nugu.sdk.platform.android.focus.AndroidAudioFocusInteractor
import com.skt.nugu.sdk.platform.android.focus.AudioFocusInteractor
import com.skt.nugu.sdk.platform.android.focus.AudioFocusInteractorFactory
import com.skt.nugu.sdk.platform.android.log.AndroidLogger
import com.skt.nugu.sdk.platform.android.mediaplayer.AndroidMediaPlayer
import com.skt.nugu.sdk.platform.android.mediaplayer.IntegratedMediaPlayer
import com.skt.nugu.sdk.platform.android.speaker.AndroidAudioSpeaker

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

            override fun createSpeaker(type: Speaker.Type): Speaker? {
                return when (type) {
                    Speaker.Type.NUGU -> object :
                        AndroidAudioSpeaker(context, AudioManager.STREAM_MUSIC) {
                        override fun getSpeakerType() = type
                    }
                    Speaker.Type.ALARM -> object :
                        AndroidAudioSpeaker(context, AudioManager.STREAM_ALARM) {
                        override fun getSpeakerType() = type
                    }
                    else -> null
                }
            }
        }
        internal var defaultEpdTimeoutMillis: Long = 10000L
        internal var transportFactory: TransportFactory = DefaultTransportFactory()
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

        internal var defaultDisplayDuration = 7000L

        internal var textSourceHandler: TextAgentInterface.TextSourceHandler? = null

        internal var enableDisplay: Boolean = true
        internal var enableLocation: Boolean = true

        internal var soundProvider: SoundProvider? = null

        internal var beepDirectiveDelegate: BeepDirectiveDelegate? = null

        internal var messageClient: MessageClient? = null

        internal var enableChips: Boolean = true

        internal var enableAudioPlayer: Boolean = true

        internal var cancelPolicyOnStopTTS: DirectiveHandlerResult.CancelPolicy = DirectiveHandlerResult.POLICY_CANCEL_NONE

        internal var systemExceptionDirectiveDelegate: ExceptionDirectiveDelegate? = null

        internal var asrBeepResourceProvider: AsrBeepResourceProvider? = null

        internal val agentFactoryMap = HashMap<String, AgentFactory<*>>()

        internal var clientVersion: String? = null
            get() = try {
                if (field.isNullOrBlank()) {
                    context.let {
                        it.packageManager.getPackageInfo(it.packageName, 0).versionName
                    }
                } else field
            } catch (t: PackageManager.NameNotFoundException) {
                null
            }

        // Log
        internal var logger: LogInterface? = AndroidLogger()
        // Preferences
        internal var preferences: PreferencesInterface? = AndroidPreferences(context)

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
        fun bluetoothProvider(bluetoothProvider: BluetoothProvider?) =
            apply { this.bluetoothProvider = bluetoothProvider }

        /**
         * @param delay (milliseconds) the transition delay for idle state of DialogUXState
         */
        fun dialogUXStateTransitionDelay(delay: Long) =
            apply { this.dialogUXStateTransitionDelay = delay }

        /**
         * @param enable the flag to enable or disable managing lifecycle for display.
         * If want to manage the display's lifecycle yourself, disable (set to false).
         */
        fun enableDisplayLifeCycleManagement(enable: Boolean) =
            apply { this.enableDisplayLifeCycleManagement = enable }

        /**
         * @param duration the default duration for display that is showing after a scenario finish.
         */
        fun defaultDisplayDuration(duration: Long) =
            apply { this.defaultDisplayDuration = duration }

        /**
         * @param handler the handler for text source directive. If not provided, default behavior at TextAgent.
         */
        fun textSourceHandler(handler: TextAgentInterface.TextSourceHandler?) =
            apply { this.textSourceHandler = handler }

        /**
         * @param enable the flag to enable or disable display.
         */
        fun enableDisplay(enable: Boolean) = apply { this.enableDisplay = enable }

        /**
         * @param enable the flag to enable or disable location.
         */
        fun enableLocation(enable: Boolean) = apply { this.enableLocation = enable }

        /**
         * @param provider the provider for content URIs for sounds.
         */
        fun soundProvider(provider: SoundProvider?) = apply { this.soundProvider = provider }

        /**
         * @param delegate the delegate which handle Sound.Beep directive.
         */
        fun beepDirectiveDelegate(delegate: BeepDirectiveDelegate?) = apply { this.beepDirectiveDelegate = delegate }

        /**
         * @param provider the beep resource provider
         */
        fun asrBeepResourceProvider(provider: AsrBeepResourceProvider?) = apply { this.asrBeepResourceProvider = provider }

        /**
         * @param delegate the delegate which handle System.Exception directive.
         */
        fun systemExceptionDirectiveDelegate(delegate: ExceptionDirectiveDelegate?) = apply { this.systemExceptionDirectiveDelegate = delegate }

        /**
         * @param client the provider for MessageAgent
         */
        fun messageClient(client: MessageClient?) = apply { this.messageClient = client }

        /**
         * @param enable the flag to enable or disable chips.
         */
        fun enableChips(enable: Boolean) = apply { this.enableChips = enable }

        /**
         * @param enable the flag to enable or disable AudioPlayer.
         */
        fun enableAudioPlayer(enable: Boolean) = apply { this.enableAudioPlayer = enable }

        /**
         * @param policy the cancel policy on stop tts
         */
        fun cancelPolicyOnStopTTS(policy: DirectiveHandlerResult.CancelPolicy) = apply { this.cancelPolicyOnStopTTS = policy }

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) =
            apply { agentFactoryMap[namespace] = factory }

        fun clientVersion(clientVersion: String) = apply { this.clientVersion = clientVersion }

        fun logger(logger: LogInterface) = apply { this.logger = logger }

        fun pref(preferences: PreferencesInterface) = apply { this.preferences = preferences }

        fun build(): NuguAndroidClient {
            return NuguAndroidClient(this)
        }
    }

    private val dialogUXStateAggregator: DialogUXStateAggregator

    private val playbackRouter: PlaybackRouter =
        com.skt.nugu.sdk.agent.playback.impl.PlaybackRouter()

    private val client: NuguClient = NuguClient.Builder(
        builder.authDelegate
    ).logger(builder.logger)
        .systemExceptionDirectiveDelegate(builder.systemExceptionDirectiveDelegate)
        .preferences(builder.preferences)
        .transportFactory(builder.transportFactory)
        .sdkVersion(BuildConfig.VERSION_NAME)
        .apply {
            builder.agentFactoryMap.forEach {
                addAgentFactory(it.key, it.value)
            }

            addAgentFactory(SessionAgent.NAMESPACE, object : AgentFactory<SessionAgent> {
                override fun create(container: SdkContainer): SessionAgent = SessionAgent(
                    container.getContextManager(),
                    container.getDirectiveSequencer(),
                    container.getSessionManager()
                )
            })

            addAgentFactory(DefaultASRAgent.NAMESPACE, object : AgentFactory<DefaultASRAgent> {
                override fun create(container: SdkContainer): DefaultASRAgent {
                    return with(container) {
                        DefaultASRAgent(
                            getInputManagerProcessor(),
                            getAudioSeamlessFocusManager(),
                            getMessageSender(),
                            getContextManager(),
                            getSessionManager(),
                            getDialogAttributeStorage(),
                            builder.defaultAudioProvider,
                            SpeexEncoder(),
                            builder.endPointDetector,
                            builder.defaultEpdTimeoutMillis,
                            DefaultFocusChannel.USER_DIALOG_CHANNEL_NAME,
                            DefaultFocusChannel.INTERNAL_DIALOG_CHANNEL_NAME,
                            DefaultFocusChannel.INTERACTION_CHANNEL_NAME,
                            getPlaySynchronizer(),
                            getInteractionControlManager()
                        ).apply {
                            getDirectiveSequencer().addDirectiveHandler(this)

                            CancelRecognizeDirectiveHandler(this).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)
                            }
                        }
                    }
                }
            })

            addAgentFactory(
                DefaultSpeakerAgent.NAMESPACE,
                object : AgentFactory<DefaultSpeakerAgent> {
                    override fun create(container: SdkContainer): DefaultSpeakerAgent =
                        with(container) {
                            DefaultSpeakerAgent(
                                getContextManager(),
                                getMessageSender()
                            ).apply {
                                getDirectiveSequencer().addDirectiveHandler(this)

                                builder.speakerFactory.let {
                                    Speaker.Type.values().forEach { type ->
                                        it.createSpeaker(type)?.let { speaker ->
                                            addSpeaker(speaker)
                                        }
                                    }
                                }
                            }
                        }
                })
            if (builder.enableAudioPlayer) {
                addAgentFactory(
                    DefaultAudioPlayerAgent.NAMESPACE,
                    object : AgentFactory<DefaultAudioPlayerAgent> {
                        override fun create(container: SdkContainer): DefaultAudioPlayerAgent =
                            with(container) {
                                DefaultAudioPlayerAgent(
                                    builder.playerFactory.createAudioPlayer(),
                                    getMessageSender(),
                                    getAudioSeamlessFocusManager(),
                                    getContextManager(),
                                    playbackRouter,
                                    getPlaySynchronizer(),
                                    getDirectiveSequencer(),
                                    getDirectiveGroupProcessor(),
                                    DefaultFocusChannel.CONTENT_CHANNEL_NAME,
                                    builder.enableDisplayLifeCycleManagement
                                ).apply {
                                    val audioPlayerMetadataDirectiveHandler =
                                        AudioPlayerMetadataDirectiveHandler()
                                            .apply {
                                                getDirectiveSequencer().addDirectiveHandler(this)
                                            }

                                    AudioPlayerLyricsDirectiveHandler(
                                        getContextManager(),
                                        getMessageSender(),
                                        this,
                                        this,
                                        getInterLayerDisplayPolicyManager()
                                    ).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }

                                    if (builder.enableDisplay) {
                                        AudioPlayerTemplateHandler(
                                            getPlaySynchronizer(),
                                            getSessionManager(),
                                            getInterLayerDisplayPolicyManager()
                                        ).apply {
                                            getDisplayPlayStackManager().addPlayContextProvider(this)
                                            setDisplay(this)
                                            getDirectiveSequencer().addDirectiveHandler(this)
                                            getDirectiveGroupProcessor().addDirectiveGroupPreprocessor(
                                                AudioPlayerDirectivePreProcessor()
                                            )
                                            audioPlayerMetadataDirectiveHandler.addListener(this)
                                        }
                                    }

                                    getAudioPlayStackManager().addPlayContextProvider(this)

                                    addASRResultListener(object: ASRAgentInterface.OnResultListener {
                                        override fun onNoneResult(dialogRequestId: String) {
                                            // fail beep
                                        }

                                        override fun onPartialResult(
                                            result: String,
                                            dialogRequestId: String
                                        ) {
                                            // no-op
                                        }

                                        override fun onCompleteResult(
                                            result: String,
                                            dialogRequestId: String
                                        ) {
                                            // success
                                        }

                                        override fun onError(
                                            type: ASRAgentInterface.ErrorType,
                                            dialogRequestId: String
                                        ) {

                                        }

                                        override fun onCancel(
                                            cause: ASRAgentInterface.CancelCause,
                                            dialogRequestId: String
                                        ) {
                                        }
                                    })
                                }
                            }
                    })
            }
            addAgentFactory(DefaultTTSAgent.NAMESPACE, object : AgentFactory<DefaultTTSAgent> {
                override fun create(container: SdkContainer): DefaultTTSAgent = with(container) {
                    DefaultTTSAgent(
                        builder.playerFactory.createSpeakPlayer(),
                        getMessageSender(),
                        getAudioSeamlessFocusManager(),
                        getContextManager(),
                        getPlaySynchronizer(),
                        getInterLayerDisplayPolicyManager(),
                        builder.cancelPolicyOnStopTTS,
                        DefaultFocusChannel.DIALOG_CHANNEL_NAME
                    ).apply {
                        getAudioPlayStackManager().addPlayContextProvider(this)
                        getDirectiveSequencer().addDirectiveHandler(this)

                        StopDirectiveHandler(this).apply {
                            getDirectiveSequencer().addDirectiveHandler(this)
                        }
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
                    DefaultMicrophoneAgent.NAMESPACE,
                    object : AgentFactory<DefaultMicrophoneAgent> {
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
                addAgentFactory(
                    DefaultScreenAgent.NAMESPACE,
                    object : AgentFactory<DefaultScreenAgent> {
                        override fun create(container: SdkContainer): DefaultScreenAgent =
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
                addAgentFactory(
                    DefaultDelegationAgent.NAMESPACE,
                    object : AgentFactory<DefaultDelegationAgent> {
                        override fun create(container: SdkContainer): DefaultDelegationAgent =
                            with(container) {
                                DefaultDelegationAgent(
                                    getContextManager(),
                                    getMessageSender(),
                                    it
                                ).apply {
                                    getDirectiveSequencer().addDirectiveHandler(this)
                                }
                            }
                    })
            }
            builder.extensionClient?.let {
                addAgentFactory(
                    DefaultExtensionAgent.NAMESPACE,
                    object : AgentFactory<DefaultExtensionAgent> {
                        override fun create(container: SdkContainer): DefaultExtensionAgent =
                            with(container) {
                                DefaultExtensionAgent(
                                    getContextManager(),
                                    getMessageSender()
                                ).apply {
                                    getDirectiveSequencer().addDirectiveHandler(this)
                                    setClient(it)
                                }
                            }
                    })
            }
            if (builder.enableLocation) {
                addAgentFactory(
                    DefaultLocationAgent.NAMESPACE,
                    object : AgentFactory<DefaultLocationAgent> {
                        override fun create(container: SdkContainer): DefaultLocationAgent =
                            with(container) {
                                DefaultLocationAgent(getContextManager())
                            }
                    })
            }
            addAgentFactory(DefaultTextAgent.NAMESPACE, object : AgentFactory<DefaultTextAgent> {
                override fun create(container: SdkContainer): DefaultTextAgent = with(container) {
                    DefaultTextAgent(
                        getMessageSender(),
                        getContextManager(),
                        getDialogAttributeStorage(),
                        builder.textSourceHandler
                    ).apply {
                        getDirectiveSequencer().addDirectiveHandler(this)
                    }
                }
            })

            if (builder.enableDisplay) {
                addAgentFactory(
                    DefaultDisplayAgent.NAMESPACE,
                    object : AgentFactory<DefaultDisplayAgent> {
                        override fun create(container: SdkContainer): DefaultDisplayAgent =
                            with(container) {
                                DefaultDisplayAgent(
                                    getPlaySynchronizer(),
                                    ElementSelectedEventHandler(
                                        getContextManager(),
                                        getMessageSender()
                                    ),
                                    getSessionManager(),
                                    getInterLayerDisplayPolicyManager(),
                                    getContextManager(),
                                    builder.enableDisplayLifeCycleManagement,
                                    builder.defaultDisplayDuration
                                ).apply {
                                    getDisplayPlayStackManager().addPlayContextProvider(this)

                                    RenderDirectiveHandler(this).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }

                                    ControlFocusDirectiveHandler(
                                        this,
                                        getContextManager(),
                                        getMessageSender(),
                                        namespaceAndName
                                    ).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }
                                    ControlScrollDirectiveHandler(
                                        this,
                                        getContextManager(),
                                        getMessageSender(),
                                        namespaceAndName
                                    ).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }

                                    CloseDirectiveHandler(
                                        this,
                                        getContextManager(),
                                        getMessageSender()
                                    ).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }

                                    UpdateDirectiveHandler(this).apply {
                                        getDirectiveSequencer().addDirectiveHandler(this)
                                    }
                                }
                            }
                    })
            }

            builder.bluetoothProvider?.let {
                addAgentFactory(
                    DefaultBluetoothAgent.NAMESPACE,
                    object : AgentFactory<DefaultBluetoothAgent> {
                        override fun create(container: SdkContainer): DefaultBluetoothAgent =
                            with(container) {
                                DefaultBluetoothAgent(
                                    getMessageSender(),
                                    getContextManager(),
                                    getAudioFocusManager(),
                                    DefaultFocusChannel.CONTENT_CHANNEL_NAME,
                                    it
                                ).apply {
                                    getDirectiveSequencer().addDirectiveHandler(this)
                                }
                            }
                    })
            }

            builder.soundProvider?.let {
                addAgentFactory(
                    DefaultSoundAgent.NAMESPACE,
                    object : AgentFactory<DefaultSoundAgent> {
                        override fun create(container: SdkContainer): DefaultSoundAgent =
                            with(container) {
                                DefaultSoundAgent(
                                    builder.playerFactory.createBeepPlayer(),
                                    getMessageSender(),
                                    getContextManager(),
                                    it,
                                    DefaultFocusChannel.SOUND_BEEP_CHANNEL_NAME,
                                    getAudioFocusManager(),
                                    builder.beepDirectiveDelegate
                                ).apply {
                                    getDirectiveSequencer().addDirectiveHandler(this)
                                }
                            }
                    })
            }

            builder.messageClient?.let {
                addAgentFactory(MessageAgent.NAMESPACE, object : AgentFactory<MessageAgent> {
                    override fun create(container: SdkContainer): MessageAgent = MessageAgent(
                        it,
                        TTSScenarioPlayer(
                            container.getPlaySynchronizer(),
                            container.getAudioSeamlessFocusManager(),
                            DefaultFocusChannel.DIALOG_CHANNEL_NAME,
                            builder.playerFactory.createSpeakPlayer(),
                            container.getAudioPlayStackManager()
                        ),
                        container.getContextManager(),
                        container.getContextManager(),
                        container.getMessageSender(),
                        container.getDirectiveSequencer(),
                        container.getInteractionControlManager()
                    )
                })
            }

            if(builder.enableChips) {
                addAgentFactory(ChipsAgent.NAMESPACE, object: AgentFactory<ChipsAgent> {
                    override fun create(container: SdkContainer): ChipsAgent = ChipsAgent(
                        container.getDirectiveSequencer(),
                        container.getContextManager(),
                        container.getContextManager()
                    )
                })
            }

            builder.clientVersion?.let {
                clientVersion(it)
            }
        }
        .build()

    override val audioPlayerAgent: DefaultAudioPlayerAgent?
        get() = try {
            client.getAgent(DefaultAudioPlayerAgent.NAMESPACE) as DefaultAudioPlayerAgent
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
            client.getAgent(DefaultASRAgent.NAMESPACE) as ASRAgentInterface
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
            client.getAgent(DefaultLocationAgent.NAMESPACE) as LocationAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val delegationAgent: DelegationAgentInterface?
        get() = try {
            client.getAgent(DefaultDelegationAgent.NAMESPACE) as DelegationAgentInterface
        } catch (th: Throwable) {
            null
        }
    override val systemAgent: SystemAgentInterface = client.systemAgent
    override val networkManager: ConnectionManagerInterface = client.networkManager
    override val bluetoothAgent: BluetoothAgentInterface?
        get() = try {
            client.getAgent(DefaultBluetoothAgent.NAMESPACE) as BluetoothAgentInterface
        } catch (th: Throwable) {
            null
        }

    val chipsAgent: ChipsAgentInterface? = try {
        client.getAgent(ChipsAgent.NAMESPACE) as ChipsAgentInterface
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
                client.getSdkContainer().getDirectiveGroupProcessor().addPostProcessedListener(this)
            }
        } else {
            null
        }

        dialogUXStateAggregator = DialogUXStateAggregator(
            builder.dialogUXStateTransitionDelay,
            client.getSdkContainer().getSessionManager(),
            client.getSdkContainer().getAudioSeamlessFocusManager(),
            displayAgent
        ).apply {
            client.getSdkContainer().getInteractionControlManager().addListener(this)
        }

        ttsAgent?.addListener(dialogUXStateAggregator)
        asrAgent?.addOnStateChangeListener(dialogUXStateAggregator)
        chipsAgent?.addListener(dialogUXStateAggregator)

        asrAgent?.let { asrAgent ->
            builder.asrBeepResourceProvider?.let { provider ->
                AsrBeepPlayer(
                    client.audioFocusManager,
                    DefaultFocusChannel.ASR_BEEP_CHANNEL_NAME,
                    asrAgent,
                    provider,
                    builder.playerFactory.createBeepPlayer()
                )
            }
        }

        getAgent(RoutineAgent.NAMESPACE)?.let {routineAgent ->
            textAgent?.let {
                (routineAgent as RoutineAgent).textAgent = textAgent
            }
        }

        initOsContextProvider()
    }

    private fun initOsContextProvider() {
        object : OsContextProvider() {
            override fun getType(): Type = Type.ANDROID
        }.apply {
            client.setStateProvider(namespaceAndName, this)
            provideState(client.getSdkContainer().getContextManager(), namespaceAndName, ContextType.FULL, 0)
            provideState(client.getSdkContainer().getContextManager(), namespaceAndName, ContextType.COMPACT, 0)
        }
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
        wakeupInfo: WakeupInfo?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?
    ) {
        Logger.d(
            TAG,
            "[startRecognition] wakeupInfo: $wakeupInfo"
        )

        asrAgent?.startRecognition(
            audioInputStream,
            audioFormat,
            wakeupInfo,
            param,
            callback
        )
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

    override fun requestTextInput(
        text: String,
        playServiceId: String?,
        token: String?,
        referrerDialogRequestId: String?,
        includeDialogAttribute: Boolean,
        listener: TextAgentInterface.RequestListener?
    ): String? = textAgent?.requestTextInput(text, playServiceId, token, referrerDialogRequestId, includeDialogAttribute, listener)

    override fun requestTTS(
        text: String,
        playServiceId: String?,
        listener: TTSAgentInterface.OnPlaybackListener?
    ): String? = ttsAgent?.requestTTS(text, playServiceId, listener)

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

    override fun addReceiveDirectivesListener(listener: DirectiveGroupProcessorInterface.Listener) {
        client.getSdkContainer().getDirectiveGroupProcessor().addPreProcessedListener(listener)
    }

    override fun removeReceiveDirectivesListener(listener: DirectiveGroupProcessorInterface.Listener) {
        client.getSdkContainer().getDirectiveGroupProcessor().removePreProcessedListener(listener)
    }

    override fun addOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        client.getSdkContainer().getMessageSender().addOnSendMessageListener(listener)
    }

    override fun removeOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        client.getSdkContainer().getMessageSender().removeOnSendMessageListener(listener)
    }

    override fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        client.getSdkContainer().getDirectiveSequencer().addOnDirectiveHandlingListener(listener)
    }

    override fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        client.getSdkContainer().getDirectiveSequencer().removeOnDirectiveHandlingListener(listener)
    }

    override fun getAgent(namespace: String): CapabilityAgent? = client.getAgent(namespace)
}