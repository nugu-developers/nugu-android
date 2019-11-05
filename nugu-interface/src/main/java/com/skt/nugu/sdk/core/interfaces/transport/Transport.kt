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
package com.skt.nugu.sdk.core.interfaces.transport

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest

/**
 * This class defines the interface for transport
 * that must be implemented to represent the creation and management of an interface.
 */
interface Transport {
    /**
     * Initiate a connection to DeviceGateway.
     */
    fun connect(): Boolean

    /**
     * Disconnect from DeviceGateway.
     */
    fun disconnect()

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    fun isConnected(): Boolean

    /**
     * Sends an message request.
     */
    fun send(request: MessageRequest)

    /**
     *  Explicitly clean up client resources.
     */
    fun shutdown()

    /**
     *  Redirecting connection from SystemCapability
     */
    fun onHandoffConnection(protocol: String,
                            domain: String,
                            hostname: String,
                            port: Int,
                            retryCountLimit: Int,
                            connectionTimeout: Int,
                            charge: String)
}