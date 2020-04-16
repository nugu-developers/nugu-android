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

/**
 * This interface class allows notifications from a MessageRouter
 */
interface MessageRouterObserverInterface {
    /**
     * This function will be called when the connection status changes.
     */
    fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason : ConnectionStatusListener.ChangedReason)
    /**
     * This function will be called when a Message arrives from DeviceGateway.
     **/
    fun receive(message: Any)
}