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
package com.skt.nugu.sampleapp.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.skt.nugu.sdk.platform.android.login.auth.Credentials
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthInterface
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthOptions
import com.skt.nugu.sdk.platform.android.ux.widget.Snackbar
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Activity to demonstrate nugu authentication using a nugu-login-kit
 */
class LoadingActivity : AppCompatActivity(), ClientManager.Observer {
    companion object {
        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, LoadingActivity::class.java))
        }

        private const val TAG = "LoadingActivity"

        enum class GrantType { AUTHORIZATION_CODE, CLIENT_CREDENTIALS }

        private var grantType = GrantType.AUTHORIZATION_CODE
    }

    /**
     * Initializes, Creates a new authClient
     */
    private val authClient by lazy {
        // Configure Nugu OAuth Options
        val options = NuguOAuthOptions.Builder()
            .clientSecret("YOUR_CLIENT_SECRET_HERE")
            .deviceUniqueId(getDeviceUniqueId())
            .build()
        NuguOAuth.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
    }

    override fun onResume() {
        super.onResume()
        /** Add observer When initialized, [onInitialized] is called **/
        ClientManager.observer = this
    }

    override fun onDestroy() {
        /** remove observer **/
        ClientManager.observer = null
        super.onDestroy()
    }

    override fun onInitialized() {
        /** authorization_code **/
        if (grantType == GrantType.AUTHORIZATION_CODE) {
            // load credentials
            val storedCredentials = PreferenceHelper.credentials(this@LoadingActivity)
            // serialized a credential, extract of refreshToken
            val refreshToken = Credentials.parse(storedCredentials).refreshToken

            when {
                refreshToken.isEmpty() ->
                    authClient.loginByWebbrowser(activity = this, listener = object : NuguOAuthInterface.OnLoginListener {
                        override fun onSuccess(credentials: Credentials) {
                            // save credentials
                            PreferenceHelper.credentials(this@LoadingActivity, credentials.toString())
                            // successful, calls IntroActivity.
                            startIntroActivity()
                        }

                        override fun onError(reason: String) {
                            Log.e(TAG, reason)
                            // please try again in a few minutes
                            Snackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.authorization_failure_message)
                                .show()
                        }
                    })
                else -> authClient.loginSilently(refreshToken, object : NuguOAuthInterface.OnLoginListener {
                    override fun onSuccess(credentials: Credentials) {
                        // save credentials
                        PreferenceHelper.credentials(this@LoadingActivity, credentials.toString())
                        // successful, calls MainActivity.
                        startMainActivity()
                    }

                    override fun onError(reason: String) {
                        // please try again in a few minutes
                        Snackbar.with(findViewById(R.id.baseLayout))
                            .message(R.string.authorization_failure_message)
                            .show()
                        // After removing the credentials, it is recommended to renew the token via loginByWebbrowser
                        PreferenceHelper.credentials(this@LoadingActivity, "")
                    }
                })
            }
        }
        /** client_credentials **/
        else if (grantType == GrantType.CLIENT_CREDENTIALS) {
            authClient.login(object : NuguOAuthInterface.OnLoginListener {
                override fun onSuccess(credentials: Credentials) {
                    // save credentials
                    PreferenceHelper.credentials(this@LoadingActivity, credentials.toString())
                    // successful, calls MainActivity.
                    startMainActivity()
                }

                override fun onError(reason: String) {
                    // please try again in a few minutes
                    Snackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.authorization_failure_message)
                        .show()
                }
            })
        }
    }

    /**
     * Generate random unique ID
     * reference : https://developer.android.com/training/articles/user-data-ids
     */
    private fun getDeviceUniqueId(): String {
        // load deviceUniqueId
        var deviceUniqueId = PreferenceHelper.deviceUniqueId(this)
        if (deviceUniqueId.isBlank()) {
            // Generate random
            deviceUniqueId += BigInteger(130, SecureRandom()).toString(32) // Fix your device policy
        }
        // save deviceUniqueId
        PreferenceHelper.deviceUniqueId(this, deviceUniqueId)
        return deviceUniqueId
    }

    /**
     * Start main activity
     **/
    private fun startMainActivity() {
        runOnUiThread {
            MainActivity.invokeActivity(this@LoadingActivity)
            finishAffinity()
        }
    }

    /**
     * Start intro activity, If not, go to main activity.
     * You must enter poc_id[YOUR_POC_ID_HERE].
     * Available after POC registration, please check below
     * @see [https://developers.nugu.co.kr/#/sdk/pocList]
    **/
    private fun startIntroActivity() {
        runOnUiThread {
            IntroActivity.invokeActivity(this@LoadingActivity, "YOUR_POC_ID_HERE", getDeviceUniqueId())
        }
    }

}