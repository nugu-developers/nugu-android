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
package com.skt.nugu.sdk.agent.extension

import com.skt.nugu.sdk.core.interfaces.common.EventCallback

/**
 * The public interface for ExtensionAgent
 */
interface ExtensionAgentInterface : IssueCommandEventRequester {
    /**
     * The client for Extension Agent Interface
     */
    interface Client {
        /**
         * Called when receive Extension.Action directive.
         *
         * @param data the required data for action
         * @param playServiceId the playServiceId
         * @param dialogRequestId the dialogRequestId for action
         * @return true: success, false: otherwise
         */
        fun action(data: String, playServiceId: String, dialogRequestId: String): Boolean

        /**
         * Return a data string in structured JSON.
         * @return a data string in structured JSON. if not exist, null.
         */
        fun getData(): String?
    }

    /**
     * enum class for ErrorType
     */
    enum class ErrorType {
        REQUEST_FAIL,
        RESPONSE_TIMEOUT
    }

    /**
     * callback interface for [issueCommand]
     */
    interface OnCommandIssuedCallback : EventCallback<ErrorType>

    /**
     * Set the client which interact with agent
     * @param client the client which interact with agent
     */
    fun setClient(client: Client)
}