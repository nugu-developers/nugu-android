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

import org.json.JSONException
import org.json.JSONObject

/**
 * The data class to result of [NuguOAuthInterface.startDeviceAuthorization]
 * @param device_code The short-lived code that is used by the device when polling for a session token.
 * @param user_code A one-time user verification code. This is needed to authorize an in-use device.
 * @param verification_uri The URI of the verification page that takes the <code>userCode</code> to authorize the device.
 * @param verification_uri_complete An alternate URL that the client can use to automatically launch a browser. This process skips the manual step in
 * which the user visits the verification page and enters their code.
 * @param expires_in Indicates the number of seconds in which the verification code will become invalid.
 * @param interval Indicates the number of seconds the client must wait between attempts when polling for a session.
 */
data class DeviceAuthorizationResult(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val verification_uri_complete: String,
    val expires_in: Long,
    val interval: Long
) {
    companion object {
        /**
         * Parse to a DeviceAuthorizationResult
         * @param string is json format
         * @return a DeviceAuthorizationResult
         * @throws JSONException if the parse fails or doesn't yield a JSONObject
         */
        @Throws(JSONException::class)
        fun parse(string: String): DeviceAuthorizationResult {
            JSONObject(string).apply {
                return DeviceAuthorizationResult(
                    device_code = getString("device_code"),
                    user_code = getString("user_code"),
                    verification_uri = getString("verification_uri"),
                    verification_uri_complete = getString("verification_uri_complete"),
                    expires_in = getLong("expires_in"),
                    interval = getLong("interval")
                )
            }
        }
    }

}