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
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.client.toResId
import com.skt.nugu.sampleapp.service.SampleAppService
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.login.auth.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguToast

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }
    private val fragment by lazy {  supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment  }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        updateAccountInfo()
    }

    private fun updateAccountInfo() {
        NuguOAuth.getClient().introspect(object : NuguOAuthInterface.OnIntrospectResponseListener {
            override fun onSuccess(response: IntrospectResponse) {
                if (response.active) {
                    if (response.username.isEmpty()) {
                        Log.i(TAG, "Anonymous logined")
                    }
                    runOnUiThread {
                        fragment.setSummary("account", response.username, getString(R.string.setting_anonymous_summary))
                        fragment.setEnabled("account", response.username.isNotEmpty())
                        fragment.setEnabled("service", response.username.isNotEmpty())
                    }
                } else {
                    Log.e(TAG, "the token is inactive. response=$response")
                    runOnUiThread {
                        NuguToast.with(applicationContext)
                            .message(R.string.device_gw_error_003)
                            .duration(NuguToast.LENGTH_SHORT)
                            .show()
                    }
                }
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    private fun revoke() {
        NuguOAuth.getClient().revoke(object : NuguOAuthInterface.OnRevokeListener {
            override fun onSuccess() {
                handleRevoke()
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    private fun launchAgreement() {
        SettingsAgreementActivity.invokeActivity(this)
    }

    private fun launchService() {
        SettingsServiceActivity.invokeActivity(this)
    }

    private fun launchPrivacy() {
        ConfigurationStore.privacyUrl { url, error ->
            error?.apply {
                Log.e(TAG, "[launchPrivacy] error=$this")
                return@privacyUrl
            }

            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                })
            } catch (e: Throwable) {
                Log.e(TAG, "[launchPrivacy] $e")
            }
        }
    }

    private fun hideFloating() {
        val isEnable = PreferenceHelper.enableFloating(this)
        if (!isEnable) {
            SampleAppService.hideFloating(this)
        }
    }

    private fun accountWithTid() {
        NuguOAuth.getClient().accountWithTid(this, object : NuguOAuthInterface.OnAccountListener {
            override fun onSuccess(credentials: Credentials) {
                // Update token
                PreferenceHelper.credentials(this@SettingsActivity, credentials.toString())
                updateAccountInfo()
            }

            override fun onError(error: NuguOAuthError) {
                handleOAuthError(error)
            }
        })
    }

    private fun handleRevoke() {
        ClientManager.getClient().networkManager.shutdown()
        NuguOAuth.getClient().clearAuthorization()
        PreferenceHelper.credentials(this@SettingsActivity, "")
        LoginActivity.invokeActivity(this@SettingsActivity)
        finishAffinity()
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
            handleRevoke()
        }
        runOnUiThread {
            NuguToast.with(this@SettingsActivity)
                .message(error.toResId())
                .duration(NuguToast.LENGTH_SHORT)
                .show()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            when (preference?.key) {
                "account" -> {
                    (activity as SettingsActivity).accountWithTid()
                }
                "service" -> {
                    (activity as SettingsActivity).launchService()
                }
                "agreement" -> {
                    (activity as SettingsActivity).launchAgreement()
                }
                "privacy" -> {
                    (activity as SettingsActivity).launchPrivacy()
                }
                "revoke" -> {
                    (activity as SettingsActivity).revoke()
                }
                "enableFloating" -> {
                    (activity as SettingsActivity).hideFloating()
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        fun setSummary(key: String, username: String, defValue: String) {
            findPreference<Preference>(key)?.let {
                it.summary = if(username.isEmpty()) defValue else username
            }
        }

        fun setEnabled(key: String, enabled: Boolean) {
            findPreference<Preference>(key)?.let {
                it.isEnabled = enabled
            }
        }
    }

}