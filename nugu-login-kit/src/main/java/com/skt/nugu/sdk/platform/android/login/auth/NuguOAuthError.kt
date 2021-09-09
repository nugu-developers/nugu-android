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

import android.content.ActivityNotFoundException
import com.skt.nugu.sdk.platform.android.login.exception.BaseException
import com.skt.nugu.sdk.platform.android.login.exception.ClientUnspecifiedException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * All errors that might occur.
 * The response errors return a description as defined in the spec: [https://developers-doc.nugu.co.kr/nugu-sdk/authentication]
 */
class NuguOAuthError(val throwable: Throwable) {
    var error: String = when(throwable) {
        is BaseException -> throwable.error
        is UnknownHostException,is SocketTimeoutException,is SocketException -> NETWORK_ERROR
        is ActivityNotFoundException -> ACTIVITY_NOT_FOUND_ERROR
        is SecurityException -> SECURITY_ERROR
        is ClientUnspecifiedException, is UninitializedPropertyAccessException -> INITIALIZE_ERROR
        else -> UNKNOWN_ERROR
    }
    var description: String = throwable.message ?: "unspecified"
    var code: String? =  (throwable as? BaseException.UnAuthenticatedException)?.code
    var httpCode: Int? =  (throwable as? BaseException.HttpErrorException)?.httpCode

    @Suppress("unused")
    companion object {
        /** oauth errors **/
        const val INVALID_REQUEST = "invalid_request"
        const val INVALID_GRANT = "invalid_grant"
        const val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
        const val INVALID_SCOPE = "invalid_scope"
        const val REDIRECT_URI_MISMATCH = "redirect_uri_mismatch"
        const val UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type"
        const val UNAUTHORIZED = "unauthorized"
        const val UNAUTHORIZED_CLIENT = "unauthorized_client"
        const val INVALID_TOKEN = "invalid_token"
        const val INVALID_CLIENT = "invalid_client"
        const val ACCESS_DENIED = "access_denied"

        /** oauth code **/
        const val USER_ACCOUNT_CLOSED = "user_account_closed"
        const val USER_ACCOUNT_PAUSED =  "user_account_paused"
        const val USER_DEVICE_DISCONNECTED =  "user_device_disconnected"
        const val USER_DEVICE_UNEXPECTED = "user_device_unexpected"

        /** network errors **/
        const val NETWORK_ERROR = "network_error"

        /** unknown errors **/
        const val UNKNOWN_ERROR = "unknown_error"

        /** ActivityNotFoundException **/
        const val ACTIVITY_NOT_FOUND_ERROR = "activity_not_found_error"

        /** SecurityException **/
        const val SECURITY_ERROR = "security_error"

        /** ClientUnspecifiedException, UninitializedPropertyAccessException **/
        const val INITIALIZE_ERROR = "initialize_error"

        /** poc status **/
        const val FINISHED = "FINISHED"
        const val DROP = "DROP"
    }

    override fun toString(): String {
        val builder = StringBuilder("NuguOAuthError : ")
            .append("error: ").append(error)
            .append(", message: ").append(description)
            .append(", throwable: ").append(throwable.toString())
        return builder.toString()
    }
}