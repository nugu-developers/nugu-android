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
package com.skt.nugu.sdk.core.interfaces.connection

import com.skt.nugu.sdk.core.interfaces.message.MessageObserver

/**
 * This class reflects a connection to DeviceGateway and how it may be observed.
 */
interface ConnectionManagerInterface : NetworkManagerInterface {
    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    fun isConnected(): Boolean
    /**
     * the device to disconnect and connect
     * Registry Connection Handoff from SystemCapabilityAgent#handleHandoffConnection
     * @param protocol is only H2
     * @param hostname the hostname
     * @param address the address
     * @param port the port
     * @param retryCountLimit Maximum count of retries
     * @param charge (internal option)
     */
    fun handoffConnection(
        protocol: String,
        hostname: String,
        address: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    )

    /**
     * Resets the connection immediately.
     * @param description The description is just a log
     */
    fun resetConnection(description : String?)
    /**
     * Adds an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to add.
     */
    fun addMessageObserver(observer: MessageObserver)

    /**
     * Removes an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to remove.
     */
    fun removeMessageObserver(observer: MessageObserver)

    /**
     * Start the connection-oriented feature.
     * @param onCompletion This indicates that the reconnection with the server is complete and the message is ready to be sent.
     * @return success or not
    */
    fun startReceiveServerInitiatedDirective(onCompletion: (() -> Unit)? = null) : Boolean

    /**
     * Stop the connection-oriented feature.
     */
    fun stopReceiveServerInitiatedDirective()

    /**
     * Return whether the connection-oriented has been started.
     */
    fun isStartReceiveServerInitiatedDirective() : Boolean
}