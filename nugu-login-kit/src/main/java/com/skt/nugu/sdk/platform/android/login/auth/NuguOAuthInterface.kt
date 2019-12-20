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
     * @param listener Listener to receive result.
     * 실행결과를 수신합니다.
     */
    fun loginSilently(refreshToken : String, listener: OnLoginListener)

    /**
     * Start a login without browser. Only Type1
     * @param authCode authCode
     * @param listener Listener to receive result.
     */
    fun loginWithAuthenticationCode(authCode: String, listener: OnLoginListener)
    /**
     * Called when state changed
     * @param state changed state
     */
    interface OnLoginListener {
        /**
         * Listener called when a login completes successfully.
         */
        fun onSuccess(credentials: Credentials)

        /**
         * Listener called when a login fails
         * @param reason of failure
         */
        fun onError(reason: String)
    }

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