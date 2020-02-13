/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.platform.android.login.auth

import com.skt.nugu.sdk.platform.android.login.exception.BaseException

/**
 * All errors that might occur.
 * The response errors return a description as defined in the spec: [https://developers-doc.nugu.co.kr/nugu-sdk/authentication]
 */
class NuguOAuthError(val throwable: Throwable) {
    var error: String = if (throwable is BaseException) throwable.error else UNKNOWN_ERROR
    var message: String = throwable.message ?: "unspecified"

    companion object {
        /** oauth errors **/
        val INVALID_REQUEST = "invalid_request"
        val INVALID_GRANT = "invalid_grant"
        val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
        val INVALID_SCOPE = "invalid_scope"
        val REDIRECT_URI_MISMATCH = "redirect_uri_mismatch"
        val UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type"
        val UNAUTHORIZED = "unauthorized"
        val UNAUTHORIZED_CLIENT = "unauthorized_client"
        val INVALID_TOKEN = "invalid_token"
        val INVALID_CLIENT = "invalid_client"
        val ACCESS_DENIED = "access_denied"

        /** network errors **/
        val NETWORK_ERROR = "network_error"

        /** unknown errors **/
        val UNKNOWN_ERROR = "error"

        /** poc status **/
        val FINISHED = "FINISHED"
        val DROP = "DROP"
    }

    override fun toString(): String {
        val builder = StringBuilder("NuguOAuthError : ")
            .append("error: ").append(error)
            .append(", message: ").append(message)
            .append(", throwable: ").append(throwable.toString())
        return builder.toString()
    }
}