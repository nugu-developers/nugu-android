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
package com.skt.nugu.sdk.client.port.transport.http2

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.Status
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*

/**
 * Class to manage the transport state.
 */
internal class TransportState {
    companion object {
        private const val TAG = "TransportState"
        private val stateMap = EnumMap<DetailedState, Status>(
            DetailedState::class.java)

        fun fromDetailedState(detailedState: DetailedState) : Status{
            return stateMap[detailedState] ?: Status.DISCONNECTED
        }
    }

    /**
     * Enum to Connection Detailed State of Transportable
     */
    enum class DetailedState {
        /** Ready to start data connection setup.  */
        IDLE,
        /** The connection state is connecting. */
        CONNECTING,
        /** Awaiting response from registry server in order to policy information.  */
        CONNECTING_REGISTRY,
        /** Connecting to devicegateway */
        CONNECTING_DEVICEGATEWAY,
        /** The connection state is reconnecting. **/
        RECONNECTING,
        /** Handoff the devicegateway **/
        HANDOFF,
        /** Connected to devicegateway */
        CONNECTED,
        /** performing disconnect */
        DISCONNECTING,
        /** The connection state is disconnected. */
        DISCONNECTED,
        /** Attempt to connect failed.  */
        FAILED
    }

    private var state: Status =  Status.DISCONNECTED
    private var detailedState: DetailedState =
        DetailedState.IDLE
    private var reason: ChangedReason? = null
    /**
     * This is the map described in the Javadoc comment above. The positions
     * of the elements of the array must correspond to the ordinal values
     * of `DetailedState`.
     */
    init {
        stateMap[DetailedState.IDLE] = Status.DISCONNECTED
        stateMap[DetailedState.CONNECTING] = Status.CONNECTING
        stateMap[DetailedState.CONNECTING_REGISTRY] = Status.CONNECTING
        stateMap[DetailedState.CONNECTING_DEVICEGATEWAY] = Status.CONNECTING
        stateMap[DetailedState.RECONNECTING] = Status.CONNECTING
        stateMap[DetailedState.HANDOFF] = Status.CONNECTING
        stateMap[DetailedState.CONNECTED] = Status.CONNECTED
        stateMap[DetailedState.DISCONNECTING] = Status.DISCONNECTED
        stateMap[DetailedState.DISCONNECTED] = Status.DISCONNECTED
        stateMap[DetailedState.FAILED] = Status.DISCONNECTED
    }

    fun isConnectedOrConnecting(): Boolean {
        synchronized(this) {
            return state === Status.CONNECTED || state === Status.CONNECTING
        }
    }

    /**
     * Indicates whether connection exists and it is possible to establish
     * connections and pass data.
     * @return true if the state is CONNECTED, false otherwise.
     */
    fun isConnected(): Boolean {
        synchronized(this) {
            return state === Status.CONNECTED
        }
    }

    /**
     * Reports the current state of the connection.
     * @return the state
     */
    fun getState(): Status {
        synchronized(this) {
            return state
        }
    }

    /**
     * Reports the current detail state of the connection.
     * @return the detail state
     */
    fun getDetailedState(): DetailedState? {
        synchronized(this) {
            return detailedState
        }
    }

    /**
     * Sets the detail state of the connection.
     * @param detailedState the [DetailedState].
     * @param reason a `String` indicating the reason for the state change,
     * if one was supplied. May be `null`.
     */
    fun setDetailedState(detailedState: DetailedState, reason: ChangedReason? = null) {
        Logger.d(TAG, "state changed : ${this.detailedState} -> $detailedState ")
        synchronized(this) {
            this.detailedState = detailedState
            this.state = stateMap[detailedState] ?: Status.DISCONNECTED
            this.reason = reason
        }

    }

    override fun toString(): String {
        synchronized(this) {
            val builder = StringBuilder("TransportState: ")
                .append("state: ").append(state).append("/").append(detailedState)
                .append(", reason: ").append(reason ?: "(unspecified)")
            return builder.toString()
        }
    }
}