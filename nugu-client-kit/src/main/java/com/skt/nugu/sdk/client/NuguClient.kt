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
package com.skt.nugu.sdk.client

import com.skt.nugu.sdk.client.agent.factory.*
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationClient
import com.skt.nugu.sdk.core.interfaces.capability.microphone.Microphone
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.focus.FocusManager
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.network.NetworkManager
import com.skt.nugu.sdk.core.network.MessageRouter
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.capability.text.TextAgentInterface
import com.skt.nugu.sdk.core.dialog.DialogUXStateAggregator
import com.skt.nugu.sdk.core.attachment.AttachmentManager
import com.skt.nugu.sdk.core.interfaces.client.ClientHelperInterface
import com.skt.nugu.sdk.core.interfaces.client.NuguClientInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.light.Light
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.core.interfaces.capability.movement.MovementController
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.core.interfaces.capability.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.core.interfaces.capability.system.BatteryStatusProvider
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.capabilityagents.asr.*
import com.skt.nugu.sdk.core.capabilityagents.playbackcontroller.PlaybackRouter
import com.skt.nugu.sdk.core.utils.SdkVersion
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.context.ContextManager
import com.skt.nugu.sdk.core.context.PlayStackContextManager
import com.skt.nugu.sdk.core.inputprocessor.InputProcessorManager
import com.skt.nugu.sdk.core.playsynchronizer.PlaySynchronizer
import com.skt.nugu.sdk.client.client.DisplayAggregator
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.core.directivesequencer.*
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.core.interfaces.capability.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.core.interfaces.capability.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.AbstractSystemAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.playstack.AudioPlayStackProvider
import com.skt.nugu.sdk.core.playstack.DisplayPlayStackProvider
import java.util.concurrent.Future

class NuguClient private constructor(
    builder: Builder
) : ClientHelperInterface
    , NuguClientInterface {

    companion object {
        private const val TAG = "NuguClient"
    }

    data class Builder(
        internal val playerFactory: PlayerFactory,
        internal val speakerFactory: SpeakerFactory,
        internal val authDelegate: AuthDelegate,
        internal val endPointDetector: AudioEndPointDetector?,
        internal val defaultAudioProvider: AudioProvider,
        internal val audioEncoder: Encoder
    ) {
        internal var defaultEpdTimeoutMillis: Long = 10000L

        internal var transportFactory: TransportFactory = GrpcTransportFactory()

        // Log
        internal var logger: LogInterface? = null

        // sdk version for userAgent
        internal var sdkVersion: String = "1.0"

        // Components for agent
        internal var defaultMicrophone: Microphone? = null
        internal var delegationClient: DelegationClient? = null
        internal var extensionClient: ExtensionAgentInterface.Client? = null
        internal var movementController: MovementController? = null
        internal var batteryStatusProvider: BatteryStatusProvider? = null
        internal var light: Light? = null

        // Agent Factory
        internal var audioPlayerAgentFactory: AudioPlayerAgentFactory =
            DefaultAgentFactory.AUDIO_PLAYER
        internal var asrAgentFactory: ASRAgentFactory = DefaultAgentFactory.ASR
        internal var ttsAgentFactory: TTSAgentFactory = DefaultAgentFactory.TTS
        internal var textAgentFactory: TextAgentFactory = DefaultAgentFactory.TEXT
        internal var extensionAgentFactory: ExtensionAgentFactory = DefaultAgentFactory.EXTENSION
        internal var displayAgentFactory: DisplayAgentFactory? = DefaultAgentFactory.TEMPLATE
        internal var locationAgentFactory: LocationAgentFactory? = DefaultAgentFactory.LOCATION
        internal var delegationAgentFactory: DelegationAgentFactory? =
            DefaultAgentFactory.DELEGATION

        fun defaultEpdTimeoutMillis(epdTimeoutMillis: Long) =
            apply { defaultEpdTimeoutMillis = epdTimeoutMillis }

        fun defaultMicrophone(microphone: Microphone?) =
            apply { defaultMicrophone = microphone }

        fun delegationClient(client: DelegationClient?) =
            apply { delegationClient = client }

        fun extensionClient(client: ExtensionAgentInterface.Client?) =
            apply { extensionClient = client }

        fun movementController(controller: MovementController?) =
            apply { movementController = controller }

        fun batteryStatusProvider(batteryStatusProvider: BatteryStatusProvider?) =
            apply { this.batteryStatusProvider = batteryStatusProvider }

        fun light(light: Light?) = apply { this.light = light }

        fun audioPlayerAgentFactory(factory: AudioPlayerAgentFactory) =
            apply { audioPlayerAgentFactory = factory }

        fun asrAgentFactory(factory: ASRAgentFactory) = apply { asrAgentFactory = factory }
        fun ttsAgentFactory(factory: TTSAgentFactory) = apply { ttsAgentFactory = factory }
        fun textAgentFactory(factory: TextAgentFactory) = apply { textAgentFactory = factory }
        fun extensionAgentFactory(factory: ExtensionAgentFactory) =
            apply { extensionAgentFactory = factory }

        fun displayAgentFactory(factory: DisplayAgentFactory) =
            apply { displayAgentFactory = factory }

        fun locationAgentFactory(factory: LocationAgentFactory) =
            apply { locationAgentFactory = factory }

        fun transportFactory(factory: TransportFactory) = apply { transportFactory = factory }
        fun delegationAgentFactory(factory: DelegationAgentFactory?) =
            apply { delegationAgentFactory = factory }

        fun logger(logger: LogInterface) = apply { this.logger = logger }
        fun sdkVersion(sdkVersion: String) = apply { this.sdkVersion = sdkVersion }
        fun build() = NuguClient(this)
    }

    private val inputProcessorManager = InputProcessorManager()
    private val directiveSequencer: DirectiveSequencer = DirectiveSequencer()

    private val playbackRouter: com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter =
        PlaybackRouter()

    // CA
    private val speakerManager: AbstractSpeakerAgent
    private val micManager: AbstractMicrophoneAgent
    override val audioPlayerAgent: AbstractAudioPlayerAgent
    override val ttsAgent: AbstractTTSAgent
    //    private val alertsCapabilityAgent: AlertsCapabilityAgent
    override val systemAgent: AbstractSystemAgent
    override var displayAgent: DisplayAgentInterface? = null
    override var locationAgent: LocationAgentInterface? = null

    // CA internal Object (ref)

    val audioFocusManager: FocusManagerInterface = FocusManager(
        DefaultFocusChannel.getDefaultAudioChannels(),
        "Audio"
    )
    val visualFocusManager: FocusManagerInterface?
    private val messageRouter: MessageRouter =
        MessageRouter(builder.transportFactory, builder.authDelegate)
    private val dialogUXStateAggregator = DialogUXStateAggregator()
    override val asrAgent: AbstractASRAgent
    override val textAgent: TextAgentInterface
    override val extensionAgent: AbstractExtensionAgent?
    override val delegationAgent: AbstractDelegationAgent?
    override val networkManager: NetworkManagerInterface

    private val displayAggregator: DisplayAggregator?

    var useServerSideEndPointDetector: Boolean = false

    private val contextStateProviderRegistry: ContextStateProviderRegistry

    private val sdkContainer: SdkContainer

    init {
        with(builder) {
            Logger.logger = logger
            SdkVersion.currentVersion = sdkVersion
            val messageInterpreter =
                MessageInterpreter(
                    DirectiveGroupProcessor(
                        inputProcessorManager,
                        directiveSequencer
                    ).apply {
                        addDirectiveGroupPreprocessor(TimeoutResponseHandler(inputProcessorManager))
                        if (displayAgentFactory != null) {
                            addDirectiveGroupPreprocessor(AudioPlayerDirectivePreProcessor())
                        }
                    }, AttachmentManager()
                )

            networkManager = NetworkManager.create(messageRouter).apply {
                addMessageObserver(messageInterpreter)
            }

            val contextManager = ContextManager()
            contextStateProviderRegistry = contextManager

            val dialogSessionManager = DialogSessionManager()

            val tempDisplayAgentFactory = displayAgentFactory
            if (tempDisplayAgentFactory != null) {
                visualFocusManager =
                    FocusManager(
                        DefaultFocusChannel.getDefaultVisualChannels(),
                        "Visual"
                    )
            } else {
                visualFocusManager = null
            }

            val playSynchronizer = PlaySynchronizer()

            sdkContainer = object : SdkContainer {
                override fun getInputManagerProcessor(): InputProcessorManagerInterface =
                    inputProcessorManager

                override fun getAudioFocusManager(): FocusManagerInterface = audioFocusManager

                override fun getVisualFocusManager(): FocusManagerInterface? = visualFocusManager

                override fun getMessageSender(): MessageSender = networkManager
                override fun getConnectionManager(): ConnectionManagerInterface = networkManager

                override fun getContextManager(): ContextManagerInterface = contextManager

                override fun getDialogSessionManager(): DialogSessionManagerInterface =
                    dialogSessionManager

                override fun getPlaySynchronizer(): PlaySynchronizerInterface = playSynchronizer
                override fun getDirectiveSequencer(): DirectiveSequencerInterface = directiveSequencer

                override fun getAudioProvider(): AudioProvider = defaultAudioProvider

                override fun getAudioEncoder(): Encoder = audioEncoder

                override fun getEndPointDetector(): AudioEndPointDetector? = endPointDetector

                override fun getEpdTimeoutMillis(): Long = defaultEpdTimeoutMillis

                override fun getDelegationClient(): DelegationClient? = delegationClient

                override fun getLight(): Light? = light

                override fun getMicrophone(): Microphone? = defaultMicrophone

                override fun getMovementController(): MovementController? = movementController

                override fun getBatteryStatusProvider(): BatteryStatusProvider? = batteryStatusProvider
                override fun getPlayerFactory(): PlayerFactory = playerFactory
                override fun getPlaybackRouter(): com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter = playbackRouter
            }

            speakerManager =
                DefaultAgentFactory.SPEAKER.create(sdkContainer).apply {
                    addSpeaker(speakerFactory.createNuguSpeaker())
                    addSpeaker(speakerFactory.createAlarmSpeaker())
                    speakerFactory.createCallSpeaker()?.let {
                        addSpeaker(it)
                    }
                    speakerFactory.createExternalSpeaker()?.let {
                        addSpeaker(it)
                    }
                }

            micManager = DefaultAgentFactory.MICROPHONE.create(sdkContainer)

            // CA
            ttsAgent = ttsAgentFactory.create(sdkContainer)

            dialogUXStateAggregator.addListener(ttsAgent)
            ttsAgent.addListener(dialogUXStateAggregator)

            systemAgent = DefaultAgentFactory.SYSTEM.create(sdkContainer).apply {
                addListener(messageRouter)
            }

            val audioEncoder = audioEncoder// SpeexEncoder()

            asrAgent = asrAgentFactory.create(sdkContainer)
            dialogUXStateAggregator.addListener(asrAgent)

            asrAgent.addOnStateChangeListener(dialogUXStateAggregator)

            textAgent = textAgentFactory.create(sdkContainer)

            extensionAgent = extensionClient?.let {
                extensionAgentFactory.create(sdkContainer).apply {
                    setClient(it)
                }
            }

            delegationAgent = delegationClient?.let {
                delegationAgentFactory?.create(sdkContainer)?.apply {
                    contextManager.setStateProvider(namespaceAndName, this)
                    // update delegate initial state
                    contextManager.setState(
                        namespaceAndName,
                        "",
                        StateRefreshPolicy.SOMETIMES,
                        0
                    )
                }
            }

            dialogSessionManager.addListener(dialogUXStateAggregator)
            dialogSessionManager.addListener(asrAgent)
            dialogSessionManager.addListener(textAgent)

            val displayTemplateAgent = tempDisplayAgentFactory?.create(sdkContainer)

            if (displayTemplateAgent != null && visualFocusManager != null) {
                audioPlayerAgent = audioPlayerAgentFactory.create(sdkContainer)

                directiveSequencer.addDirectiveHandler(displayTemplateAgent)

                displayAgent = displayTemplateAgent
                displayAggregator = DisplayAggregator(
                    displayTemplateAgent,
                    audioPlayerAgent
                )
            } else {
                displayAggregator = null
                displayAgent = null

                audioPlayerAgent = audioPlayerAgentFactory.create(sdkContainer)
            }

            locationAgent = locationAgentFactory?.create(sdkContainer)?.apply {
                contextManager.setStateProvider(namespaceAndName, this)
            }

            PlayStackContextManager(
                contextManager,
                AudioPlayStackProvider(audioFocusManager, audioPlayerAgent, ttsAgent),
                visualFocusManager?.let {
                    DisplayPlayStackProvider(it)
                }
            )

            with(directiveSequencer) {
                addDirectiveHandler(speakerManager)
                addDirectiveHandler(audioPlayerAgent)
                addDirectiveHandler(ttsAgent)
                DefaultAgentFactory.LIGHT.create(sdkContainer)?.let {
                    addDirectiveHandler(it)
                }

                addDirectiveHandler(textAgent)
                addDirectiveHandler(asrAgent)
                addDirectiveHandler(systemAgent)

                extensionAgent?.let {
                    addDirectiveHandler(it)
                }

                delegationAgent?.let {
                    addDirectiveHandler(it)
                }

                DefaultAgentFactory.MOVEMENT.create(sdkContainer)?.let {
                    addDirectiveHandler(it)
                }
            }
        }
    }

    override fun connect() {
        networkManager.enable()
    }

    override fun disconnect() {
        networkManager.disable()
    }

//    override fun addMessageListener(listener: MessageObserver) {
//        networkManager.addMessageObserver(listener)
//    }
//
//    override fun removeMessageListener(listener: MessageObserver) {
//        networkManager.removeMessageObserver(listener)
//    }

    override fun addConnectionListener(listener: ConnectionStatusListener) {
        networkManager.addConnectionStatusListener(listener)
    }

    override fun removeConnectionListener(listener: ConnectionStatusListener) {
        networkManager.removeConnectionStatusListener(listener)
    }

    override fun getSpeakerManager(): SpeakerManagerInterface {
        return speakerManager
    }

    override fun addSpeakerListener(listener: SpeakerManagerObserver) {
        return speakerManager.addSpeakerManagerObserver(listener)
    }

    override fun removeSpeakerListener(listener: SpeakerManagerObserver) {
        return speakerManager.removeSpeakerManagerObserver(listener)
    }

    override fun getPlaybackRouter(): com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter =
        playbackRouter

    override fun addAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent.addListener(listener)
    }

    override fun removeAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent.removeListener(listener)
    }

    override fun addDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.addListener(listener)
    }

    override fun removeDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.removeListener(listener)
    }

    // AIP
    override fun addASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        asrAgent.addOnStateChangeListener(listener)
    }

    override fun removeASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        asrAgent.removeOnStateChangeListener(listener)
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakewordStartPosition: Long?,
        wakewordEndPosition: Long?,
        wakewordDetectPosition: Long?
    ): Future<Boolean> {
        Logger.d(
            TAG,
            "[startRecognition] wakewordStartPosition: $wakewordStartPosition , wakewordEndPosition:$wakewordEndPosition, wakewordDetectPosition:$wakewordDetectPosition"
        )

        return asrAgent.startRecognition(
            audioInputStream,
            audioFormat,
            wakewordStartPosition,
            wakewordEndPosition,
            wakewordDetectPosition
        )
    }

    override fun stopRecognition() {
        Logger.d(TAG, "[stopRecognition]")
        asrAgent.stopRecognition()
    }

    override fun addASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent.addOnResultListener(listener)
    }

    override fun removeASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent.removeOnResultListener(listener)
    }

    override fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener?) {
        textAgent.requestTextInput(text, listener)
    }

    override fun shutdown() {
        systemAgent.shutdown()
        audioPlayerAgent.shutdown()
        ttsAgent.stopTTS(true)
        networkManager.disable()
    }

    override fun requestTTS(
        text: String,
        playServiceId: String,
        listener: TTSAgentInterface.OnPlaybackListener?
    ) {
        ttsAgent.requestTTS(text, playServiceId, listener)
    }

    override fun localStopTTS() {
        ttsAgent.stopTTS(false)
    }

    override fun cancelTTSAndOthers() {
        ttsAgent.stopTTS(true)
    }

    override fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        displayAggregator?.setRenderer(renderer)
    }

    override fun getDisplay(): DisplayAggregatorInterface? {
        return displayAggregator
    }

    override fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, stateProvider)
    }

    override fun addSystemAgentListener(listener: SystemAgentInterface.Listener) {
        systemAgent.addListener(listener)
    }

    override fun removeSystemAgentListener(listener: SystemAgentInterface.Listener) {
        systemAgent.removeListener(listener)
    }
}