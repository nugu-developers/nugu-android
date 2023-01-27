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
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsIntent
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.auth.*
import com.skt.nugu.sdk.platform.android.login.helper.CustomTabActivityHelper
import com.skt.nugu.sdk.platform.android.login.helper.CustomTabActivityHelper.CHROME_CUSTOM_TAB_REQUEST_CODE
import java.lang.IllegalStateException

/**
 * Getting an authentication result as callback from an Activity
 */
class NuguOAuthCallbackActivity : Activity() {
    /** Get NuguOAuth instance **/
    companion object {
        private const val TAG = "NuguOAuthCallbackActivity"
        const val EXTRA_ERROR  = "error"
        const val WEBVIEW_REQUEST_CODE = CHROME_CUSTOM_TAB_REQUEST_CODE + 1
        const val WEBVIEW_RESULT_FAILED = RESULT_FIRST_USER + 1
        const val WEBVIEW_RESULT_SUCCESS = RESULT_FIRST_USER + 2
    }
    private val auth by lazy { NuguOAuth.getClient() }
    private var action: String? = NuguOAuth.ACTION_LOGIN
    private var data: String? = null
    private var handler: Handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable {
        auth.setResult(false, NuguOAuthError(Throwable("user cancelled")))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(NuguOAuth.EXTRA_OAUTH_ACTION, action)
        outState.putString(NuguOAuth.EXTRA_OAUTH_DATA, data)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        action = savedInstanceState.getString(NuguOAuth.EXTRA_OAUTH_ACTION)
        data = savedInstanceState.getString(NuguOAuth.EXTRA_OAUTH_DATA)
    }


    /**
     * Called when the activity is starting.
     * @see [Activity.onCreate]}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!processOAuthCallback(intent)) {
            finish()
        }
    }

    @VisibleForTesting
    internal fun processOAuthCallback(intent: Intent?) : Boolean {
        action = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_ACTION)
        data = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_DATA)
        Logger.w(TAG, "[processOAuthCallback] action=$action, supportDeepLink=${WebViewActivity.supportDeepLink}")

        val isTidLogin = try {
            auth.isTidLogin()
        } catch (e: IllegalStateException) {
            auth.setResult(false, NuguOAuthError(e))
            return false
        }

        when(action) {
            NuguOAuth.ACTION_LOGIN -> {
                if (isTidLogin) {
                    Logger.d(TAG, "[processOAuthCallback] already login")
                    auth.setResult(true)
                    return false
                }

                if (auth.verifyStateFromIntent(intent)) {
                    performLogin(auth.codeFromIntent(intent))
                    return true
                }

                val fallbackRunnable = Runnable {
                    Logger.w(TAG, "[processOAuthCallback] fallback")
                    handler.removeCallbacks(finishRunnable)
                    startActivityForResult(Intent(this, WebViewActivity::class.java).apply {
                        putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, this@NuguOAuthCallbackActivity.action)
                        data = auth.getLoginUri(this@NuguOAuthCallbackActivity.data)
                    }, WEBVIEW_REQUEST_CODE)
                }

                if(!WebViewActivity.supportDeepLink) {
                    fallbackRunnable.run()
                    return true
                }
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setUrlBarHidingEnabled(true).build()
                CustomTabActivityHelper.openCustomTab(this, customTabsIntent, auth.getLoginUri(this.data), object :
                    CustomTabActivityHelper.CustomTabFallback {
                    override fun openUri(activity: Activity?, uri: Uri?) {
                        fallbackRunnable.run()
                    }
                })
            }
            NuguOAuth.ACTION_ACCOUNT -> {
                if (auth.verifyStateFromIntent(intent)) {
                    performLogin(auth.codeFromIntent(intent))
                    return true
                }
                val fallbackRunnable = Runnable {
                    Logger.w(TAG, "[processOAuthCallback] fallback")
                    handler.removeCallbacks(finishRunnable)
                    startActivityForResult(Intent(this, WebViewActivity::class.java).apply {
                        putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, this@NuguOAuthCallbackActivity.action)
                        data = auth.getAccountInfoUri(this@NuguOAuthCallbackActivity.data)
                    }, WEBVIEW_REQUEST_CODE)
                }

                if(!WebViewActivity.supportDeepLink) {
                    fallbackRunnable.run()
                    return true
                }
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setUrlBarHidingEnabled(true).build()
                CustomTabActivityHelper.openCustomTab(this, customTabsIntent, auth.getAccountInfoUri(data), object :
                    CustomTabActivityHelper.CustomTabFallback {
                    override fun openUri(activity: Activity?, uri: Uri?) {
                        fallbackRunnable.run()
                    }
                })
            }
            else -> {
                Logger.e(TAG, "[processOAuthCallback] unexpected action=$action")
                auth.setResult(false, NuguOAuthError(Throwable("unexpected action=$action")))
                return false
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Logger.d(TAG, "[onActivityResult] requestCode=$requestCode, resultCode=$resultCode")
        when(requestCode) {
            WEBVIEW_REQUEST_CODE -> {
                when(resultCode) {
                    WEBVIEW_RESULT_FAILED -> {
                        val extraError = data?.extras?.getSerializable(EXTRA_ERROR)
                        if (extraError is Throwable)
                            auth.setResult(false, NuguOAuthError(extraError))
                        else auth.setResult(false)
                        finish()
                    }
                    WEBVIEW_RESULT_SUCCESS -> {
                        if(auth.verifyStateFromIntent(data)) {
                            performLogin(auth.codeFromIntent(data))
                        } else {
                            auth.setResult(false, NuguOAuthError(Throwable("Csrf failed. data=$data")))
                            finish()
                        }
                    }
                    else -> handler.postDelayed(finishRunnable, 100L)
                }
            }
            CHROME_CUSTOM_TAB_REQUEST_CODE -> {
                handler.postDelayed(finishRunnable, 100L)
            }
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
        Logger.d(TAG, "[onNewIntent]")
        handler.removeCallbacks(finishRunnable)

        if (auth.verifyStateFromIntent(intent)) {
            performLogin(auth.codeFromIntent(intent))
        } else {
            auth.setResult(false, NuguOAuthError(Throwable("Csrf failed. data=$data")))
            finish()
        }
    }

    /**
     * Perform a login.
     */
    private fun performLogin(code : String?) {
        Logger.d(TAG, "[performLogin]")
        auth.loginInternal(
            AuthorizationRequest.AuthorizationCodeRequest(code),
            object : AuthStateListener {
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