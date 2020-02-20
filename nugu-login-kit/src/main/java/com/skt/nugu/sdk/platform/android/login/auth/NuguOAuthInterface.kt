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

/**
 * Authentication interface for the NUGU oauth
 */
interface NuguOAuthInterface {
    /**
     * Checking the authorization status
     * @return true is authorized, otherwise false
     */
    fun isLogin(): Boolean
    /**
     * Set the authorization options
     * @param options is [NuguOAuthOptions]
     */
    fun setOptions(options: Any)
    /**
     * Immediately logout(remove) the authorization.
     * @param listener Listener to receive authorization state events.
     */
    fun logout()

    /**
     * Helper function to extract out AuthCode from the getIntent for login.
     * @return true is successful extract of authCode, otherwise false
     */
    fun hasAuthCodeFromIntent(intent : Any) : Boolean

    /**
     * Gets an Intent to start the Nugu login flow for startActivity
     * @return The Intent used for start the login flow.
     */
    fun getLoginIntent() : Any

    /**
     * Start a login with browser. Only Type1
     * @param activity The activity making the call.
     * @param listener Listener to receive result.
     */
    fun loginByWebbrowser(activity: Activity, listener : OnLoginListener)

    /**
     * Start a login with credentials. Only Type2
     * @param activity The activity making the call.
     * @param listener Listener to receive result.
     */
    fun login(listener: OnLoginListener)

    /**
     * Refresh Token from Type1.
     * @param refreshToken refresh Token
     * @param listener Listener to receive result.
     * providers will return a "Refresh Token" when you sign-in initially.
     * When our session expires, we can exchange the refresh token to get new auth tokens.
     * > Auth tokens are not the same as a Refresh token
     */
    fun loginSilently(refreshToken : String, listener: OnLoginListener)

    /**
     * Start a login without browser. Only Type1
     * @param code authCode
     * @param listener Listener to receive result.
     */
    fun loginWithAuthenticationCode(code: String, listener: OnLoginListener)

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
     * Login with device_code to issue tokens
     * @param code The short-lived code that is used by the device when polling for a session token.
     * @param listener Listener to receive result.
     */
    fun loginWithDeviceCode(code: String, listener: OnLoginListener)

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