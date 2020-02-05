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
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.focus.FocusManager
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.network.NetworkManager
import com.skt.nugu.sdk.core.network.MessageRouter
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.client.dialog.DialogUXStateAggregator
import com.skt.nugu.sdk.core.attachment.AttachmentManager
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.agent.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.client.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.playback.impl.PlaybackRouter
import com.skt.nugu.sdk.core.utils.SdkVersion
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.context.ContextManager
import com.skt.nugu.sdk.core.context.PlayStackContextManager
import com.skt.nugu.sdk.core.inputprocessor.InputProcessorManager
import com.skt.nugu.sdk.core.playsynchronizer.PlaySynchronizer
import com.skt.nugu.sdk.client.display.DisplayAggregator
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.core.dialog.DialogSessionManager
import com.skt.nugu.sdk.core.directivesequencer.*
import com.skt.nugu.sdk.agent.asr.AbstractASRAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.tts.AbstractTTSAgent
import com.skt.nugu.sdk.client.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.playstack.PlayStackManager
import com.skt.nugu.sdk.core.utils.ImmediateBooleanFuture
import java.util.concurrent.Future

class NuguClient private constructor(
    builder: Builder
) {
    companion object {
        private const val TAG = "NuguClient"
    }

    data class Builder(
        internal val playerFactory: PlayerFactory,
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

        // Agent Factory
        internal var audioPlayerAgentFactory: AudioPlayerAgentFactory =
            DefaultAgentFactory.AUDIO_PLAYER
        internal var asrAgentFactory: ASRAgentFactory? = null
        internal var ttsAgentFactory: TTSAgentFactory = DefaultAgentFactory.TTS
        internal var textAgentFactory: TextAgentFactory = DefaultAgentFactory.TEXT
        internal var displayAgentFactory: DisplayAgentFactory? = DefaultAgentFactory.TEMPLATE

        internal val agentFactoryMap = HashMap<String, AgentFactory<*>>()

        fun defaultEpdTimeoutMillis(epdTimeoutMillis: Long) =
            apply { defaultEpdTimeoutMillis = epdTimeoutMillis }

        fun audioPlayerAgentFactory(factory: AudioPlayerAgentFactory) =
            apply { audioPlayerAgentFactory = factory }

        fun asrAgentFactory(factory: ASRAgentFactory) = apply { asrAgentFactory = factory }
        fun ttsAgentFactory(factory: TTSAgentFactory) = apply { ttsAgentFactory = factory }
        fun textAgentFactory(factory: TextAgentFactory) = apply { textAgentFactory = factory }

        fun displayAgentFactory(factory: DisplayAgentFactory) =
            apply { displayAgentFactory = factory }

        fun transportFactory(factory: TransportFactory) = apply { transportFactory = factory }

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) =
            apply { agentFactoryMap[namespace] = factory }

        fun logger(logger: LogInterface) = apply { this.logger = logger }
        fun sdkVersion(sdkVersion: String) = apply { this.sdkVersion = sdkVersion }
        fun build() = NuguClient(this)
    }

    private val inputProcessorManager = InputProcessorManager()
    private val directiveSequencer: DirectiveSequencer = DirectiveSequencer()

    private val playbackRouter: com.skt.nugu.sdk.agent.playback.PlaybackRouter =
        PlaybackRouter()

    // CA
    val audioPlayerAgent: AbstractAudioPlayerAgent
    val ttsAgent: AbstractTTSAgent
    //    private val alertsCapabilityAgent: AlertsCapabilityAgent
    val systemAgent: AbstractSystemAgent
    val displayAgent: DisplayAgentInterface?

    // CA internal Object (ref)

    val audioFocusManager: FocusManagerInterface = FocusManager(
        DefaultFocusChannel.getDefaultAudioChannels(),
        "Audio"
    )
    private val messageRouter: MessageRouter =
        MessageRouter(builder.transportFactory, builder.authDelegate)
    private val dialogUXStateAggregator =
        DialogUXStateAggregator()
    val asrAgent: AbstractASRAgent?
    val textAgent: TextAgentInterface
    val networkManager: NetworkManagerInterface

    private val displayAggregator: DisplayAggregator?

    var useServerSideEndPointDetector: Boolean = false

    private val contextStateProviderRegistry: ContextStateProviderRegistry

    private val audioPlayStackManager: PlayStackManager = PlayStackManager("Audio")
    private val displayPlayStackManager: PlayStackManager = PlayStackManager("Display")

    private val sdkContainer: SdkContainer

    init {
        with(builder) {
            Logger.logger = logger
            SdkVersion.currentVersion = sdkVersion
            val directiveGroupProcessor = DirectiveGroupProcessor(
                inputProcessorManager,
                directiveSequencer
            ).apply {
                addDirectiveGroupPreprocessor(TimeoutResponseHandler(inputProcessorManager))
            }
            val messageInterpreter =
                MessageInterpreter(directiveGroupProcessor, AttachmentManager())

            networkManager = NetworkManager.create(messageRouter).apply {
                addMessageObserver(messageInterpreter)
            }

            val contextManager = ContextManager()
            contextStateProviderRegistry = contextManager

            val dialogSessionManager = DialogSessionManager()

            val playSynchronizer = PlaySynchronizer()

            sdkContainer = object : SdkContainer {
                override fun getInputManagerProcessor(): InputProcessorManagerInterface =
                    inputProcessorManager

                override fun getAudioFocusManager(): FocusManagerInterface = audioFocusManager

                override fun getAudioPlayStackManager(): PlayStackManagerInterface =
                    audioPlayStackManager

                override fun getDisplayPlayStackManager(): PlayStackManagerInterface =
                    displayPlayStackManager

                override fun getMessageSender(): MessageSender = networkManager
                override fun getConnectionManager(): ConnectionManagerInterface = networkManager

                override fun getContextManager(): ContextManagerInterface = contextManager

                override fun getDialogSessionManager(): DialogSessionManagerInterface =
                    dialogSessionManager

                override fun getPlaySynchronizer(): PlaySynchronizerInterface = playSynchronizer
                override fun getDirectiveSequencer(): DirectiveSequencerInterface =
                    directiveSequencer

                override fun getDirectiveGroupProcessor(): DirectiveGroupProcessorInterface =
                    directiveGroupProcessor

                override fun getDialogUXStateAggregator(): DialogUXStateAggregatorInterface =
                    dialogUXStateAggregator

                override fun getAudioProvider(): AudioProvider = defaultAudioProvider

                override fun getAudioEncoder(): Encoder = audioEncoder

                override fun getEndPointDetector(): AudioEndPointDetector? = endPointDetector

                override fun getEpdTimeoutMillis(): Long = defaultEpdTimeoutMillis

                override fun getPlayerFactory(): PlayerFactory = playerFactory

                override fun getPlaybackRouter(): com.skt.nugu.sdk.agent.playback.PlaybackRouter =
                    playbackRouter
            }

            ttsAgent = ttsAgentFactory.create(sdkContainer)
            asrAgent = asrAgentFactory?.create(sdkContainer)
            textAgent = textAgentFactory.create(sdkContainer)
            audioPlayerAgent = audioPlayerAgentFactory.create(sdkContainer)
            displayAgent = displayAgentFactory?.create(sdkContainer)
            systemAgent = DefaultAgentFactory.SYSTEM.create(sdkContainer)

            agentFactoryMap.forEach {
                it.value.create(sdkContainer)?.let {agent ->
                    agentMap.put(it.key, agent)
                }
            }

            ttsAgent.addListener(dialogUXStateAggregator)
            asrAgent?.addOnStateChangeListener(dialogUXStateAggregator)
            dialogSessionManager.addListener(dialogUXStateAggregator)

            displayAggregator = if (displayAgent != null) {
                DisplayAggregator(
                    displayAgent,
                    audioPlayerAgent
                )
            } else {
                null
            }

            PlayStackContextManager(
                contextManager,
                audioPlayStackManager,
                displayPlayStackManager
            )
        }
    }

    private val agentMap = HashMap<String, CapabilityAgent>()

    fun connect() {
        networkManager.enable()
    }

    fun disconnect() {
        networkManager.disable()
    }

//    override fun addMessageListener(listener: MessageObserver) {
//        networkManager.addMessageObserver(listener)
//    }
//
//    override fun removeMessageListener(listener: MessageObserver) {
//        networkManager.removeMessageObserver(listener)
//    }

    fun addConnectionListener(listener: ConnectionStatusListener) {
        networkManager.addConnectionStatusListener(listener)
    }

    fun removeConnectionListener(listener: ConnectionStatusListener) {
        networkManager.removeConnectionStatusListener(listener)
    }

    fun getPlaybackRouter(): com.skt.nugu.sdk.agent.playback.PlaybackRouter =
        playbackRouter

    fun addAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent.addListener(listener)
    }

    fun removeAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener) {
        audioPlayerAgent.removeListener(listener)
    }

    fun addDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.addListener(listener)
    }

    fun removeDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener) {
        dialogUXStateAggregator.removeListener(listener)
    }

    // AIP
    fun addASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        asrAgent?.addOnStateChangeListener(listener)
    }

    fun removeASRListener(listener: ASRAgentInterface.OnStateChangeListener) {
        asrAgent?.removeOnStateChangeListener(listener)
    }

    fun startRecognition(
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

        return asrAgent?.startRecognition(
            audioInputStream,
            audioFormat,
            wakewordStartPosition,
            wakewordEndPosition,
            wakewordDetectPosition
        ) ?: ImmediateBooleanFuture(false)
    }

    fun stopRecognition() {
        Logger.d(TAG, "[stopRecognition]")
        asrAgent?.stopRecognition()
    }

    fun addASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent?.addOnResultListener(listener)
    }

    fun removeASRResultListener(listener: ASRAgentInterface.OnResultListener) {
        asrAgent?.removeOnResultListener(listener)
    }

    fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener?) {
        textAgent.requestTextInput(text, listener)
    }

    fun shutdown() {
        systemAgent.shutdown()
        audioPlayerAgent.shutdown()
        ttsAgent.stopTTS(true)
        networkManager.disable()
    }

    fun requestTTS(
        text: String,
        playServiceId: String,
        listener: TTSAgentInterface.OnPlaybackListener?
    ) {
        ttsAgent.requestTTS(text, playServiceId, listener)
    }

    fun localStopTTS() {
        ttsAgent.stopTTS(false)
    }

    fun cancelTTSAndOthers() {
        ttsAgent.stopTTS(true)
    }

    fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        displayAggregator?.setRenderer(renderer)
    }

    fun getDisplay(): DisplayAggregatorInterface? {
        return displayAggregator
    }

    fun setStateProvider(
        namespaceAndName: NamespaceAndName,
        stateProvider: ContextStateProvider?
    ) {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, stateProvider)
    }

    fun addSystemAgentListener(listener: SystemAgentInterface.Listener) {
        systemAgent.addListener(listener)
    }

    fun removeSystemAgentListener(listener: SystemAgentInterface.Listener) {
        systemAgent.removeListener(listener)
    }

    fun getAgent(namespace: String): CapabilityAgent? = agentMap[namespace]
}