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
package com.skt.nugu.sdk.platform.android.login.exception

import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthError.Companion.NETWORK_ERROR

/**
 * The BaseException class is used as the Base across all login exception classes
 */
sealed class BaseException(val error: String, val description: String, val code: String? = null) : Throwable(description) {
    /**
     * HttpErrorException denotes errors that occur in HTTP call
     */
    class HttpErrorException(val httpCode: Int, description: String) : BaseException(NETWORK_ERROR, description)
    /**
     * Thrown when authentication fails.
     */
    class UnAuthenticatedException(error: String, description: String, code: String?) : BaseException(error, description, code)
}