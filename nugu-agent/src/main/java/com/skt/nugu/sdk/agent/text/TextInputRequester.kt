/**
 * Copyright (c) 2022 SK Telecom Co., Ltd. All rights reserved.
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

interface TextInputRequester {
    /**
     * @param text the input text which to send request.
     * @param playServiceId the playServiceId for request
     * @param token: the token for request
     * @param source: the source for request
     * @param referrerDialogRequestId the referrerDialogRequestId for request
     * @param includeDialogAttribute the flag to include or not dialog's attribute
     */
    data class Request(
        val text: String,
        val playServiceId: String? = null,
        val token: String? = null,
        val source: String? = null,
        val referrerDialogRequestId: String? = null,
        val includeDialogAttribute: Boolean = true,
    ) {
        class Builder(
            private val text: String
        ) {
            private var playServiceId: String? = null
            private var token: String? = null
            private var source: String? = null
            private var referrerDialogRequestId: String? = null
            private var includeDialogAttribute: Boolean = true

            fun playServiceId(playServiceId: String?): Builder = apply {
                this.playServiceId = playServiceId
            }

            fun token(token: String?): Builder = apply {
                this.token = token
            }

            fun source(source: String?): Builder = apply {
                this.source = source
            }

            fun referrerDialogRequestId(referrerDialogRequestId: String?): Builder = apply {
                this.referrerDialogRequestId = referrerDialogRequestId
            }

            fun includeDialogAttribute(includeDialogAttribute: Boolean): Builder = apply {
                this.includeDialogAttribute = includeDialogAttribute
            }

            fun build(): Request = Request(
                text,
                playServiceId,
                token,
                source,
                referrerDialogRequestId,
                includeDialogAttribute
            )
        }
    }

    /**
     * Given a request, request "Text.Input" event.
     * @param request the request. refer [Request]
     * @param listener the listener for request's status.
     * @return the dialogRequestId for request
     */
    fun textInput(request: Request, listener: TextAgentInterface.RequestListener? = null): String

    /**
     * Given a builder for request, request "Text.Input" event.
     * @param requestBuilder the builder for request. refer [Request] and [Request.Builder]
     * @param listener the listener for request's status.
     * @return the dialogRequestId for request
     */
    fun textInput(requestBuilder: Request.Builder, listener: TextAgentInterface.RequestListener? = null): String
}