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
package com.skt.nugu.sdk.agent.delegation

/**
 * The public interface for DelegationAgent
 * Usually, used when external app interacts with NUGU
 */
interface DelegationAgentInterface {
    /**
     * Enum class for Error
     */
    enum class Error {
        /**
         * Timeout for request
         */
        TIMEOUT,
        /**
         * Failed to request
         */
        UNKNOWN
    }

    /**
     * The listener for request
     */
    interface OnRequestListener {
        /**
         * Request handled successfully
         */
        fun onSuccess()

        /**
         * error occurs
         */
        fun onError(error: Error)
    }

    /**
     * Sends a request to NUGU.
     * @param playServiceId the identifier for play which sends request
     * @param data the data structured JSON
     * @param listener the listener to be notified request finished (success or error)
     * @throws IllegalArgumentException if there are invalid param given
     * @return the dialogRequestId for request
     */
    fun request(playServiceId: String, data: String, listener: OnRequestListener?): String
}