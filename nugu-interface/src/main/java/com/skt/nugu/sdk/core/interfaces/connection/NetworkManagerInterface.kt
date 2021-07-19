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

/**
 * The class provide interfaces to control the network connection to NUGU.
 */
interface NetworkManagerInterface {
    /**
     * Enable network manager.
     * @param quiet expectations are no longer notify the CONNECTED state when this is true. Defaults to false.
     */
    @Deprecated(message = "No longer used by NetworkManager")
    fun enable(quiet: Boolean = false)

    /**
     * Disable network manager
     */
    @Deprecated(message = "No longer used by NetworkManager", replaceWith = ReplaceWith( expression = "shutdown()"))
    fun disable()

    /**
     * Shutdown network manager
     * Shut down all connections and clean up.
     */
    fun shutdown()

    /**
     * Add listener to be notified when connection status changed for NUGU
     * @param listener the listener that will add
     */
    fun addConnectionStatusListener(listener: ConnectionStatusListener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeConnectionStatusListener(listener: ConnectionStatusListener)
}