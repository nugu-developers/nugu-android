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

import androidx.annotation.VisibleForTesting
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
import kotlin.math.pow


/**
 * NuguOAuthClient is a client for work with Authorization.
 * that manages and persists end-user credentials.
 * @see NuguOAuth
 */
class NuguOAuthClient(private val delegate: UrlDelegate) {
    // The http client
    private val client = HttpClient(delegate)

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
        private const val maxDelayForRetry: Long = 15L * 1000L /*second in ms*/

        /** Max number of times a request is retried before failing.  */
        private const val maxRetries = 5

        /** The default timeout in milliseconds*/
        private const val initialTimeoutMs = 300L
    }

    interface UrlDelegate {
        fun baseUrl() : String
        fun tokenEndpoint() : String
        fun authorizationEndpoint() : String
        fun introspectionEndpoint() : String
        fun revocationEndpoint() : String
        fun deviceAuthorizationEndpoint() : String
        fun meEndpoint() : String
    }

    init {
        credential = Credentials.getDefault()
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
    private fun handleRequestToken(authorizationRequest: AuthorizationRequest): AuthFlowState {
        val form = FormEncodingBuilder()
            .add("grant_type", authorizationRequest.grantType.value)
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("data", "{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}")

        runCatching {
            when (authorizationRequest) {
                is AuthorizationRequest.AuthorizationCodeRequest -> {
                    form.add("code", authorizationRequest.code.toString())
                        .add("redirect_uri", options.redirectUri.toString())
                }
                is AuthorizationRequest.ClientCredentialsRequest -> {
                    // no op
                }
                is AuthorizationRequest.DeviceCodeRequest -> {
                    form.add("device_code", authorizationRequest.deviceCode.toString())
                }
                is AuthorizationRequest.RefreshTokenRequest -> {
                    form.add("refresh_token", authorizationRequest.refreshToken.toString())
                }
            }
        }

        try {
            val request = Request.Builder(
                uri = delegate.tokenEndpoint(),
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
                        description = body.optString("error_description"),
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
    @VisibleForTesting
    internal fun handleStopping(): AuthFlowState {
        if (credential.accessToken.isBlank()) {
            throw Throwable("accessToken is empty")
        }
        return AuthFlowState.STOPPING
    }

    /**
     * Handle the [AuthFlowState.STARTING] AuthFlowState for Initial status.
     * @return [AuthFlowState.REQUEST_ISSUE_TOKEN]
     */
    @VisibleForTesting
    internal fun handleStarting(): AuthFlowState {
        //reset retrys
        retriesAttempted = 0
        return AuthFlowState.REQUEST_ISSUE_TOKEN
    }

    /**
     * Authorization flow
     * @param code authCode
     * @param refreshToken refresh Token
     * */
    fun handleAuthorizationFlow(authorizationRequest: AuthorizationRequest) { //grantType: GrantType, code: String?, refreshToken: String?) {
        var flowState = AuthFlowState.STARTING
        while (flowState != AuthFlowState.STOPPING) {
            flowState = when (flowState) {
                AuthFlowState.STARTING -> handleStarting()
                AuthFlowState.REQUEST_ISSUE_TOKEN -> handleRequestToken(authorizationRequest)
                AuthFlowState.STOPPING -> handleStopping()
            }
        }
    }

    fun handleStartDeviceAuthorization(data: String): DeviceAuthorizationResult {
        val form = FormEncodingBuilder()
            .add("client_id", options.clientId)
            .add("client_secret", options.clientSecret)
            .add("data", data)
        val request = Request.Builder(uri = delegate.deviceAuthorizationEndpoint(),
            form = form).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                return DeviceAuthorizationResult.parse(response.body)
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.optString("error_description"),
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

    @Suppress("unused")
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
    @VisibleForTesting
    internal fun shouldRetry(
        retriesAttempted: Int,
        statusCode: Int? = null,
        throwable: Throwable? = null,
        maxDelay: Long? = null
    ): Boolean {
        if (retriesAttempted >= maxRetries) {
            return false
        }
        if (statusCode in 400..499 || throwable is IOException) {
            // Exponential back-off.
            val sleepMillis = min(
                (retriesAttempted.toDouble() + 1).pow(2.0).toLong() * initialTimeoutMs,
                maxDelay ?: maxDelayForRetry
            )
            Thread.sleep(sleepMillis)
            return true
        }
        return false
    }

    fun handleMe() : MeResponse {
        val header = Headers()
            .add("authorization", buildAuthorization())

        val request = Request.Builder(
            uri = delegate.meEndpoint(),
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
                    description = body.optString("resultMessage"),
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
            uri = delegate.revocationEndpoint(),
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
                    description = body.optString("error_description"),
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
            uri = delegate.introspectionEndpoint(),
            form = form
        ).build()
        val response = client.newCall(request)
        when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                return IntrospectResponse.parse(response.body)
            }
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_BAD_REQUEST -> {
                val body = JSONObject(response.body)
                throw BaseException.UnAuthenticatedException(
                    error = body.get("error").toString(),
                    description = body.optString("error_description"),
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


