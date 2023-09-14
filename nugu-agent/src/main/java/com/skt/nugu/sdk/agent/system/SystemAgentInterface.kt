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
package com.skt.nugu.sdk.agent.system

/**
 * The public interface for SystemCapabilityAgent
 */
interface SystemAgentInterface {
    enum class ExceptionCode {
        PLAY_ROUTER_PROCESSING_EXCEPTION,
        TTS_SPEAKING_EXCEPTION,
        UNAUTHORIZED_REQUEST_EXCEPTION,
        INTERNAL_SERVICE_EXCEPTION,
        ASR_RECOGNIZING_EXCEPTION,
        CONCURRENT_CONNECTION_EXCEPTION,
        INVALID_REQUEST_EXCEPTION,
        SERVICE_UNAVAILABLE_EXCEPTION
    }
    enum class RevokeReason {
        REVOKED_DEVICE,
        WITHDRAWN_USER
    }

    /**
     * This interface is used by the System Capability agent
     */
    interface Listener {
        /**
         * After receiving an event, turn off immediately.
         */
        fun onTurnOff()

        /**
         * Called on receive exception.
         * @param code
         * @param description
         */
        fun onException(code: ExceptionCode, description: String?)

        /**
         * The device has been revoked.
         */
        fun onRevoke(reason: RevokeReason)

        /**
         * After receiving an event, terminate app according to app's rule.
         */
        fun onTerminateApp(payload: String)
    }

    /**
     * @hide
     * internal function
     * **/
    fun onEcho()

    /**
     * Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: Listener)
    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)
}