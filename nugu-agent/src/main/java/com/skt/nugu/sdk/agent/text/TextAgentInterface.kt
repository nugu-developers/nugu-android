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

interface TextAgentInterface: TextInputRequester {
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
        enum class Result {
            OK, NOT_SUPPORTED_STATE
        }

        /**
         * @param payload the payload of directive
         * @param header the header of directive
         * @return [Result.OK] if should execute, otherwise if not.
         */
        fun shouldExecuteDirective(payload: String, header: Header): Result
    }

    /**
     * The handler for text redirect directive.
     */
    interface TextRedirectHandler {
        enum class Result {
            OK, NOT_SUPPORTED_STATE
        }
        /**
         * @param payload the payload of directive
         * @param header the header of directive
         * @return [Result.OK] if should execute, otherwise if not.
         */
        fun shouldExecuteDirective(payload: String, header: Header): Result
    }


    /**
     * The listener about textSource directive's internal handling event.
     * If text source handled by given [TextSourceHandler], the listener is not called.
     * The handler only called when handled at internally.
     */
    interface InternalTextSourceHandlerListener: RequestListener {
        fun onRequested(dialogRequestId: String)
    }

    interface InternalTextRedirectHandlerListener: RequestListener {
        fun onRequested(dialogRequestId: String)
    }

    fun addInternalTextSourceHandlerListener(listener: InternalTextSourceHandlerListener)
    fun removeInternalTextSourceHandlerListener(listener: InternalTextSourceHandlerListener)

    fun addInternalTextRedirectHandlerListener(listener: InternalTextRedirectHandlerListener)
    fun removeInternalTextRedirectHandlerListener(listener: InternalTextRedirectHandlerListener)

    /**
     * @param text the input text which to send request.
     * @param playServiceId the playServiceId for request
     * @param token: the token for request
     * @param source: the source for request
     * @param referrerDialogRequestId the referrerDialogRequestId for request
     * @param includeDialogAttribute the flag to include or not dialog's attribute
     * @param listener the listener for request
     * @return the dialogRequestId for request
     */
    fun requestTextInput(
        text: String,
        playServiceId: String? = null,
        token: String? = null,
        source: String? = null,
        referrerDialogRequestId: String? = null,
        includeDialogAttribute: Boolean = true,
        listener: RequestListener? = null
    ): String
}