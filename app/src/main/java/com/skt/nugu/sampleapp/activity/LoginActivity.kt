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
import android.widget.Button
import android.widget.Toast
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.platform.android.login.auth.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguSnackbar
import java.math.BigInteger
import java.security.SecureRandom

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

    private val btnLoginAuthorizationCode: Button by lazy {
        findViewById<Button>(R.id.btn_authorization_code_login)
    }
    private val btnLoginClientCredentials: Button by lazy {
        findViewById<Button>(R.id.btn_client_credentials_login)
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
        setContentView(R.layout.activity_login)
        /** Add observer When initialized, [onInitialized] is called **/
        ClientManager.observer = this

        btnLoginAuthorizationCode.setOnClickListener {
            startAuthorizationCodeLogin()
        }
        btnLoginClientCredentials.setOnClickListener {
            startClientCredentialsLogin()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /** remove observer **/
        ClientManager.observer = null
    }

    override fun onInitialized() {
        isSdkInitialized = true
    }

    /** Type1 : authorization_code **/
    fun startAuthorizationCodeLogin() {
        if (!isSdkInitialized) {
            Toast.makeText(this, R.string.sdk_not_initialized, Toast.LENGTH_LONG).show()
            return
        }
        val storedCredentials = PreferenceHelper.credentials(this@LoginActivity)
        // serialized a credential
        authClient.setCredentials(storedCredentials)

        when {
            !authClient.isAuthorizationCodeLogin() ->
                authClient.loginByInAppBrowser(
                    activity = this,
                    listener = object : NuguOAuthInterface.OnLoginListener {
                        override fun onSuccess(credentials: Credentials) {
                            // save credentials
                            PreferenceHelper.credentials(this@LoginActivity, credentials.toString())
                            // successful, calls IntroActivity.
                            startIntroActivity()
                        }

                        override fun onError(error: NuguOAuthError) {
                            handleOAuthError(error)
                        }
                    })
            else -> {
                if(!authClient.isExpired()) {
                    startMainActivity()
                    return
                }
                val refreshToken = authClient.getRefreshToken()
                authClient.loginSilently(refreshToken, object : NuguOAuthInterface.OnLoginListener {
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
        }
    }

    /** Type2 : client_credentials **/
    fun startClientCredentialsLogin() {
        if(!isSdkInitialized) {
            Toast.makeText(this, R.string.sdk_not_initialized, Toast.LENGTH_LONG).show()
            return
        }
        val storedCredentials = PreferenceHelper.credentials(this@LoginActivity)
        // serialized a credential
        authClient.setCredentials(storedCredentials)
        if(authClient.isClientCredentialsLogin() && !authClient.isExpired()) {
            startMainActivity()
            return
        }

        authClient.login(object : NuguOAuthInterface.OnLoginListener {
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
        if(error.error == NuguOAuthError.NETWORK_ERROR) {
            NuguSnackbar.with(findViewById(R.id.baseLayout))
                .message(R.string.authorization_failure_message)
                .show()
        } else {
            // After removing the credentials, it is recommended to renew the token via loginByWebbrowser
            PreferenceHelper.credentials(this@LoginActivity, "")

            when(error.code) {
                NuguOAuthError.USER_ACCOUNT_CLOSED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_account_closed)
                        .show()
                }
                NuguOAuthError.USER_ACCOUNT_PAUSED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_account_paused)
                        .show()
                }
                NuguOAuthError.USER_DEVICE_DISCONNECTED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_device_disconnected)
                        .show()
                }
                NuguOAuthError.USER_DEVICE_UNEXPECTED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_device_unexpected)
                        .show()
                }
                else -> {
                    when(error.error) {
                        NuguOAuthError.UNAUTHORIZED -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_unauthorized)
                                .show()
                        }
                        NuguOAuthError.UNAUTHORIZED_CLIENT -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_unauthorized_client)
                                .show()
                        }
                        NuguOAuthError.INVALID_TOKEN -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_invalid_token)
                                .show()
                        }
                        NuguOAuthError.INVALID_CLIENT -> {
                            if(error.description == NuguOAuthError.FINISHED) {
                                NuguSnackbar.with(findViewById(R.id.baseLayout))
                                    .message(R.string.service_finished)
                                    .show()
                            } else {
                                NuguSnackbar.with(findViewById(R.id.baseLayout))
                                    .message(R.string.error_invalid_client)
                                    .show()
                            }
                        }
                        NuguOAuthError.ACCESS_DENIED -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_access_denied)
                                .show()
                        }
                        else -> {
                            // check detail
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.authentication_failed)
                                .show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate random unique ID
     * Change the unique ID you can identify
     *
     * example :
     * private fun getDeviceUniqueId(): String = "{deviceSerialNumber}"
     * reference :
     * https://developers-doc.nugu.co.kr/nugu-sdk/authentication
     */
    private fun getDeviceUniqueId(): String {
        // load deviceUniqueId
        var deviceUniqueId = PreferenceHelper.deviceUniqueId(this)
        if (deviceUniqueId.isBlank()) {
            // Generate random
            deviceUniqueId += BigInteger(130, SecureRandom()).toString(32) // Fix your device policy
            // save deviceUniqueId
            PreferenceHelper.deviceUniqueId(this, deviceUniqueId)
        }
        return deviceUniqueId
    }

    /**
     * Start main activity
     **/
    private fun startMainActivity() {
        runOnUiThread {
            MainActivity.invokeActivity(this@LoginActivity)
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
            IntroActivity.invokeActivity(this@LoginActivity, getString(R.string.nugu_poc_id), getDeviceUniqueId())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == IntroActivity.requestCode) {
            startMainActivity()
        }
    }
}