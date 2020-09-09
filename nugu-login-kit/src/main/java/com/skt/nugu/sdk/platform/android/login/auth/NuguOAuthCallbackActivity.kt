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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.customtabs.CustomTabsIntent
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.helper.CustomTabActivityHelper

/**
 * Getting an authentication result as callback from an Activity
 */
class NuguOAuthCallbackActivity : Activity() {
    /** Get NuguOAuth instance **/
    private val TAG = "NuguOAuthCallbackActivity"
    private val auth by lazy { NuguOAuth.getClient() }
    private var action: String? = NuguOAuth.ACTION_LOGIN
    private var handler: Handler = Handler()

    private val finishRunnable = Runnable { finish() }
    /**
     * Called when the activity is starting.
     * @see [Activity.onCreate]}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        action = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_ACTION)
        when(action) {
            NuguOAuth.ACTION_LOGIN -> {
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
                CustomTabActivityHelper.openCustomTab(this, intent, auth.getLoginUri(), object :
                    CustomTabActivityHelper.CustomTabFallback {
                    override fun openUri(activity: Activity?, uri: Uri?) {
                        val signInIntent = auth.getLoginIntent()
                        startActivity(signInIntent)
                    }
                })
            }
            NuguOAuth.ACTION_ACCOUNT -> {
                val intent = CustomTabsIntent.Builder()
                    .enableUrlBarHiding().build()
                CustomTabActivityHelper.openCustomTab(this, intent, auth.getAccountInfoUri(), object :
                    CustomTabActivityHelper.CustomTabFallback {
                    override fun openUri(activity: Activity?, uri: Uri?) {
                        val signInIntent = auth.getAccountInfoIntent()
                        startActivity(signInIntent)
                    }
                })
            }
            else -> {
                Logger.d(TAG, "[onCreate] unexpected action=$action")
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handler.postDelayed(finishRunnable, 100)
    }

    /**
     * This is called for activities that set launchMode to "singleTop" in
     * their package, or if a client used the {@link Intent#FLAG_ACTIVITY_SINGLE_TOP}
     * flag when calling [startActivity]
     * @see [Activity.onNewIntent]}
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handler.removeCallbacks(finishRunnable)

        auth.hasAuthCodeFromIntent(intent as Intent)
        when(action) {
            NuguOAuth.ACTION_LOGIN -> {
                signIn()
            }
            NuguOAuth.ACTION_ACCOUNT -> {
                finish()
            }
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