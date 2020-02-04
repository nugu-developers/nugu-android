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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.bluetooth.*
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.Listener
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.AVRCPCommand
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.State
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.BluetoothEvent
import com.skt.nugu.sdk.agent.bluetooth.BluetoothEventBus
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class DefaultBluetoothAgent(
    messageSender: MessageSender,
    contextManager: ContextManagerInterface
) : AbstractBluetoothAgent(
    messageSender,
    contextManager
) {
    /**
     * This class handles providing configuration for the bluetooth Capability agent
     */
    companion object {
        private const val TAG = "DefaultBluetoothAgent"

        /** directives */
        const val NAME_START_DISCOVERABLE_MODE = "StartDiscoverableMode"
        const val NAME_FINISH_DISCOVERABLE_MODE = "FinishDiscoverableMode"
        const val NAME_PLAY = "Play"
        const val NAME_STOP = "Stop"
        const val NAME_PAUSE = "Pause"
        const val NAME_NEXT = "Next"
        const val NAME_PREVIOUS = "Previous"

        /** events */
        const val EVENT_NAME_START_DISCOVERABLE_MODE_SUCCEEDED = "StartDiscoverableModeSucceeded"
        const val EVENT_NAME_START_DISCOVERABLE_MODE_FAILED = "StartDiscoverableModeFailed"
        const val EVENT_NAME_FINISH_DISCOVERABLE_MODE_SUCCEEDED = "FinishDiscoverableModeSucceeded"
        const val EVENT_NAME_FINISH_DISCOVERABLE_MODE_FAILED = "FinishDiscoverableModeFailed"

        const val EVENT_NAME_CONNECT_SUCCEEDED = "ConnectSucceeded"
        const val EVENT_NAME_CONNECT_FAILED = "ConnectFailed"

        const val EVENT_NAME_DISCONNECT_SUCCEEDED = "DisconnectSucceeded"
        const val EVENT_NAME_DISCONNECT_FAILED = "DisconnectFailed"

        const val EVENT_NAME_MEDIACONTROL_PLAY_SUCCEEDED = "MediaControlPlaySucceeded"
        const val EVENT_NAME_MEDIACONTROL_PLAY_FAILED = "MediaControlPlayFailed"

        const val EVENT_NAME_MEDIACONTROL_STOP_SUCCEEDED = "MediaControlStopSucceeded"
        const val EVENT_NAME_MEDIACONTROL_STOP_FAILED = "MediaControlStopFailed"

        const val EVENT_NAME_MEDIACONTROL_PAUSE_SUCCEEDED = "MediaControlPauseSucceeded"
        const val EVENT_NAME_MEDIACONTROL_PAUSE_FAILED = "MediaControlPauseFailed"

        const val EVENT_NAME_MEDIACONTROL_NEXT_SUCCEEDED = "MediaControlNextSucceeded"
        const val EVENT_NAME_MEDIACONTROL_NEXT_FAILED = "MediaControlNextFailed"

        const val EVENT_NAME_MEDIACONTROL_PREV_SUCCEEDED = "MediaControlPreviousSucceeded"
        const val EVENT_NAME_MEDIACONTROL_PREV_FAILED = "MediaControlPreviousFailed"

        const val KEY_HAS_PAIRED_DEVICES = "hasPairedDevices"
        private const val KEY_PLAY_SERVICE_ID = "playServiceId"

        const val DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00"

        val START_DISCOVERABLE_MODE = NamespaceAndName(
            NAMESPACE,
            NAME_START_DISCOVERABLE_MODE
        )
        val FINISH_DISCOVERABLE_MODE = NamespaceAndName(
            NAMESPACE,
            NAME_FINISH_DISCOVERABLE_MODE
        )
        val PLAY = NamespaceAndName(
            NAMESPACE,
            NAME_PLAY
        )
        val STOP = NamespaceAndName(
            NAMESPACE,
            NAME_STOP
        )
        val PAUSE = NamespaceAndName(
            NAMESPACE,
            NAME_PAUSE
        )
    }

    private val executor = Executors.newSingleThreadExecutor()
    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    override var address = DEFAULT_MAC_ADDRESS
    override var name: String? = null
    internal var state = State.OFF

    private var bluetoothDevice: BluetoothDevice? = null

    private var listeners = HashSet<Listener>()
    private val eventBus = BluetoothEventBus()

    init {
        /**
         * Performs initialization.
         */
        contextManager.setStateProvider(namespaceAndName, this)
    }

    internal data class StartDiscoverableModePayload(
        @SerializedName("playServiceId") val playServiceId: String,
        @SerializedName("durationInSeconds") val durationInSeconds: Long
    )

    internal data class EmptyPayload(
        @SerializedName("playServiceId") val playServiceId: String
    )

    private fun getPairedDevices(): HashMap<String, Boolean> {
        val pairedDevices = HashMap<String, Boolean>()
        pairedDevices[KEY_HAS_PAIRED_DEVICES] = this@DefaultBluetoothAgent.bluetoothDevice != null
        return pairedDevices
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()
        configuration[START_DISCOVERABLE_MODE] = nonBlockingPolicy
        configuration[FINISH_DISCOVERABLE_MODE] = nonBlockingPolicy
        configuration[PLAY] = nonBlockingPolicy
        configuration[STOP] = nonBlockingPolicy
        configuration[PAUSE] = nonBlockingPolicy
        return configuration
    }

    override fun onContextAvailable(jsonContext: String) {
        // no-op
    }

    override fun onContextFailure(error: ContextRequester.ContextRequestError) {
        // no-op
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            if (listeners.size == 0) {
                return@submit
            }

            val context = JsonObject().apply {
                addProperty("version", VERSION)

                name?.let {
                    add("device", JsonObject().apply {
                        addProperty("name", it)
                        addProperty("status", state.value)
                    })
                }

                bluetoothDevice?.let {
                    add("activeDevice", JsonObject().apply {
                        addProperty("id", it.address)
                        addProperty("name", it.name)
                        addProperty("streaming", it.state())
                    })
                }
            }.toString()

            contextSetter.setState(
                namespaceAndName,
                context,
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }


    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }


    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
        removeDirective(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info)
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun addListener(listener: Listener) {
        Logger.d(TAG, "[addListener] observer: $listener")
        this.listeners.add(listener)
    }

    override fun removeListener(listener: Listener) {
        Logger.d(TAG, "[removeListener] observer: $listener")
        this.listeners.remove(listener)
    }

    private fun sendEvent(name: String, playServiceId: String, props: HashMap<String, Boolean>? = null): Boolean {
        val request = EventMessageRequest.Builder(
            contextManager.getContextWithoutUpdate(namespaceAndName),
            NAMESPACE,
            name,
            VERSION
        ).payload(
            JsonObject().apply {
                addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                props?.forEach {
                    addProperty(it.key, it.value)
                }
            }.toString()
        ).build()

        return messageSender.sendMessage(request)
    }

    /**
     * Handle the action specified by the directive
     * @param info The directive currently being handled.
     */
    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getName()) {
            NAME_START_DISCOVERABLE_MODE -> handleStartDiscoverableMode(info)
            NAME_FINISH_DISCOVERABLE_MODE -> handleFinishDiscoverableMode(info)
            NAME_PLAY -> handlePlayDirective(info)
            NAME_STOP -> handleStopDirective(info)
            NAME_PAUSE -> handlePauseDirective(info)
            NAME_NEXT -> handleNextDirective(info)
            NAME_PREVIOUS -> handlePreviousDirective(info)
            else -> handleUnknownDirective(info)
        }
    }

    private fun handleUnknownDirective(info: DirectiveInfo) {
        Logger.w(TAG, "[handleUnknownDirective] info: $info")
        removeDirective(info)
    }

    private fun handleStartDiscoverableMode(info: DirectiveInfo) {
        Logger.d(TAG, "[HandleStartDiscoverableMode] info : $info")
        val payload =
            MessageFactory.create(info.directive.payload, StartDiscoverableModePayload::class.java)
        if (payload == null) {
            Logger.w(TAG, "[HandleStartDiscoverableMode] invalid payload: ${info.directive.payload}")
            setHandlingFailed(info, "Payload Parsing Failed")
            return
        }
        setHandlingCompleted(info)

        executor.submit {
            eventBus.subscribe(arrayListOf(
                EVENT_NAME_START_DISCOVERABLE_MODE_SUCCEEDED,
                EVENT_NAME_START_DISCOVERABLE_MODE_FAILED), object : BluetoothEventBus.Listener {
                override fun call(name: String): Boolean {
                    return sendEvent(name, payload.playServiceId, getPairedDevices())
                }
            }).subscribe(arrayListOf(
                EVENT_NAME_CONNECT_SUCCEEDED,
                EVENT_NAME_CONNECT_FAILED,
                EVENT_NAME_DISCONNECT_SUCCEEDED,
                EVENT_NAME_DISCONNECT_FAILED), object : BluetoothEventBus.Listener {
                override fun call(name: String): Boolean {
                    return sendEvent(name, payload.playServiceId)
                }
            })
            executeStartDiscoverableMode(payload.durationInSeconds)
        }
    }

    private fun getEmptyPayload(info: DirectiveInfo): EmptyPayload? {
        val payload = MessageFactory.create(info.directive.payload, EmptyPayload::class.java)
        if (payload == null) {
            Logger.w(TAG, "invalid payload: ${info.directive.payload}")
            setHandlingFailed(info, "Payload Parsing Failed")
            return null
        }
        setHandlingCompleted(info)
        return payload
    }


    private fun handleFinishDiscoverableMode(info: DirectiveInfo) {
        Logger.d(TAG, "[handleFinishDiscoverableMode] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                eventBus.subscribe(arrayListOf(
                    EVENT_NAME_FINISH_DISCOVERABLE_MODE_SUCCEEDED,
                    EVENT_NAME_FINISH_DISCOVERABLE_MODE_FAILED) , object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        // avoid memory leaks
                        eventBus.clearAllSubscribers()
                        return sendEvent(name, payload.playServiceId)
                    }
                })
                executeFinishDiscoverableMode()
            }
        }
    }

    private fun handlePlayDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePlayDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PLAY_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PLAY_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        return sendEvent(name, payload.playServiceId)
                    }
                })
                executePlay()
            }
        }
    }

    private fun handleStopDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleStopDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                val events = arrayListOf(
                EVENT_NAME_MEDIACONTROL_STOP_SUCCEEDED,
                EVENT_NAME_MEDIACONTROL_STOP_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        return sendEvent(name, payload.playServiceId)
                    }
                })
                executeStop()
            }
        }
    }

    private fun handlePauseDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePauseDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PAUSE_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PAUSE_FAILED)
                eventBus.subscribe(events , object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        return sendEvent(name, payload.playServiceId)
                    }
                })
                executePause()
            }
        }
    }

    private fun handleNextDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleNextDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_NEXT_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_NEXT_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        return sendEvent(name, payload.playServiceId)
                    }
                })

                executeNext()
            }
        }
    }

    private fun handlePreviousDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePreviousDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PREV_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PREV_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        return sendEvent(name, payload.playServiceId)
                    }
                })
                executePrevious()
            }
        }
    }

    private fun executeStop() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.AVRCPEvent(AVRCPCommand.STOP))
        }
    }

    private fun executeNext() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.AVRCPEvent(AVRCPCommand.NEXT))
        }
    }

    private fun executePrevious() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.AVRCPEvent(AVRCPCommand.PREVIOUS))
        }
    }

    private fun executePlay() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.AVRCPEvent(AVRCPCommand.PLAY))
        }
    }

    private fun executePause() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.AVRCPEvent(AVRCPCommand.PAUSE))
        }
    }

    private fun executeStartDiscoverableMode(durationInSeconds: Long) {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.DiscoverableEvent(enabled = true, durationInSeconds = durationInSeconds))
        }
    }

    private fun executeFinishDiscoverableMode() {
        listeners.forEach {
            it.onBluetoothEvent(BluetoothEvent.DiscoverableEvent(false))
        }
    }

    override fun sendBluetoothEvent(event: BluetoothEvent): Boolean {
        when (event) {
            is BluetoothEvent.DiscoverableEvent -> {
                state = if (event.enabled) State.ON else State.OFF

                Logger.d(TAG, "discoverable : $state")
                when (state) {
                    State.ON -> {
                        return eventBus.post(EVENT_NAME_START_DISCOVERABLE_MODE_SUCCEEDED)
                    }
                    State.OFF -> {
                        return try {
                            eventBus.post(EVENT_NAME_FINISH_DISCOVERABLE_MODE_SUCCEEDED)
                        } finally {
                            bluetoothDevice = null
                        }
                    }
                }
            }
            is BluetoothEvent.ConnectedEvent -> {
                Logger.d(TAG, "connected device : ${event.device}")
                bluetoothDevice = event.device
                return eventBus.post(EVENT_NAME_CONNECT_SUCCEEDED)

            }
            is BluetoothEvent.ConnectFailedEvent -> {
                Logger.d(TAG, "ConnectFailed device : ${event.device}")
                return eventBus.post(EVENT_NAME_CONNECT_FAILED)
            }
            is BluetoothEvent.DisconnectedEvent -> {
                Logger.d(TAG, "disconnected device : ${event.device}")
                bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.INACTIVE)
                return try {
                    eventBus.post(EVENT_NAME_DISCONNECT_SUCCEEDED)
                } finally {
                    bluetoothDevice = null
                }
            }
            is BluetoothEvent.AVRCPEvent -> {
                Logger.d(TAG, "bluetoothDevice is null : ${event.command}")
                return when (event.command) {
                    AVRCPCommand.PLAY -> {
                        bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.ACTIVE)
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PLAY_SUCCEEDED)
                    }
                    AVRCPCommand.PAUSE -> {
                        bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.PAUSED)
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PAUSE_SUCCEEDED)
                    }
                    AVRCPCommand.STOP -> {
                        bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.INACTIVE)
                        eventBus.post(EVENT_NAME_MEDIACONTROL_STOP_SUCCEEDED)
                    }
                    AVRCPCommand.NEXT -> {
                        bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.ACTIVE)
                        eventBus.post(EVENT_NAME_MEDIACONTROL_NEXT_SUCCEEDED)
                    }
                    AVRCPCommand.PREVIOUS -> {
                        bluetoothDevice?.state(BluetoothAgentInterface.StreamingState.ACTIVE)
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PREV_SUCCEEDED)
                    }
                }
            }
        }
    }
}