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
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.Listener
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.AVRCPCommand
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.BluetoothEvent
import com.skt.nugu.sdk.agent.bluetooth.BluetoothProvider

import com.skt.nugu.sdk.agent.bluetooth.BluetoothEventBus
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import java.util.concurrent.CountDownLatch
import kotlin.collections.HashMap


class DefaultBluetoothAgent(
    messageSender: MessageSender,
    contextManager: ContextManagerInterface,
    bluetoothProvider : BluetoothProvider?
) : AbstractBluetoothAgent(
    messageSender,
    contextManager,
    bluetoothProvider
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

        const val EVENT_NAME_MEDIACONTROL_PREVIOUS_SUCCEEDED = "MediaControlPreviousSucceeded"
        const val EVENT_NAME_MEDIACONTROL_PREVIOUS_FAILED = "MediaControlPreviousFailed"

        const val KEY_HAS_PAIRED_DEVICES = "hasPairedDevices"
        private const val KEY_PLAY_SERVICE_ID = "playServiceId"

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
        val NEXT = NamespaceAndName(
            NAMESPACE,
            NAME_NEXT
        )
        val PREVIOUS = NamespaceAndName(
            NAMESPACE,
            NAME_PREVIOUS
        )
    }

    private val executor = Executors.newSingleThreadExecutor()
    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    private var listener : Listener? = null
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

        bluetoothProvider?.let {
            pairedDevices[KEY_HAS_PAIRED_DEVICES] = it.activeDevice() != null
        }
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
        configuration[NEXT] = nonBlockingPolicy
        configuration[PREVIOUS] = nonBlockingPolicy
        return configuration
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            bluetoothProvider?.let {
                val context = JsonObject().apply {
                    addProperty("version", VERSION)

                    it.device()?.let { hostController ->
                        add("device", JsonObject().apply {
                            addProperty("name", hostController.name)
                            addProperty("status", hostController.state.value)
                        })
                    }

                    it.activeDevice()?.let { bluetoothDevice ->
                        add("activeDevice", JsonObject().apply {
                            addProperty("id", bluetoothDevice.address)
                            addProperty("name", bluetoothDevice.name)
                            addProperty("streaming", bluetoothDevice.state.value)
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

    override fun setListener(listener: Listener) {
        Logger.d(TAG, "[setListener] $listener")
        this.listener = listener
    }

    private fun sendEvent(name: String, playServiceId: String, props: HashMap<String, Boolean>? = null): Boolean {
        val waitResult = CountDownLatch(1)
        var result = false
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION)
                    .payload(JsonObject().apply {
                        addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                        props?.forEach {
                            addProperty(it.key, it.value)
                        }
                    }.toString()).build()

                result = messageSender.sendMessage(request)
                waitResult.countDown()
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                waitResult.countDown()
            }
        }, namespaceAndName)

        waitResult.await()
        return result
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
                    EVENT_NAME_MEDIACONTROL_PREVIOUS_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PREVIOUS_FAILED)
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
        listener?.onAVRCPCommand(AVRCPCommand.STOP)
    }

    private fun executeNext() {
        listener?.onAVRCPCommand(AVRCPCommand.NEXT)
    }

    private fun executePrevious() {
        listener?.onAVRCPCommand(AVRCPCommand.PREVIOUS)
    }

    private fun executePlay() {
        listener?.onAVRCPCommand(AVRCPCommand.PLAY)
    }

    private fun executePause() {
        listener?.onAVRCPCommand(AVRCPCommand.PAUSE)
    }

    private fun executeStartDiscoverableMode(durationInSeconds: Long) {
        listener?.onDiscoverableStart(durationInSeconds = durationInSeconds)
    }

    private fun executeFinishDiscoverableMode() {
        listener?.onDiscoverableFinish()
    }

    override fun sendBluetoothEvent(event: BluetoothEvent): Boolean {
        when (event) {
            is BluetoothEvent.DiscoverableEvent -> {
                Logger.d(TAG, "discoverable : ${event.enabled}")
                return when (event.enabled) {
                    true -> {
                        eventBus.post(EVENT_NAME_START_DISCOVERABLE_MODE_SUCCEEDED)
                    }
                    false -> {
                        eventBus.post(EVENT_NAME_FINISH_DISCOVERABLE_MODE_SUCCEEDED)
                    }
                }
            }
            is BluetoothEvent.ConnectedEvent -> {
                Logger.d(TAG, "connected device")
                return eventBus.post(EVENT_NAME_CONNECT_SUCCEEDED)

            }
            is BluetoothEvent.ConnectFailedEvent -> {
                Logger.d(TAG, "ConnectFailed device")
                return eventBus.post(EVENT_NAME_CONNECT_FAILED)
            }
            is BluetoothEvent.DisconnectedEvent -> {
                Logger.d(TAG, "disconnected device")
                return eventBus.post(EVENT_NAME_DISCONNECT_SUCCEEDED)
            }
            is BluetoothEvent.AVRCPEvent -> {
                Logger.d(TAG, "bluetoothDevice command : ${event.command}")
                return when (event.command) {
                    AVRCPCommand.PLAY -> {
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PLAY_SUCCEEDED)
                    }
                    AVRCPCommand.PAUSE -> {
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PAUSE_SUCCEEDED)
                    }
                    AVRCPCommand.STOP -> {
                        eventBus.post(EVENT_NAME_MEDIACONTROL_STOP_SUCCEEDED)
                    }
                    AVRCPCommand.NEXT -> {
                        eventBus.post(EVENT_NAME_MEDIACONTROL_NEXT_SUCCEEDED)
                    }
                    AVRCPCommand.PREVIOUS -> {
                        eventBus.post(EVENT_NAME_MEDIACONTROL_PREVIOUS_SUCCEEDED)
                    }
                }
            }
        }
    }
}