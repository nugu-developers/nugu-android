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
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.client.ClientHelperInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationClient
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.capability.light.Light
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.capability.microphone.Microphone
import com.skt.nugu.sdk.core.interfaces.capability.movement.MovementController
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerFactory
import com.skt.nugu.sdk.core.interfaces.capability.speaker.Speaker
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.core.interfaces.capability.system.BatteryStatusProvider
import com.skt.nugu.sdk.platform.android.log.AndroidLogger
import com.skt.nugu.sdk.platform.android.mediaplayer.AndroidMediaPlayer
import com.skt.nugu.sdk.platform.android.speaker.AndroidAudioSpeaker
import com.skt.nugu.sdk.external.jademarble.SpeexEncoder
import com.skt.nugu.sdk.external.silvertray.NuguOpusPlayer
import com.skt.nugu.sdk.client.NuguClient
import com.skt.nugu.sdk.client.agent.factory.ASRAgentFactory
import com.skt.nugu.sdk.client.agent.factory.AgentFactory
import com.skt.nugu.sdk.client.agent.factory.DefaultAgentFactory
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.client.NuguClientInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.platform.android.mediaplayer.IntegratedMediaPlayer
import com.skt.nugu.sdk.platform.android.battery.AndroidBatteryStatusProvider
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.text.TextAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.client.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.UriSourcePlayablePlayer
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
         * @param factory the transport factory for network
         */
        fun transportFactory(factory: TransportFactory) = apply { transportFactory = factory }

        /**
         * @param factory the audio focus interactor factory
         */
        fun audioFocusInteractorFactory(factory: AudioFocusInteractorFactory?) = apply {audioFocusInteractorFactory = factory}

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) = apply { agentFactoryMap[namespace] = factory }

        fun build(): NuguAndroidClient   {
            return NuguAndroidClient(this)
        }
    }

    private val client: NuguClient = NuguClient.Builder(
        builder.playerFactory,
        builder.speakerFactory,
        builder.authDelegate,
        builder.endPointDetector,
        builder.defaultAudioProvider,
        SpeexEncoder()
    ).logger(AndroidLogger())
        .delegationClient(builder.delegationClient)
        .defaultEpdTimeoutMillis(builder.defaultEpdTimeoutMillis)
        .defaultMicrophone(builder.defaultMicrophone)
        .extensionClient(builder.extensionClient)
        .movementController(builder.movementController)
        .batteryStatusProvider(builder.batteryStatusProvider)
        .light(builder.light)
        .transportFactory(builder.transportFactory)
        .sdkVersion(BuildConfig.VERSION_NAME)
        .apply {
            builder.agentFactoryMap.forEach {
                addAgentFactory(it.key, it.value)
            }
            asrAgentFactory(builder.asrAgentFactory)
        }
        .build()

    override val audioPlayerAgent: AudioPlayerAgentInterface? = client.audioPlayerAgent
    override val ttsAgent: TTSAgentInterface? = client.ttsAgent
    override val displayAgent: DisplayAgentInterface? = client.displayAgent
    override val extensionAgent: ExtensionAgentInterface? = client.extensionAgent
    override val asrAgent: ASRAgentInterface? = client.asrAgent
    override val textAgent: TextAgentInterface? = client.textAgent
    override val locationAgent: LocationAgentInterface? = client.locationAgent
    override val delegationAgent: DelegationAgentInterface? = client.delegationAgent
    override val systemAgent: SystemAgentInterface = client.systemAgent
    override val networkManager: NetworkManagerInterface = client.networkManager

    private val audioFocusInteractor: AudioFocusInteractor?

    init {
        audioFocusInteractor = builder.audioFocusInteractorFactory?.create(client.audioFocusManager)
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

    override fun getSpeakerManager(): SpeakerManagerInterface = client.getSpeakerManager()

    override fun addSpeakerListener(listener: SpeakerManagerObserver) {
        client.addSpeakerListener(listener)
    }

    override fun removeSpeakerListener(listener: SpeakerManagerObserver) {
        client.removeSpeakerListener(listener)
    }

    override fun getPlaybackRouter(): PlaybackRouter = client.getPlaybackRouter()

    override fun addAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        client.addAudioPlayerListener(listener)
    }

    override fun removeAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        client.removeAudioPlayerListener(listener)
    }

    override fun addDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        client.addDialogUXStateListener(listener)
    }

    override fun removeDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        client.removeDialogUXStateListener(listener)
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
        client.requestTextInput(text, listener)
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

    override fun getDisplay(): DisplayAggregatorInterface? = client.getDisplay()

    override fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        client.setDisplayRenderer(renderer)
    }

    override fun shutdown() {
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