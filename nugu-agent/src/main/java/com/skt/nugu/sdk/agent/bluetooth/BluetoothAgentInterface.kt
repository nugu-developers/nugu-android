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
     * the hardware address of the local Bluetooth.
     */
    var address: String
    /**
     * the friendly Bluetooth name of the local Bluetooth.
     */
    var name: String?

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
        INACTIVE("INACTIVE"),
        PAUSED("PAUSED"),
        ACTIVE("ACTIVE"),
        UNUSABLE("UNUSABLE")
    }

    /** An enum representing AVRCP commands. **/
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

    /**
     * The Bluetooth events.
     */
    sealed class BluetoothEvent {
        /**
         * Event indicating that a local device has changed to discoverable mode.
         */
        class DiscoverableEvent(val enabled: Boolean, val durationInSeconds: Long = 0) : BluetoothEvent()
        /**
         * Event indicating that a remote device has changed to connections(Connected, ConnectFailed,Disconnected) .
         */
        class ConnectedEvent(val device: BluetoothDevice) : BluetoothEvent()
        class ConnectFailedEvent(val device: BluetoothDevice) : BluetoothEvent()
        class DisconnectedEvent(val device: BluetoothDevice) : BluetoothEvent()
        /**
         * Event indicating that an AVRCP command has changed.
         */
        class AVRCPEvent(val command: BluetoothAgentInterface.AVRCPCommand) : BluetoothEvent()
    }
    /**
     * Interface of a listener to be called when there has been an event of bluetooth
     * @param event a [BluetoothEvent]
     */
    interface Listener {
        fun onBluetoothEvent(event: BluetoothEvent)
    }

    /**
     * Add a listener
     * @param listener the listener that added
     */
    fun addListener(listener: Listener)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)

    /**
     * Send a local Bluetooth event to the server (DeviceGateway).
     * @return true is success, otherwise false
     */
    fun sendBluetoothEvent(event: BluetoothEvent): Boolean
}