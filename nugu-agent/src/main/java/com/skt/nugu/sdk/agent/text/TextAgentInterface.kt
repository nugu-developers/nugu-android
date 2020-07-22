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
package com.skt.nugu.sdk.agent.text

import com.skt.nugu.sdk.core.interfaces.message.Header

interface TextAgentInterface {
    enum class ErrorType {
        ERROR_NETWORK,
        ERROR_RESPONSE_TIMEOUT,
        ERROR_UNKNOWN
    }

    interface RequestListener {
        fun onRequestCreated(dialogRequestId: String)
        fun onReceiveResponse(dialogRequestId: String)
        fun onError(dialogRequestId: String, type: ErrorType)
    }

    /**
     * The handler for text source directive.
     */
    interface TextSourceHandler {
        /**
         * @param payload the payload of directive
         * @param header the header of directive
         * @return true if handled, otherwise return false
         */
        fun handleTextSource(payload: String, header: Header): Boolean
    }

    /**
     * The listener about textSource directive's internal handling event.
     * If text source handled by given [TextSourceHandler], the listener is not called.
     * The handler only called when handled at internally.
     */
    interface InternalTextSourceHandlerListener: RequestListener {
        fun onRequested(dialogRequestId: String)
    }

    fun addInternalTextSourceHandlerListener(listener: InternalTextSourceHandlerListener)
    fun removeInternalTextSourceHandlerListener(listener: InternalTextSourceHandlerListener)

    /**
     * @param text the input text which to send request.
     * @param listener the listener for request
     * @return the dialogRequestId for request
     */
    fun requestTextInput(text: String, listener: RequestListener?): String
}