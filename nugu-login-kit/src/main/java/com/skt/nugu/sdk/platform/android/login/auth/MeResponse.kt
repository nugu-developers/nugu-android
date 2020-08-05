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


data class MeResponse(
    val anonymous: Boolean,
    val deviceId: String,
    val tid: String,
    val userId: String
) {
    companion object {
        /**
         * Parse to a AccountInfo
         * @param string is json format
         * @return a AccountInfo
         * @throws JSONException if the parse fails or doesn't yield a JSONObject
         */
        @Throws(JSONException::class)
        fun parse(string: String): MeResponse {
            JSONObject(string).apply {

                return MeResponse(
                    anonymous = getBoolean("anonymous"),
                    deviceId = getString("deviceId"),
                    tid = if(isNull("tid")) "" else getString("tid"),
                    userId = getString("userId")
                )
            }
        }
    }

}