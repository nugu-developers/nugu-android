/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.service.webkit.NuguWebView
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.service.webkit.Const

class SettingsAgreementActivity : AppCompatActivity(), NuguWebView.WindowListener, NuguWebView.DocumentListener {
    companion object {
        private const val TAG = "SettingsAgreementAct"
        private val REASON_WITHDRAWN_USER = "WITHDRAWN_USER"

        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, SettingsAgreementActivity::class.java))
        }
    }

    private val webView by lazy {
        NuguWebView(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(webView)

        with(webView) {
            authorization = NuguOAuth.getClient().getAuthorization()
            pocId = ConfigurationStore.configuration.pocId
            appVersion = BuildConfig.VERSION_NAME
            theme = NuguWebView.THEME.LIGHT
            windowListener = this@SettingsAgreementActivity
            documentListener = this@SettingsAgreementActivity
            ConfigurationStore.agreementUrl { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@agreementUrl
                }
                loadUrl(url)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        webView.onNewIntent(intent)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCloseWindow(reason: String?) {
        if(REASON_WITHDRAWN_USER == reason) {
            ClientManager.getClient().networkManager.shutdown()
            NuguOAuth.getClient().clearAuthorization()
            PreferenceHelper.credentials(this@SettingsAgreementActivity,"")
            LoginActivity.invokeActivity(this@SettingsAgreementActivity)
            finishAffinity()
        } else {
            finish()
        }
    }

    override fun onSetTitle(title: String) {
        this.title = title
    }
}