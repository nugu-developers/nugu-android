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
package com.skt.nugu.sdk.platform.android.login.auth

/**
 * OAuth Options builder.
 * @param clientId is client identifier
 * @param clientSecret is client secret
 * @param redirectUri for Other Apps to Start Your Activity. only authorization_code
 * @param deviceUniqueId is device unique id
 */
data class NuguOAuthOptions(
    var clientId: String,
    var clientSecret: String,
    var redirectUri: String?,
    var deviceUniqueId: String
) {
    /**
     * Builder class for [NuguOAuthOptions] objects.
     */
    @Suppress("unused")
    class Builder {
        /**
         * the device unique ID
         * Best practices for unique identifiers : [https://developer.android.com/training/articles/user-data-ids]
         */
        private var deviceUniqueId: String = ""
        /**
         *  The NUGU Client Id
         */
        private var clientId: String = ""
        /**
         *  The NUGU Client Secret
         */
        private var clientSecret: String = ""
        /**
         *  The redirectUri
         *  only authorization_code
         */
        private var redirectUri: String? = null

        /**
         * set [deviceUniqueId]
         */
        fun deviceUniqueId(deviceUniqueId: String) = apply { this.deviceUniqueId = deviceUniqueId }
        /**
         * set [clientId]
         */
        fun clientId(clientId: String) = apply { this.clientId = clientId }
        /**
         * set [clientSecret]
         */
        fun clientSecret(clientSecret: String) = apply { this.clientSecret = clientSecret }
        /**
         * set [redirectUri]
         */
        fun redirectUri(redirectUri: String) = apply { this.redirectUri = redirectUri }
        /**
         * Returns a new instance of an Credentials based on this builder.
         */
        fun build(): NuguOAuthOptions {
            return NuguOAuthOptions(
                clientId,
                clientSecret,
                redirectUri,
                deviceUniqueId
            )
        }
    }
}