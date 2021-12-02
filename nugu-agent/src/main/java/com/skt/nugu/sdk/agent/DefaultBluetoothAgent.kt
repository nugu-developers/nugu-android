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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.bluetooth.*
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface.*
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class DefaultBluetoothAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    focusManager: FocusManagerInterface,
    focusChannelName: String,
    private val bluetoothProvider : BluetoothProvider?,
    focusChangeHandler: OnFocusChangeHandler?
) : AbstractCapabilityAgent(NAMESPACE),
    BluetoothAgentInterface {
    /**
     * This class handles providing configuration for the bluetooth Capability agent
     */
    companion object {
        private const val TAG = "DefaultBluetoothAgent"

        const val NAMESPACE = "Bluetooth"
        private val VERSION = Version(1,1)

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

        private fun buildCompactContext() = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }
    }

    data class StateContext(
        val hostController: BluetoothHost?,
        val activeDevice: BluetoothDevice?
    ): BaseContextState {
        companion object {
            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            hostController?.let { hostController ->
                add("device", JsonObject().apply {
                    addProperty("name", hostController.name)
                    addProperty("status", hostController.state.value)

                    hostController.profiles?.let { profiles->
                        add("profiles", JsonArray().apply {
                            profiles.forEach {
                                this.add(JsonObject().apply {
                                    addProperty("name", it.name)
                                    addProperty("enabled", it.enabled)
                                })
                            }
                        })
                    }
                })
            }

            activeDevice?.let { bluetoothDevice ->
                add("activeDevice", JsonObject().apply {
                    addProperty("id", bluetoothDevice.address)
                    addProperty("name", bluetoothDevice.name)
                    addProperty("streaming", bluetoothDevice.streaming.value)
                })
            }
        }.toString()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val playbackHandlingDirectiveQueue = LinkedList<DirectiveInfo>()

    private var listener : Listener? = null
    private val eventBus = BluetoothEventBus()

    interface OnFocusChangeHandler {
        fun onFocusChanged(focus: FocusState, streamingState: StreamingState)
    }

    class StreamingChangeHandler(
        private val focusManager: FocusManagerInterface,
        private val focusChannelName: String,
        private val executor: ExecutorService,
        private val focusChangeHandler: OnFocusChangeHandler
    ) : BluetoothProvider.OnStreamStateChangeListener
        ,ChannelObserver {
        private var streamingState: StreamingState =
            StreamingState.INACTIVE

        override fun onStreamStateChanged(state: StreamingState) {
            executor.submit {
                Logger.d(TAG, "[onStreamStateChanged] $streamingState , $state")
                if (streamingState == state) {
                    return@submit
                }

                when (state) {
                    StreamingState.ACTIVE -> {
                        // always request focus if active.
                        focusManager.acquireChannel(focusChannelName, this, NAMESPACE)
                    }
                    StreamingState.UNUSABLE,
                    StreamingState.INACTIVE -> {
                        if (streamingState == StreamingState.ACTIVE || streamingState == StreamingState.PAUSED) {
                            // release focus
                            focusManager.releaseChannel(focusChannelName, this)
                        } else if (streamingState == StreamingState.UNUSABLE) {
                            // ignore
                        }
                    }
                    StreamingState.PAUSED -> {
                        // no-op
                    }
                }

                streamingState = state
            }
        }

        override fun onFocusChanged(newFocus: FocusState) {
            executor.submit {
                focusChangeHandler.onFocusChanged(newFocus, streamingState)
            }
        }
    }

    init {
        /**
         * Performs initialization.
         */
        contextManager.setStateProvider(namespaceAndName, this)

        bluetoothProvider?.setOnStreamStateChangeListener(
            StreamingChangeHandler(
                focusManager,
                focusChannelName,
                executor,
                focusChangeHandler ?: object : OnFocusChangeHandler {
                    private var focusState = FocusState.NONE

                    override fun onFocusChanged(
                        focus: FocusState,
                        streamingState: StreamingState
                    ) {
                        Logger.d(TAG, "[onFocusChanged] $focusState , $focus")
                        if (focusState == focus) {
                            return
                        }

                        when (focus) {
                            FocusState.FOREGROUND -> {
                                val shouldResume = playbackHandlingDirectiveQueue.isEmpty()
                                        && streamingState == StreamingState.PAUSED
                                if (shouldResume) {
                                    // resume
                                    executePlay()
                                }
                            }
                            FocusState.BACKGROUND -> {
                                if (streamingState == StreamingState.ACTIVE) {
                                    // pause
                                    executePause()
                                }
                            }
                            FocusState.NONE -> {
                                if (streamingState == StreamingState.ACTIVE || streamingState == StreamingState.PAUSED) {
                                    // stop
                                    executeStop()
                                }
                            }
                        }

                        focusState = focus
                    }
                })
        )
    }

    internal data class StartDiscoverableModePayload(
        @SerializedName("playServiceId") val playServiceId: String,
        @SerializedName("durationInSeconds") val durationInSeconds: Long
    )

    internal data class EmptyPayload(
        @SerializedName("playServiceId") val playServiceId: String
    )

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val nonBlockingPolicy = BlockingPolicy.sharedInstanceFactory.get()

        this[START_DISCOVERABLE_MODE] = nonBlockingPolicy
        this[FINISH_DISCOVERABLE_MODE] = nonBlockingPolicy
        this[PLAY] = nonBlockingPolicy
        this[STOP] = nonBlockingPolicy
        this[PAUSE] = nonBlockingPolicy
        this[NEXT] = nonBlockingPolicy
        this[PREVIOUS] = nonBlockingPolicy
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        executor.submit {
            if (bluetoothProvider == null) {
                contextSetter.setState(
                    namespaceAndName, StateContext.CompactContextState,
                    StateRefreshPolicy.NEVER,
                    contextType,
                    stateRequestToken
                )
            } else if (contextType == ContextType.COMPACT) {
                contextSetter.setState(
                    namespaceAndName, StateContext.CompactContextState,
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            } else {
                contextSetter.setState(
                    namespaceAndName,
                    StateContext(
                        bluetoothProvider.device(),
                        bluetoothProvider.activeDevice()
                    ),
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
        executor.submit {
            if(isPlaybackDirective(info.directive.getName())) {
                playbackHandlingDirectiveQueue.add(info)
            }
        }
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        executor.submit {
            playbackHandlingDirectiveQueue.remove(info)
        }
    }

    override fun setListener(listener: Listener) {
        Logger.d(TAG, "[setListener] $listener")
        this.listener = listener
    }

    private fun sendEventSync(
        name: String,
        playServiceId: String,
        referrerDialogRequestId: String,
        dialogRequestId: String? = null,
        hasPairedDevices: Boolean? = null
    ): Boolean {
        return sendEvent(name, playServiceId, referrerDialogRequestId, dialogRequestId, hasPairedDevices,  CountDownLatch(1))
    }

    private fun sendEvent(
        name: String,
        playServiceId: String,
        referrerDialogRequestId: String,
        dialogRequestId: String? = null,
        hasPairedDevices: Boolean? = null,
        waitResult: CountDownLatch? = null
    ): Boolean {
        var result = false

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
                    .referrerDialogRequestId(referrerDialogRequestId)
                    .payload(JsonObject().apply {
                        addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                        hasPairedDevices?.let {
                            addProperty(KEY_HAS_PAIRED_DEVICES, it)
                        }
                    }.toString())
                if(dialogRequestId != null) {
                    request.dialogRequestId(dialogRequestId)
                }

                val status = messageSender.newCall(
                    request.build()
                ).execute()
                result = status.isOk()
                waitResult?.countDown()
            }
        }, namespaceAndName)

        waitResult?.await()
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
            val referrerDialogRequestId = info.directive.header.dialogRequestId
            eventBus.subscribe(arrayListOf(
                EVENT_NAME_CONNECT_SUCCEEDED,
                EVENT_NAME_CONNECT_FAILED,
                EVENT_NAME_DISCONNECT_SUCCEEDED,
                EVENT_NAME_DISCONNECT_FAILED
            ), object : BluetoothEventBus.Listener {
                override fun call(name: String): Boolean {
                    return sendEventSync(name, payload.playServiceId, referrerDialogRequestId)
                }
            })
            executeStartDiscoverableMode(payload, referrerDialogRequestId)
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
                eventBus.clearAllSubscribers()
                val referrerDialogRequestId = info.directive.header.dialogRequestId
                executeFinishDiscoverableMode(payload.playServiceId, referrerDialogRequestId)
            }
        }
    }

    private fun handlePlayDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePlayDirective] info : $info")

        getEmptyPayload(info)?.let { payload ->
            executor.submit {
                playbackHandlingDirectiveQueue.remove(info)
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PLAY_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PLAY_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        val referrerDialogRequestId = info.directive.header.dialogRequestId
                        return sendEventSync(name, payload.playServiceId, referrerDialogRequestId)
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
                playbackHandlingDirectiveQueue.remove(info)
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_STOP_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_STOP_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        val referrerDialogRequestId = info.directive.header.dialogRequestId
                        return sendEventSync(name, payload.playServiceId, referrerDialogRequestId)
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
                playbackHandlingDirectiveQueue.remove(info)
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PAUSE_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PAUSE_FAILED)
                eventBus.subscribe(events , object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        val referrerDialogRequestId = info.directive.header.dialogRequestId
                        return sendEventSync(name, payload.playServiceId, referrerDialogRequestId)
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
                playbackHandlingDirectiveQueue.remove(info)
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_NEXT_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_NEXT_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        val referrerDialogRequestId = info.directive.header.dialogRequestId
                        return sendEventSync(name, payload.playServiceId,referrerDialogRequestId)
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
                playbackHandlingDirectiveQueue.remove(info)
                val events = arrayListOf(
                    EVENT_NAME_MEDIACONTROL_PREVIOUS_SUCCEEDED,
                    EVENT_NAME_MEDIACONTROL_PREVIOUS_FAILED)
                eventBus.subscribe(events, object : BluetoothEventBus.Listener {
                    override fun call(name: String): Boolean {
                        val referrerDialogRequestId = info.directive.header.dialogRequestId
                        return sendEventSync(name, payload.playServiceId,referrerDialogRequestId)
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

    private fun executeStartDiscoverableMode(
        payload: StartDiscoverableModePayload,
        referrerDialogRequestId: String
    ) {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()
        listener?.onDiscoverableStart(dialogRequestId, payload.durationInSeconds)?.apply {
            Logger.d(TAG, "discoverable start (success:$success)")
            if (success) {
                sendEvent(
                    EVENT_NAME_START_DISCOVERABLE_MODE_SUCCEEDED,
                    payload.playServiceId,
                    referrerDialogRequestId,
                    dialogRequestId,
                    hasPairedDevices
                )
            } else {
                sendEvent(
                    EVENT_NAME_START_DISCOVERABLE_MODE_FAILED,
                    payload.playServiceId,
                    referrerDialogRequestId,
                    dialogRequestId,
                    hasPairedDevices
                )
            }
        }
    }

    private fun executeFinishDiscoverableMode(
        playServiceId: String,
        referrerDialogRequestId: String
    ) {
        listener?.onDiscoverableFinish()?.let { success ->
            Logger.d(TAG, "discoverable finish (success:$success)")
            if (success) {
                sendEvent(
                    EVENT_NAME_FINISH_DISCOVERABLE_MODE_SUCCEEDED,
                    playServiceId,
                    referrerDialogRequestId
                )
            } else {
                sendEvent(
                    EVENT_NAME_FINISH_DISCOVERABLE_MODE_FAILED,
                    playServiceId,
                    referrerDialogRequestId
                )
            }
        }
    }

    override fun sendBluetoothEvent(event: BluetoothEvent): Boolean {
        when (event) {
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

    private fun isPlaybackDirective(name: String): Boolean = when(name) {
        NAME_PLAY,
        NAME_PAUSE,
        NAME_STOP,
        NAME_NEXT,
        NAME_PREVIOUS -> true
        else -> false
    }
}