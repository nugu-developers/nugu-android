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

import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 *  base class for an authorized identity
 **/
data class Credentials(
    /** The NUGU auth token **/
    var accessToken: String,
    /** The NUGU refresh token **/
    var refreshToken: String,
    /** The lifetime in seconds of the access token **/
    var expiresIn: Long,
    /** The issued Time **/
    var issuedTime: Long,
    /** OAuth Access Token Type **/
    var tokenType: String
) {

    /**
     * clear all of the elements from this list.
     */
    fun clear() {
        accessToken = ""
        refreshToken = ""
        expiresIn = 0
        issuedTime = 0
        tokenType = ""
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        val json = JSONObject()
        json.put("access_token", accessToken)
        json.put("token_type", tokenType)
        json.put("expires_in", expiresIn)
        json.put("issued_time", issuedTime)
        json.put("refresh_token", refreshToken)
        return json.toString()
    }
    /**
     * Companion objects
     */
    companion object {
        /**
         * Default constructor of Credentials
         **/
        fun DEFAULT() = Credentials(
            accessToken = "",
            refreshToken = "",
            expiresIn = 0,
            issuedTime = 0,
            tokenType = ""
        )

        /**
         * Parse to a Credentials
         * @param string is json format
         * @return a Credentials
         * @throws JSONException if the parse fails or doesn't yield a JSONObject
         */
        @Throws(JSONException::class)
        fun parse(string: String): Credentials {
            if (string.isBlank()) {
                return DEFAULT()
            }
            JSONObject(string).apply {
                return Credentials(
                    accessToken = getString("access_token"),
                    expiresIn = getLong("expires_in"),
                    issuedTime = if(!has("issued_time")) Date().time else getLong("issued_time"),
                    tokenType = getString("token_type"),
                    refreshToken = if (!has("refresh_token")) "" else getString("refresh_token")
                )
            }
        }
    }

    /**
     * Constructs a new [Credentials] builder,
     */
    class Builder {
        /**
         * The NUGU auth token.
         */
        private var accessToken: String = ""
        /**
         * The NUGU refresh token.
         */
        private var refreshToken: String = ""
        /**
         * The lifetime in seconds of the access token.
         * A time when credentials should be considered expired.
         */
        private var expiresIn: Long = 0
        /**
         * The issue time associated with the access token.
         */
        private var issuedTime: Long = 0
        /**
         * OAuth Access Token Type
         * @see [https://tools.ietf.org/html/rfc6750]
         */
        private var tokenType: String = ""

        /**
         * Returns a new instance of an Credentials based on this builder.
         */
        fun build() = Credentials(
            accessToken,
            refreshToken,
            expiresIn,
            issuedTime,
            tokenType
        )
    }
}