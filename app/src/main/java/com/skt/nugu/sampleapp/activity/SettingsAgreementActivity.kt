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
import android.support.v7.app.AppCompatActivity
import android.content.Intent
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sdk.platform.android.service.webkit.NuguWebView
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.service.webkit.Const

class SettingsAgreementActivity : AppCompatActivity(), NuguWebView.WindowListener, NuguWebView.DocumentListener {
    companion object {
        private const val TAG = "SettingsAgreementActivity"
        private val REASON_WITHDRAWN_USER = "WITHDRAWN_USER"
    }
    private val nuguPocId : String by lazy {
        getString(R.string.nugu_poc_id)
    }

    private val webView: NuguWebView by lazy {
        findViewById<NuguWebView>(R.id.webView)
    }

    private fun checkPocId() {
        if(nuguPocId == "YOUR_POC_ID_HERE") {
            throw IllegalArgumentException(
                "You must enter poc_id[YOUR_POC_ID_HERE].\n" +
                "Available after POC registration, please check below\n" +
                "@see [https://developers.nugu.co.kr/#/sdk/pocList]."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        checkPocId()

        with(webView) {
            authorization = NuguOAuth.getClient().getAuthorization()
            pocId = nuguPocId
            appVersion = BuildConfig.VERSION_NAME
            theme = NuguWebView.THEME.LIGHT
            windowListener = this@SettingsAgreementActivity
            documentListener = this@SettingsAgreementActivity
            loadUrl(Const.AGREEMENT_URL)
        }
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
            ClientManager.getClient().disconnect()
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