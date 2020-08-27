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

data class IntrospectResponse(
    val active: Boolean,
    val username: String
) {
    companion object {
        /**
         * Parse to a IntrospectResponse
         * @param string is json format
         * @return a IntrospectResponse
         * @throws JSONException if the parse fails or doesn't yield a JSONObject
         */
        @Throws(JSONException::class)
        fun parse(string: String): IntrospectResponse {
            JSONObject(string).apply {

                return IntrospectResponse(
                    active = getBoolean("active"),
                    username = if(isNull("username")) "" else getString("username")
                )
            }
        }
    }

}