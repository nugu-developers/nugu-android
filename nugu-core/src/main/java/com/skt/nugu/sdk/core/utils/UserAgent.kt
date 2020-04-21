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
package com.skt.nugu.sdk.core.utils

/**
 * Provides version of sdk
 */
class UserAgent {
    companion object {
        /**
         * Returns the sdk version
         */
        private var sdkVersion = "1.0.0"

        /**
         * Returns the client version
         */
        private var clientVersion = "1.0.0"

        fun setVersion(sdkVersion: String, clientVersion: String) {
            this.sdkVersion = sdkVersion
            this.clientVersion = clientVersion
        }

        override fun toString(): String {
            val builder = StringBuilder()
                .append("OpenSDK").append("/").append(sdkVersion)
                .append(" ")
                .append("Client").append("/").append(clientVersion)
            return builder.toString()
        }
    }
}