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

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.service.webkit.NuguWebView
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth

class SettingsServiceActivity : AppCompatActivity(), NuguWebView.WindowListener {
    companion object {
        private const val TAG = "SettingsServiceActivity"

        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, SettingsServiceActivity::class.java))
        }
    }

    /**
     * Edit your application's AndroidManifest.xml file,
     * and add the following declaration within the <SettingsServiceActivity> element.
     * <data android:host="oauth_refresh" android:scheme="@string/nugu_redirect_scheme" />
     * edit your IntentFilter in manifest
     */
    private val OAUTH_REDIRECT_URI : String by lazy {
        getString(R.string.nugu_redirect_scheme) + "://oauth_refresh"
    }

    private val webView by lazy {
        NuguWebView(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(webView)

        with(webView) {
            authorization = NuguOAuth.getClient().getAuthorization()
            deviceUniqueId = NuguOAuth.getClient().deviceUniqueId()
            pocId = ConfigurationStore.configuration.pocId
            redirectUri = OAUTH_REDIRECT_URI
            appVersion = BuildConfig.VERSION_NAME
            theme = NuguWebView.THEME.LIGHT
            windowListener = this@SettingsServiceActivity
            ConfigurationStore.serviceSettingUrl { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@serviceSettingUrl
                }
                loadUrl(url)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        webView.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCloseWindow(reason: String?) {
        finish()
    }
}