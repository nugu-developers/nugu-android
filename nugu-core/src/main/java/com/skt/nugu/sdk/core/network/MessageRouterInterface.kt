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
package com.skt.nugu.sdk.core.network

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

/**
 * This specifies the interface to manage a connection over some medium to DeviceGateway.
 */
interface MessageRouterInterface : MessageSender {
    /**
     * Shutdown network manager
     */
    fun shutdown()

    /**
     * Set the observer to this object.
     */
    fun setObserver(observer: MessageRouterObserverInterface)

    /**
     * Get the connection status.
     */
    fun getConnectionStatus(): ConnectionStatusListener.Status


    /**
     * Get the connection ChangedReason.
     */
    fun getConnectionChangedReason(): ConnectionStatusListener.ChangedReason

    /**
     *  handoff connection from SystemCapability
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
     */
    fun resetConnection(description: String?)

    /**
     * Start the connection-oriented feature.
     * @param onCompletion This indicates that the reconnection with the server is complete and the message is ready to be sent.
     * @return success or not
     */
    fun startReceiveServerInitiatedDirective(onCompletion: (() -> Unit)?) : Boolean

    /**
     * Stop the connection-oriented feature.
     */
    fun stopReceiveServerInitiatedDirective()

    /**
     * Return whether the connection-oriented has been started.
     */
    fun isStartReceiveServerInitiatedDirective() : Boolean
}