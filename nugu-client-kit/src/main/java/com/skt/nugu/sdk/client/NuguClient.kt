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
import com.skt.nugu.sdk.core.capabilityagents.impl.DefaultDisplayAgent
import com.skt.nugu.sdk.core.capabilityagents.display.DisplayAudioPlayerAgent
import com.skt.nugu.sdk.core.capabilityagents.impl.*
import com.skt.nugu.sdk.core.capabilityagents.playbackcontroller.PlaybackRouter
import com.skt.nugu.sdk.core.utils.SdkVersion
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.context.ContextManager
import com.skt.nugu.sdk.core.context.PlayStackContextManager
import com.skt.nugu.sdk.core.context.PlayStackProvider
import com.skt.nugu.sdk.core.directivesequencer.DirectiveSequencer
import com.skt.nugu.sdk.core.directivesequencer.AudioPlayerDirectivePreProcessor
import com.skt.nugu.sdk.core.directivesequencer.MessageInterpreter
import com.skt.nugu.sdk.core.inputprocessor.InputProcessorManager
import com.skt.nugu.sdk.core.playsynchronizer.PlaySynchronizer
import com.skt.nugu.sdk.client.client.DisplayAggregator
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.core.directivesequencer.TimeoutResponseHandler
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentFactory
import com.skt.nugu.sdk.core.interfaces.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.location.LocationAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.core.interfaces.capability.sound.SoundAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.sound.SoundProvider
import com.skt.nugu.sdk.core.interfaces.capability.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.AbstractSystemAgent
import com.skt.nugu.sdk.core.interfaces.capability.text.TextAgentFactory
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
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
        internal var soundProvider: SoundProvider? = null
        internal var light: Light? = null

        // Agent Factory
        internal var audioPlayerAgentFactory: AudioPlayerAgentFactory =
            DefaultAudioPlayerAgent.FACTORY
        internal var asrAgentFactory: ASRAgentFactory = DefaultASRAgent.FACTORY
        internal var ttsAgentFactory: TTSAgentFactory = DefaultTTSAgent.FACTORY
        internal var textAgentFactory: TextAgentFactory = DefaultTextAgent.FACTORY
        internal var extensionAgentFactory: ExtensionAgentFactory = DefaultExtensionAgent.FACTORY
        internal var displayAgentFactory: DisplayAgentFactory? = DefaultDisplayAgent.FACTORY
        internal var locationAgentFactory: LocationAgentFactory? = DefaultLocationAgent.FACTORY
        internal var soundAgentFactory: SoundAgentFactory = DefaultSoundAgent.FACTORY

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

        fun soundProvider(provider: SoundProvider?) = apply { this.soundProvider = provider }

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
        fun soundAgentFactory(factory: SoundAgentFactory) = apply { soundAgentFactory = factory }
        fun logger(logger: LogInterface) = apply { this.logger = logger }
        fun sdkVersion(sdkVersion: String) = apply { this.sdkVersion = sdkVersion }
        fun build() = NuguClient(this)
    }

    private val inputProcessorManager = InputProcessorManager()
    private val directiveSequencer: DirectiveSequencer = DirectiveSequencer()

    private val playbackRouter: com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter

    // CA
    private val speakerManager: AbstractSpeakerAgent
    private val micManager: AbstractMicrophoneAgent
    override val audioPlayerAgent: AbstractAudioPlayerAgent
    override val ttsAgent: AbstractTTSAgent
    //    private val alertsCapabilityAgent: AlertsCapabilityAgent
    private val systemCapabilityAgent: AbstractSystemAgent
    override var displayAgent: DisplayAgentInterface? = null
    override var locationAgent: LocationAgentInterface? = null

    // CA internal Object (ref)

    val audioFocusManager: FocusManagerInterface
    val visualFocusManager: FocusManagerInterface?
    private val messageRouter: MessageRouter =
        MessageRouter(builder.transportFactory, builder.authDelegate)
    private val dialogUXStateAggregator = DialogUXStateAggregator()
    override val asrAgent: AbstractASRAgent
    override val textAgent: TextAgentInterface
    override val extensionAgent: AbstractExtensionAgent?

    override val networkManager: NetworkManagerInterface

    private val displayAggregator: DisplayAggregator?

    var useServerSideEndPointDetector: Boolean = false

    private val contextStateProviderRegistry: ContextStateProviderRegistry

    init {
        with(builder) {
            Logger.logger = logger
            SdkVersion.currentVersion = sdkVersion
            val messageInterpreter =
                MessageInterpreter(directiveSequencer, AttachmentManager()).apply {
                    addDirectiveGroupPreprocessor(TimeoutResponseHandler(inputProcessorManager))
                    if (displayAgentFactory != null) {
                        addDirectiveGroupPreprocessor(AudioPlayerDirectivePreProcessor())
                    }
                }

            networkManager = NetworkManager.create(messageRouter).apply {
                addMessageObserver(messageInterpreter)
                addMessageObserver(inputProcessorManager)
            }

            val contextManager = ContextManager()
            contextStateProviderRegistry = contextManager

            speakerManager =
                DefaultSpeakerAgent.FACTORY.create(contextManager, networkManager).apply {
                    addSpeaker(speakerFactory.createNuguSpeaker())
                    addSpeaker(speakerFactory.createAlarmSpeaker())
                    speakerFactory.createCallSpeaker()?.let {
                        addSpeaker(it)
                    }
                    speakerFactory.createExternalSpeaker()?.let {
                        addSpeaker(it)
                    }
                }

            micManager =
                DefaultMicrophoneAgent.FACTORY.create(
                    networkManager,
                    contextManager,
                    defaultMicrophone
                )

            audioFocusManager =
                FocusManager(
                    DefaultFocusChannel.getDefaultAudioChannels(),
                    "Audio"
                )

            playbackRouter = PlaybackRouter()

            val playSynchronizer = PlaySynchronizer()

            // CA
            ttsAgent = ttsAgentFactory.create(
                playerFactory.createSpeakPlayer(),
                networkManager,
                audioFocusManager,
                contextManager,
                playSynchronizer,
                inputProcessorManager,
                DefaultFocusChannel.DIALOG_CHANNEL_NAME
            )

            dialogUXStateAggregator.addListener(ttsAgent)
            ttsAgent.addListener(dialogUXStateAggregator)

            systemCapabilityAgent = DefaultSystemCapabilityAgent.FACTORY.create(
                networkManager,
                networkManager,
                contextManager,
                authDelegate,
                batteryStatusProvider
            )

            val audioEncoder = audioEncoder// SpeexEncoder()

            val dialogSessionManager = DialogSessionManager()

            asrAgent = asrAgentFactory.create(
                inputProcessorManager,
                audioFocusManager,
                networkManager,
                contextManager,
                dialogSessionManager,
                defaultAudioProvider,
                audioEncoder,
                endPointDetector,
                defaultEpdTimeoutMillis,
                DefaultFocusChannel.DIALOG_CHANNEL_NAME
            )
            dialogUXStateAggregator.addListener(asrAgent)

            asrAgent.addOnStateChangeListener(dialogUXStateAggregator)

            textAgent =
                textAgentFactory.create(networkManager, contextManager, inputProcessorManager)

            extensionAgent = extensionClient?.let {
                extensionAgentFactory.create(contextManager, networkManager).apply {
                    setClient(it)
                }
            }

            dialogSessionManager.addListener(dialogUXStateAggregator)
            dialogSessionManager.addListener(asrAgent)
            dialogSessionManager.addListener(textAgent)

            val tempDisplayAgentFactory = displayAgentFactory
            if (tempDisplayAgentFactory != null) {
                visualFocusManager =
                    FocusManager(
                        DefaultFocusChannel.getDefaultVisualChannels(),
                        "Visual"
                    )

                val displayTemplateAgent = tempDisplayAgentFactory.create(
                    visualFocusManager,
                    contextManager,
                    networkManager,
                    playSynchronizer,
                    DefaultFocusChannel.DIALOG_CHANNEL_NAME
                )

                val displayAudioPlayerAgent = DisplayAudioPlayerAgent(
                    visualFocusManager,
                    contextManager,
                    networkManager,
                    playSynchronizer,
                    DefaultFocusChannel.CONTENT_CHANNEL_NAME
                )

                audioPlayerAgent = audioPlayerAgentFactory.create(
                    playerFactory.createAudioPlayer(),
                    networkManager,
                    audioFocusManager,
                    contextManager,
                    playbackRouter,
                    playSynchronizer,
                    DefaultFocusChannel.CONTENT_CHANNEL_NAME,
                    displayAudioPlayerAgent
                )
                audioPlayerAgent.addListener(displayAudioPlayerAgent)

                directiveSequencer.addDirectiveHandler(displayAudioPlayerAgent)
                directiveSequencer.addDirectiveHandler(displayTemplateAgent)

                displayAgent = displayTemplateAgent
                displayAggregator = DisplayAggregator(
                    displayTemplateAgent,
                    displayAudioPlayerAgent
                )
            } else {
                displayAggregator = null
                displayAgent = null
                visualFocusManager = null

                audioPlayerAgent = audioPlayerAgentFactory.create(
                    playerFactory.createAudioPlayer(),
                    networkManager,
                    audioFocusManager,
                    contextManager,
                    playbackRouter,
                    playSynchronizer,
                    DefaultFocusChannel.CONTENT_CHANNEL_NAME,
                    null
                )
            }

            locationAgent = locationAgentFactory?.create()?.apply {
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
                light?.let {
                    addDirectiveHandler(
                        DefaultLightAgent.FACTORY.create(
                            networkManager,
                            contextManager,
                            it
                        )
                    )
                }
                addDirectiveHandler(textAgent)
                addDirectiveHandler(asrAgent)
                addDirectiveHandler(systemCapabilityAgent)
                delegationClient?.let {
                    addDirectiveHandler(DefaultDelegationAgent.FACTORY.create(it).apply {
                        contextManager.setStateProvider(namespaceAndName, this)
                        // update delegate initial state
                        contextManager.setState(
                            namespaceAndName,
                            "",
                            StateRefreshPolicy.SOMETHIMES,
                            0
                        )
                    })
                }
                extensionAgent?.let {
                    addDirectiveHandler(it)
                }
                movementController?.let {
                    addDirectiveHandler(
                        DefaultMovementAgent.FACTORY.create(
                            contextManager,
                            networkManager,
                            it
                        )
                    )
                }
                soundProvider?.let {
                    addDirectiveHandler(
                        soundAgentFactory.create(
                            playerFactory.createBeepPlayer(),
                            contextManager,
                            networkManager,
                            it)
                    )
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

    override fun getPlaybackRouter(): com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter = playbackRouter

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
        wakewordEndPosition: Long?
    ): Future<Boolean> {
        Logger.d(
            TAG,
            "[startRecognition] wakewordStartPosition: $wakewordStartPosition , wakewordEndPosition:$wakewordEndPosition"
        )
        return asrAgent.startRecognition(
            audioInputStream,
            audioFormat,
            wakewordStartPosition,
            wakewordEndPosition
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
        systemCapabilityAgent.onUserDisconnect()
        systemCapabilityAgent.shutdown()
        audioPlayerAgent.shutdown()
        ttsAgent.stopTTS()
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
        ttsAgent.stopTTS()
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
}