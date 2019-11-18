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

import java.util.*
import java.net.HttpURLConnection
import com.skt.nugu.sdk.platform.android.login.net.HttpClient
import org.json.JSONObject
import com.skt.nugu.sdk.platform.android.login.net.FormEncodingBuilder

/**
 * NuguOAuthClient is a client for work with Authorization.
 * that manages and persists end-user credentials.
 * @see NuguOAuth
 */
internal class NuguOAuthClient(
    private val baseUrl: String
) {
    private val client = HttpClient(baseUrl)
    // The current Credentials for the app
    private var credential: Credentials
    private var options: NuguOAuthOptions

    var timeoutMs = 500L
    var retriesAttempted = 0

    enum class AuthFlowState {
        STARTING,
        REQUEST_ISSUE_TOKEN,
        STOPPING
    }

    enum class GrantType(val value: String) {
        CLIENT_CREDENTIALS("client_credentials"),
        AUTHORIZATION_CODE("authorization_code"),
    }

    companion object {
        private const val TAG = "NuguOAuthClient"
        private val maxDelayBeforeRefresh: Int = 0
        private val maxDelayForRetry: Long = 15L * 1000L /*second in ms*/
        /** Max number of times a request is retried before failing.  */
        private val maxRetries = 5
    }

    init {
        credential = Credentials.DEFAULT()
        options = NuguOAuthOptions.Builder().build()
    }

    /**
     * Request the accessToken and expiresInSecond for authentication
     * @return next [AuthFlowState]
     */
    private fun handleRequestToken(authCode: String?, refreshToken: String?): AuthFlowState {
        val grantType = options.grantType

        val form = FormEncodingBuilder()
            .add("grant_type", grantType)
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)


        when (GrantType.valueOf(grantType.toUpperCase())) {
            GrantType.CLIENT_CREDENTIALS -> {
                form.add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")
            }
            GrantType.AUTHORIZATION_CODE -> {
                if (!refreshToken.isNullOrBlank()) {
                    form.add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken)
                } else {
                    form.add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")
                        .add("code", authCode ?: "")
                        .add("redirect_uri", options.redirectUri ?: "")
                }
            }
        }

        val response = client.newCall("$baseUrl/v1/auth/oauth/token", form)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                credential = Credentials.parse(response.body)
                return AuthFlowState.STOPPING
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val json = if (response.body.isEmpty()) JSONObject() else JSONObject(response.body)
                val error = if (json.has("error")) json.get("error").toString() else ""
                val resultMessage = json.get("error_description").toString()
                throw UnAuthenticatedException("${error} : ${resultMessage}")
            }
            else -> {
                if (shouldRetry(retriesAttempted++, response.code)) {
                    return AuthFlowState.REQUEST_ISSUE_TOKEN
                }
            }
        }
        credential.clear()
        return AuthFlowState.STOPPING
    }

    /**
     * Check if the token is expired
     */
    fun isExpired(): Boolean {
        // Add a delay to be sure to not make a request with an expired token
        val now = Date().time
        return credential.expiresInSecond - now < maxDelayBeforeRefresh
    }

    /**
     * Handle the [AuthFlowState.STOPPING] AuthFlowState
     * @return [AuthFlowState.STOPPING]
     */
    private fun handleStopping(): AuthFlowState {
        return AuthFlowState.STOPPING
    }

    /**
     * Handle the [AuthFlowState.STARTING] AuthFlowState for Initial status.
     * @return [AuthFlowState.REQUEST_ISSUE_TOKEN]
     */
    private fun handleStarting(): AuthFlowState {
        resetRetriesAttempted()
        return AuthFlowState.REQUEST_ISSUE_TOKEN
    }

    /**
     * Authorization flow
     * @param refreshAccessToken true is skip the LinkDevice
     * */
    fun handleAuthorizationFlow(authCode: String?, refreshToken: String?): Boolean {
        var flowState = AuthFlowState.STARTING
        while (flowState != AuthFlowState.STOPPING) {
            flowState = when (flowState) {
                AuthFlowState.STARTING -> handleStarting()
                AuthFlowState.REQUEST_ISSUE_TOKEN -> handleRequestToken(authCode, refreshToken)
                AuthFlowState.STOPPING -> handleStopping()
            }
        }
        return credential.accessToken != ""
    }

    fun setRefreshToken(refreshToken: String) {
        credential.refreshToken = refreshToken
    }

    /**
     * Get returns current Credentials.
     */
    fun getCredentials(): Credentials {
        return credential
    }

    /**
     * Sets the Credentials.
     */
    fun setOptions(opts: NuguOAuthOptions) {
        this.options = opts
    }

    /**
     * Simple retry condition that allows retries up to a certain max number of retries.
     */
    private fun shouldRetry(retriesAttempted: Int, statusCode: Int): Boolean {
        if (retriesAttempted >= maxRetries) {
            return false
        }

        if (statusCode in 400..499) {
            Thread.sleep(timeoutMs)
            // Exponential back-off.
            timeoutMs = Math.min(timeoutMs * 2, maxDelayForRetry)
            return true
        }

        return false
    }

    /**
     * reset retrys
     */
    private fun resetRetriesAttempted() {
        timeoutMs = 500
        retriesAttempted = 0
    }
}


