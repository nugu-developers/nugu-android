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

import android.app.Activity
import androidx.annotation.Keep

/**
 * Authentication interface for the NUGU oauth
 */
interface NuguOAuthInterface {
    @Keep
    @Suppress("unused")
    enum class THEME { DARK, LIGHT }

    /**
     * Checking the authorization status
     * @return true is authorized, otherwise false
     */
    fun isLogin(): Boolean

    /**
     * Checking the authorization status
     * @return true is authorized, otherwise false
     */
    fun isTidLogin(): Boolean

    /**
     * Checking the authorization status
     * @return true is authorized, otherwise false
     */
    fun isAnonymouslyLogin(): Boolean

    /**
     * Set the authorization options
     * @param options is [NuguOAuthOptions]
     */
    fun setOptions(options: NuguOAuthOptions)

    /**
     * Get the authorization options
     */
    fun getOptions() : NuguOAuthOptions?

    /**
     * Immediately revoke the authorization.
     */
    fun revoke(listener: OnRevokeListener)

    /**
     * Start the login with tid
     * @param activity The activity making the call.
     * @param listener Listener to receive result.
     * @param data Constructs any extra parameters necessary to include in the request uri for the client authentication.
     * @param theme Optional custom theme.
     */
    fun loginWithTid(
        activity: Activity,
        listener: OnLoginListener,
        data: Map<String, String> = mapOf(),
        theme: THEME = THEME.LIGHT
    )

    /**
     * Start the account info with Tid.
     * @param activity The activity making the call.
     * @param listener Listener to receive result.
     * @param data Constructs any extra parameters necessary to include in the request uri for the client authentication.
     * @param theme Optional custom theme.
     */
    fun accountWithTid(
        activity: Activity,
        listener: OnAccountListener,
        data: Map<String, String> = mapOf(),
        theme: THEME = THEME.LIGHT
    )

    /**
     * Start anonymous login.
     * @param listener Listener to receive result.
     */
    fun loginAnonymously(listener: OnLoginListener)

    /**
     * Refresh Token with tid.
     * @param refreshToken refresh Token
     * @param listener Listener to receive result.
     * providers will return a "Refresh Token" when you sign-in initially.
     * When our session expires, we can exchange the refresh token to get new auth tokens.
     * > Auth tokens are not the same as a Refresh token
     */
    fun loginSilentlyWithTid(refreshToken: String, listener: OnLoginListener)

    /**
     * Start a login without browser. Only tid
     * @param code authCode
     * @param listener Listener to receive result.
     */
    fun loginWithAuthenticationCode(code: String, listener: OnLoginListener)

    /**
     * Request me api
     * @param listener Listener to receive result.
     */
    fun requestMe(listener: OnMeResponseListener)

    /**
     * Request introspect
     * The introspect specified by [https://tools.ietf.org/html/rfc7662#section-2.1], OAuth 2.0 Token Introspection
     * @param listener Listener to receive result.
     */
    fun introspect(listener: OnIntrospectResponseListener)

    /**
     * On account actions listener
     */
    interface OnAccountListener : OnLoginListener
    /**
     * On login actions listener
     */
    interface OnLoginListener {
        /**
         * Listener called when a login completes successfully.
         */
        fun onSuccess(credentials: Credentials)

        /**
         * Listener called when a login fails
         * @param error the NuguOAuthError
         */
        fun onError(error: NuguOAuthError)
    }

    /**
     * On deviceAuthorization actions listener
     */
    interface OnDeviceAuthorizationListener {
        /**
         * Listener called when a deviceAuthorization completes successfully.
         */
        fun onSuccess(result: DeviceAuthorizationResult)

        /**
         * Listener called when a deviceAuthorization fails
         * @param error the NuguOAuthError
         */
        fun onError(error: NuguOAuthError)
    }
    /**
     * On Revoke actions listener
     */
    interface OnRevokeListener {
        /**
         * Listener called when a deviceAuthorization completes successfully.
         */
        fun onSuccess()

        /**
         * Listener called when a deviceAuthorization fails
         * @param error the NuguOAuthError
         */
        fun onError(error: NuguOAuthError)
    }

    /**
     * On [requestMe] actions listener
     */
    interface OnMeResponseListener {
        /**
         * Listener called when a request completes successfully.
         */
        fun onSuccess(response: MeResponse)

        /**
         * Listener called when a request fails
         * @param error the NuguOAuthError
         */
        fun onError(error: NuguOAuthError)
    }

    /**
     * On [introspect] actions listener
     */
    interface OnIntrospectResponseListener {
        /**
         * Listener called when a request completes successfully.
         */
        fun onSuccess(response: IntrospectResponse)

        /**
         * Listener called when a request fails
         * @param error the NuguOAuthError
         */
        fun onError(error: NuguOAuthError)
    }
    /**
     * Login with device_code to issue tokens
     * @param code The short-lived code that is used by the device when polling for a session token.
     * @param listener Listener to receive result.
     */
    fun loginWithDeviceCode(deviceCode: String, listener: OnLoginListener)

    /**
     * Start device authorization by requesting a pair of verification codes from the authorization service.
     * @param data custom values
     * @param listener Listener to receive result.
     */
    fun startDeviceAuthorization(data: String, listener: OnDeviceAuthorizationListener)

    /**
     * addAuthStateListener adds an [AuthStateListener] on the given was changed
     * @param listener the listener that added
     */
    fun addAuthStateListener(listener: AuthStateListener)

    /**
     * Removes an [AuthStateListener]
     * @param listener the listener that removed
     */
    fun removeAuthStateListener(listener: AuthStateListener)
}