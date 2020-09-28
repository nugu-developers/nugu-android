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

import com.skt.nugu.sdk.platform.android.login.exception.BaseException
import java.util.*
import java.net.HttpURLConnection
import com.skt.nugu.sdk.platform.android.login.net.HttpClient
import org.json.JSONObject
import com.skt.nugu.sdk.platform.android.login.net.FormEncodingBuilder
import com.skt.nugu.sdk.platform.android.login.net.Headers
import com.skt.nugu.sdk.platform.android.login.net.Request
import java.io.IOException
import kotlin.math.min


/**
 * NuguOAuthClient is a client for work with Authorization.
 * that manages and persists end-user credentials.
 * @see NuguOAuth
 */
internal class NuguOAuthClient(private val baseUrl: String) {
    // The http client
    private val client = HttpClient(baseUrl)

    // The current Credentials for the app
    private var credential: Credentials

    // The current OAuthOptions
    private var options: NuguOAuthOptions

    // Retry attempts
    private var retriesAttempted = 0

    /**
     * Enum class of AuthFlowState
     */
    enum class AuthFlowState {
        STARTING,
        REQUEST_ISSUE_TOKEN,
        STOPPING
    }

    enum class GrantType(val value: String) {
        CLIENT_CREDENTIALS("client_credentials"),
        AUTHORIZATION_CODE("authorization_code"),
        DEVICE_CODE("device_code"),
        REFRESH_TOKEN("refresh_token")
    }

    companion object {
        private const val TAG = "NuguOAuthClient"
        private const val maxDelayForRetry: Long = 15L * 1000L /*second in ms*/

        /** Max number of times a request is retried before failing.  */
        private const val maxRetries = 5

        /** The default timeout in milliseconds*/
        private const val initialTimeoutMs = 300L
    }

    init {
        credential = Credentials.DEFAULT()
        options = NuguOAuthOptions.Builder().build()
    }

    /**
     * Request the accessToken and expiresInSecond for authentication
     * @return next [AuthFlowState]
     * @throws IOException if the request could not be executed due to
     *     cancellation, a connectivity problem or timeout. Because networks can
     *     fail during an exchange, it is possible that the remote server
     *     accepted the request before the failure.
     */
    private fun handleRequestToken(code: String?, refreshToken: String?): AuthFlowState {
        val grantType = options.grantType

        val form = FormEncodingBuilder()
            .add("grant_type", grantType)
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")

        when (GrantType.valueOf(grantType.toUpperCase())) {
            GrantType.CLIENT_CREDENTIALS -> {
                // no op
            }
            GrantType.AUTHORIZATION_CODE -> {
                form.add("code", code.toString())
                    .add("redirect_uri", options.redirectUri.toString())
            }
            GrantType.REFRESH_TOKEN -> {
                form.add("refresh_token", refreshToken.toString())
            }
            GrantType.DEVICE_CODE -> {
                form.add("device_code", code.toString())
            }
        }

        try {
            val request = Request.Builder(
                uri = "$baseUrl/v1/auth/oauth/token",
                form = form
            ).build()
            val response = client.newCall(request)
            when (response.code) {
                HttpURLConnection.HTTP_OK -> {
                    credential = Credentials.parse(response.body)
                    return AuthFlowState.STOPPING
                }
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_BAD_REQUEST -> {
                    val body = JSONObject(response.body)
                    throw BaseException.UnAuthenticatedException(
                        error = body.get("error").toString(),
                        description = body.get("error_description").toString(),
                        code = body.optString("code")
                    )
                }
                else -> {
                    if (shouldRetry(
                            retriesAttempted = ++retriesAttempted,
                            statusCode = response.code
                        )
                    ) {
                        return AuthFlowState.REQUEST_ISSUE_TOKEN
                    }

                    throw BaseException.HttpErrorException(
                        response.code,
                        response.body
                    )
                }
            }
        } catch (e: Throwable) {
            if (shouldRetry(retriesAttempted = ++retriesAttempted, throwable = e)) {
                return AuthFlowState.REQUEST_ISSUE_TOKEN
            }
            throw e
        }
    }

    /**
     * Check if the token is expired
     */
    fun isExpired(): Boolean {
        // Add a delay to be sure to not make a request with an expired token
        val now = Date().time
        return (credential.expiresIn * 1000) + credential.issuedTime < now
    }

    /**
     * Handle the [AuthFlowState.STOPPING] AuthFlowState
     * @return [AuthFlowState.STOPPING]
     * @throws Throwable if the token is empty
     */
    private fun handleStopping(): AuthFlowState {
        if (credential.accessToken.isBlank()) {
            throw Throwable("accessToken is empty")
        }
        return AuthFlowState.STOPPING
    }

    /**
     * Handle the [AuthFlowState.STARTING] AuthFlowState for Initial status.
     * @return [AuthFlowState.REQUEST_ISSUE_TOKEN]
     */
    private fun handleStarting(): AuthFlowState {
        //reset retrys
        retriesAttempted = 0
        return AuthFlowState.REQUEST_ISSUE_TOKEN
    }

    /**
     * Authorization flow
     * @param refreshAccessToken true is skip the LinkDevice
     * */
    fun handleAuthorizationFlow(code: String?, refreshToken: String?) {
        var flowState = AuthFlowState.STARTING
        while (flowState != AuthFlowState.STOPPING) {
            flowState = when (flowState) {
                AuthFlowState.STARTING -> handleStarting()
                AuthFlowState.REQUEST_ISSUE_TOKEN -> handleRequestToken(code, refreshToken)
                AuthFlowState.STOPPING -> handleStopping()
            }
        }
    }

    fun handleStartDeviceAuthorization(data: String): DeviceAuthorizationResult {
        val form = FormEncodingBuilder()
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("data", data)
        val request = Request.Builder(uri = "$baseUrl/v1/auth/oauth/device_authorization",
            form = form).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                DeviceAuthorizationResult.parse(response.body)?.let {
                    return it
                }
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.get("error_description").toString(),
                    code = body.optString("code")
                )
            }
            else -> {
                throw BaseException.HttpErrorException(
                    response.code,
                    response.body
                )
            }
        }
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
     * Set the credential information.
     * @param credential The credential information to set to
     */
    fun setCredentials(credential: Credentials) {
        this.credential = credential
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
    private fun shouldRetry(
        retriesAttempted: Int,
        statusCode: Int? = null,
        throwable: Throwable? = null
    ): Boolean {
        if (retriesAttempted >= maxRetries) {
            return false
        }
        if (statusCode in 400..499 || throwable is IOException) {
            // Exponential back-off.
            val sleepMillis = min(
                Math.pow(retriesAttempted.toDouble() + 1, 2.0).toLong() * initialTimeoutMs,
                maxDelayForRetry
            )
            Thread.sleep(sleepMillis)
            return true
        }
        return false
    }
    @Deprecated("Use introspect")
    fun handleMe() : MeResponse {
        val header = Headers()
            .add("authorization", buildAuthorization())

        val request = Request.Builder(
            uri = "$baseUrl/v1/auth/oauth/me",
            headers = header,
            method = "GET"
        ).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                return MeResponse.parse(response.body)
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.get("resultMessage").toString(),
                    code = body.optString("resultCode")
                )
            }
            else -> {
                throw BaseException.HttpErrorException(
                    response.code,
                    response.body
                )
            }
        }
    }

    fun handleRevoke() {
        val form = FormEncodingBuilder()
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("token", getCredentials().accessToken)
            .add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")

        val request = Request.Builder(
            uri = "$baseUrl/v1/auth/oauth/revoke",
            form = form
        ).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                return
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.get("error_description").toString(),
                    code = body.optString("code")
                )
            }
            else -> {
                throw BaseException.HttpErrorException(
                    response.code,
                    response.body
                )
            }
        }
    }

    fun buildAuthorization(): String {
        return getCredentials().tokenType + " " + getCredentials().accessToken
    }

    fun handleIntrospect(): IntrospectResponse {
        val form = FormEncodingBuilder()
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("token", getCredentials().accessToken)
            .add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")

        val request = Request.Builder(
            uri = "$baseUrl/v1/auth/oauth/introspect",
            form = form
        ).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                IntrospectResponse.parse(response.body)?.let {
                    return it
                }
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.get("error_description").toString(),
                    code = body.optString("code")
                )
            }
            else -> {
                throw BaseException.HttpErrorException(
                    response.code,
                    response.body
                )
            }
        }
    }
}


