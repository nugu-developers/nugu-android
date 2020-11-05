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
package com.skt.nugu.sdk.platform.android.login.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.customtabs.CustomTabsIntent
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.auth.AuthStateListener
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthError
import com.skt.nugu.sdk.platform.android.login.helper.CustomTabActivityHelper

/**
 * Getting an authentication result as callback from an Activity
 */
class NuguOAuthCallbackActivity : Activity() {
    /** Get NuguOAuth instance **/
    companion object {
        private val TAG = "NuguOAuthCallbackActivity"
        const val RESULT_WEBVIEW_FAILED = RESULT_FIRST_USER + 1
        const val EXTRA_ERROR  = "error"
        private val WEBVIEW_REQUEST_CODE = CustomTabActivityHelper.CHROME_CUSTOM_TAB_REQUEST_CODE + 1
        private const val finishDelayMillis = 100L
    }
    private val auth by lazy { NuguOAuth.getClient() }
    private var action: String? = NuguOAuth.ACTION_LOGIN
    private var handler: Handler = Handler()
    private val finishRunnable = Runnable {
        if(firstRequestCode == nextRequestCode) {
            finish()
        }
    }
    private var nextRequestCode = CustomTabActivityHelper.CHROME_CUSTOM_TAB_REQUEST_CODE
    private var firstRequestCode = 0
    /**
     * Called when the activity is starting.
     * @see [Activity.onCreate]}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        action = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_ACTION)
        when(action) {
            NuguOAuth.ACTION_LOGIN -> {
                if (auth.isAuthorizationCodeLogin()) {
                    auth.setResult(true)
                    finish()
                    return
                }

                if (auth.setCodeFromIntent(intent as Intent)) {
                    performLogin()
                    return
                }
                val intent = CustomTabsIntent.Builder()
                    .enableUrlBarHiding().build()
                CustomTabActivityHelper.openCustomTab(this, intent, auth.getLoginUri(), object :
                    CustomTabActivityHelper.CustomTabFallback {
                    override fun openUri(activity: Activity?, uri: Uri?) {
                        nextRequestCode = WEBVIEW_REQUEST_CODE
                        Logger.e(TAG, "[onCreate] fallback, action=$action")
                        startActivityForResult(Intent(activity, WebViewActivity::class.java).apply {
                            putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, this@NuguOAuthCallbackActivity.action)
                            data = uri
                        }, nextRequestCode)
                    }
                })
            }
            NuguOAuth.ACTION_ACCOUNT -> {
                if (auth.setCodeFromIntent(intent as Intent)) {
                    performLogin()
                    return
                }

                val intent = CustomTabsIntent.Builder()
                    .enableUrlBarHiding().build()
                CustomTabActivityHelper.openCustomTab(this, intent, auth.getAccountInfoUri(), object :
                        CustomTabActivityHelper.CustomTabFallback {
                        override fun openUri(activity: Activity?, uri: Uri?) {
                            Logger.e(TAG, "[onCreate] fallback, action=$action")
                            nextRequestCode = WEBVIEW_REQUEST_CODE
                            startActivityForResult(Intent(activity, WebViewActivity::class.java).apply {
                                putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, this@NuguOAuthCallbackActivity.action)
                                data = uri
                            }, nextRequestCode)
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
        firstRequestCode = requestCode
        handler.postDelayed(finishRunnable, finishDelayMillis)

        if(resultCode == RESULT_WEBVIEW_FAILED) {
            val error = data?.extras?.getSerializable(EXTRA_ERROR)
            if (error is Throwable) {
                auth.setResult(false, NuguOAuthError(error))
            } else auth.setResult(false)
            finish()
        }
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

        if (auth.setCodeFromIntent(intent as Intent)) {
            performLogin()
        } else {
            finish()
        }
    }

    /**
     * Perform a login.
     */
    private fun performLogin() {
        auth.login(object : AuthStateListener {
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