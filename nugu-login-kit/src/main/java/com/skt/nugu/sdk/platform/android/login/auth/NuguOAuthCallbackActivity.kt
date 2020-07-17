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
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import java.net.CookieManager

/**
 * Getting an authentication result as callback from an Activity
 */
class NuguOAuthCallbackActivity : Activity() {
    /** Get NuguOAuth instance **/
    val auth by lazy { NuguOAuth.getClient() }

    /**
     * Called when the activity is starting.
     * @see [Activity.onCreate]}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.isLogin()) {
            auth.setResult(true)
            finish()
            return
        }

        if(auth.hasAuthCodeFromIntent(intent as Intent)) {
            signIn()
            return
        }

        val intent = CustomTabsIntent.Builder()
            .enableUrlBarHiding().build()
        intent.launchUrl(this, auth.getLoginUri() )
    }

    /**
     * This is called for activities that set launchMode to "singleTop" in
     * their package, or if a client used the {@link Intent#FLAG_ACTIVITY_SINGLE_TOP}
     * flag when calling [startActivity]
     * @see [Activity.onNewIntent]}
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        when {
            auth.hasAuthCodeFromIntent(intent as Intent) -> signIn()
        }
    }

    /**
     * Perform a login.
     */
    private fun signIn() {
        auth.login(object :
            AuthStateListener {
            override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
                if (newState == AuthStateListener.State.REFRESHED /* Authentication successful */) {
                    auth.setResult(true)
                    finish()
                    return false
                } else if (newState == AuthStateListener.State.UNRECOVERABLE_ERROR /* Authentication error */) {
                    auth.setResult(false)
                    finish()
                    return false
                }
                return true
            }
        })
    }
}