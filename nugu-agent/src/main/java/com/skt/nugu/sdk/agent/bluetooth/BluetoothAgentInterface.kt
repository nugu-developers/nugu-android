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
package com.skt.nugu.sdk.agent.bluetooth

/**
 * Interface for Bluetooth Capability Agent
 */
interface BluetoothAgentInterface {
    /**
     * The enum State describes the state of the bluetooth (on,off).
     */
    enum class State(val value: String) {
        ON("ON"),
        OFF("OFF")
    }

    /**
     * An enum representing the current state of the stream.
     */
    enum class StreamingState(val value: String) {
        // The initial streaming state before the content is streamed.
        INACTIVE("INACTIVE"),
        // The streaming state when audio playback is paused
        PAUSED("PAUSED"),
        // The streaming state of the connected Bluetooth device and the active device that plays audio.
        ACTIVE("ACTIVE"),
        // The streaming state when audio playback is not available, For example, unsupported avrcp.
        UNUSABLE("UNUSABLE")
    }

    /**
     * An enum representing AVRCP commands.
     */
    enum class AVRCPCommand {
        // A Play command.
        PLAY,
        // A Pause command.
        PAUSE,
        // A Stop command.
        STOP,
        // A Next command.
        NEXT,
        // A Previous command.
        PREVIOUS
    }
    data class DiscoverableStartResult(
        val success: Boolean,
        val hasPairedDevices: Boolean
    )
    /**
     * The Bluetooth events.
     */
    sealed class BluetoothEvent {
        /**
         * Event indicating that a remote device has changed to connections(Connected, ConnectFailed, Disconnected).
         */
        class ConnectedEvent : BluetoothEvent()
        class ConnectFailedEvent : BluetoothEvent()
        class DisconnectedEvent : BluetoothEvent()
        /**
         * Event indicating that an [AVRCPCommand] has changed.
         */
        class AVRCPEvent(val command: AVRCPCommand) : BluetoothEvent()
    }
    /**
     * Interface of a listener to be called when there has been an directive of bluetooth from the server.
     * @param event a [BluetoothEvent]
     */
    interface Listener {
        fun onDiscoverableStart(dialogRequestId: String, durationInSeconds: Long = 0) : DiscoverableStartResult
        fun onDiscoverableFinish() : Boolean
        fun onAVRCPCommand(command: AVRCPCommand)
    }

    /**
     * Set a listener
     * @param listener the listener that added
     */
    fun setListener(listener: Listener)

    /**
     * Send a local Bluetooth event to the server (DeviceGateway).
     * @return true is success, otherwise false
     */
    fun sendBluetoothEvent(event: BluetoothEvent): Boolean
}