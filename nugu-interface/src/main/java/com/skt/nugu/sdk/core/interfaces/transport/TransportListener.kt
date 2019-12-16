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

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener

/**
 * An interface class which allows a derived class to observe a Transport implementation.
 */
interface TransportListener {
    /**
     * Called when a connecting to DeviceGateway is established.
     */
    fun onConnecting(transport: Transport, reason: ConnectionStatusListener.ChangedReason)
    /**
     * Called when a connection to DeviceGateway is established.
     */
    fun onConnected(transport: Transport)
    /**
     * Called when a disconnected
     */
    fun onDisconnected(transport: Transport, reason: ConnectionStatusListener.ChangedReason)
}