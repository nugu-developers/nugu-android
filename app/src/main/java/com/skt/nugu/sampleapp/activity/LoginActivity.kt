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
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.activity.main.MainActivity
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.client.toResId
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.platform.android.login.auth.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguSnackbar

/**
 * Activity to demonstrate nugu authentication using a nugu-login-kit
 */
class LoginActivity : AppCompatActivity(), ClientManager.Observer {
    companion object {
        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
        private const val TAG = "LoginActivity"
    }
    private var isSdkInitialized = false

    private val btnLoginTid: Button by lazy {
        findViewById<Button>(R.id.btn_tid_login)
    }
    private val btnLoginAnonymous: Button by lazy {
        findViewById<Button>(R.id.btn_anonymous_login)
    }

    /**
     * Get the authClient.
     */
    private val authClient by lazy {
        NuguOAuth.getClient()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        /** Add observer When initialized, [onInitialized] is called **/
        ClientManager.observer = this

        btnLoginTid.setOnClickListener {
            startTidLogin()
        }
        btnLoginAnonymous.setOnClickListener {
            startAnonymouslyLogin()
        }

        /* If you need to update deviceUniqueId again, see sample code below.
        *     val newOptions = NuguOAuthOptions.Builder()
        *         .deviceUniqueId("{deviceSerialNumber} or {userId}")
        *         .build()
        *     NuguOAuth.getClient().setOptions(newOptions)
        */
    }

    override fun onDestroy() {
        super.onDestroy()
        /** remove observer **/
        ClientManager.observer = null
    }

    override fun onInitialized() {
        isSdkInitialized = true
    }

    /**
     * Start the login with tid
     **/
    private fun startTidLogin() {
        if (!isSdkInitialized) {
            Toast.makeText(this, R.string.sdk_not_initialized, Toast.LENGTH_LONG).show()
            return
        }
        // Restore credentials from local storage
        // Important : Inside NuguOAuth, credentials only exist in memory.
        val storedCredentials = PreferenceHelper.credentials(this@LoginActivity)
        authClient.setCredentials(storedCredentials)

        if(!authClient.isTidLogin()) {
            authClient.loginWithTid(
                activity = this,
                listener = object : NuguOAuthInterface.OnLoginListener {
                    // Successfully login
                    override fun onSuccess(credentials: Credentials) {
                        // Save the [credentials] to local storage.
                        // Important : Inside NuguOAuth, credentials only exist in memory.
                        PreferenceHelper.credentials(this@LoginActivity, credentials.toString())
                        // successful, calls IntroActivity.
                        startIntroActivity()
                    }

                    //  Login failed
                    override fun onError(error: NuguOAuthError) {
                        handleOAuthError(error)
                    }
                })
            return
        }

        if(!authClient.isExpired()) {
            startMainActivity()
            return
        }
        val refreshToken = authClient.getRefreshToken()
        authClient.loginSilentlyWithTid(refreshToken, object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
                // save credentials
                PreferenceHelper.credentials(this@LoginActivity, credentials.toString())
                // successful, calls MainActivity.
                startMainActivity()
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    /**
     * Start anonymous login.
     **/
    fun startAnonymouslyLogin() {
        if(!isSdkInitialized) {
            Toast.makeText(this, R.string.sdk_not_initialized, Toast.LENGTH_LONG).show()
            return
        }

        // Restore credentials from local storage
        // Important : Inside NuguOAuth, credentials only exist in memory.
        val storedCredentials = PreferenceHelper.credentials(this@LoginActivity)
        authClient.setCredentials(storedCredentials)

        if(authClient.isAnonymouslyLogin() && !authClient.isExpired()) {
            startMainActivity()
            return
        }

        authClient.loginAnonymously(object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
                // save credentials
                PreferenceHelper.credentials(this@LoginActivity, credentials.toString())
                // successful, calls MainActivity.
                startMainActivity()
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    /**
     * Handles failed OAuth attempts.
     * The response errors return a description as defined in the spec: [https://developers-doc.nugu.co.kr/nugu-sdk/authentication]
     */
    private fun handleOAuthError(error: NuguOAuthError) {
        Log.e(TAG, "An unexpected error has occurred. " +
                "Please check the logs for details\n" +
                "$error")
        if(error.error != NuguOAuthError.NETWORK_ERROR &&
            error.error != NuguOAuthError.INITIALIZE_ERROR) {
            // After removing the credentials, it is recommended to renew the token via loginByWebbrowser
            PreferenceHelper.credentials(this@LoginActivity, "")
        }
        NuguSnackbar.with(findViewById(R.id.baseLayout))
            .message(error.toResId())
            .show()
    }

    /**
     * Start main activity
     * @param wakeupAction true indicate wakeup start, otherwise false
     **/
    private fun startMainActivity(wakeupAction: Boolean = false) {
        runOnUiThread {
            MainActivity.invokeActivity(this@LoginActivity, wakeupAction)
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
            GuideActivity.invokeActivity(this@LoginActivity, ClientManager.deviceUniqueId(this))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == GuideActivity.requestCode) {
            startMainActivity(resultCode == RESULT_FIRST_USER)
        }
    }
}