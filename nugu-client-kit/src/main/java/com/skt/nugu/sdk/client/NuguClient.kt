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
import com.skt.nugu.sdk.core.focus.FocusManager
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.network.NetworkManager
import com.skt.nugu.sdk.core.network.MessageRouter
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.attachment.AttachmentManager
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.agent.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.agent.playback.impl.PlaybackRouter
import com.skt.nugu.sdk.core.utils.SdkVersion
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProviderRegistry
import com.skt.nugu.sdk.core.context.ContextManager
import com.skt.nugu.sdk.core.context.PlayStackContextManager
import com.skt.nugu.sdk.core.inputprocessor.InputProcessorManager
import com.skt.nugu.sdk.core.playsynchronizer.PlaySynchronizer
import com.skt.nugu.sdk.client.port.transport.grpc.GrpcTransportFactory
import com.skt.nugu.sdk.core.dialog.DialogSessionManager
import com.skt.nugu.sdk.core.directivesequencer.*
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.playstack.PlayStackManager

class NuguClient private constructor(
    builder: Builder
) {
    companion object {
        private const val TAG = "NuguClient"
    }

    data class Builder(
        internal val authDelegate: AuthDelegate
    ) {
        internal var transportFactory: TransportFactory = GrpcTransportFactory()

        // Log
        internal var logger: LogInterface? = null

        // sdk version for userAgent
        internal var sdkVersion: String = "1.0"

        internal val agentFactoryMap = HashMap<String, AgentFactory<*>>()

        fun transportFactory(factory: TransportFactory) = apply { transportFactory = factory }

        fun addAgentFactory(namespace: String, factory: AgentFactory<*>) =
            apply { agentFactoryMap[namespace] = factory }

        fun logger(logger: LogInterface) = apply { this.logger = logger }
        fun sdkVersion(sdkVersion: String) = apply { this.sdkVersion = sdkVersion }
        fun build() = NuguClient(this)
    }

    private val inputProcessorManager = InputProcessorManager()
    private val directiveSequencer: DirectiveSequencer = DirectiveSequencer()

    // CA
    val systemAgent: AbstractSystemAgent

    // CA internal Object (ref)

    val audioFocusManager: FocusManagerInterface = FocusManager(
        DefaultFocusChannel.getDefaultAudioChannels(),
        "Audio"
    )
    private val messageRouter: MessageRouter =
        MessageRouter(builder.transportFactory, builder.authDelegate)
    val networkManager: NetworkManagerInterface

    var useServerSideEndPointDetector: Boolean = false

    private val contextStateProviderRegistry: ContextStateProviderRegistry

    private val audioPlayStackManager: PlayStackManager = PlayStackManager("Audio")
    private val displayPlayStackManager: PlayStackManager = PlayStackManager("Display")

    private val sdkContainer: SdkContainer

    private val agentMap = HashMap<String, CapabilityAgent>()

    init {
        with(builder) {
            Logger.logger = logger
            SdkVersion.currentVersion = sdkVersion
            val directiveGroupProcessor = DirectiveGroupProcessor(
                directiveSequencer
            ).apply {
                addListener(inputProcessorManager)
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
            }

            systemAgent = DefaultAgentFactory.SYSTEM.create(sdkContainer)

            agentFactoryMap.forEach {
                agentMap[it.key] = it.value.create(sdkContainer)
            }

            PlayStackContextManager(
                contextManager,
                audioPlayStackManager,
                displayPlayStackManager
            )
        }
    }

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

    fun shutdown() {
        systemAgent.shutdown()
        networkManager.disable()
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
    fun getSdkContainer(): SdkContainer = sdkContainer
}