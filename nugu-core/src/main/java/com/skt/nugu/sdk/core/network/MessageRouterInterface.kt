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
     * Enable network manager.
     */
    fun enable()

    /**
     * Disable network manager
     */
    fun disable()

    /**
     * Set the observer to this object.
     */
    fun setObserver(observer: MessageRouterObserverInterface)

    /**
     * Get the connection status.
     */
    fun getConnectionStatus(): ConnectionStatusListener.Status

    /**
     *  Redirecting connection from SystemCapability
     */
    fun onHandoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    )
}