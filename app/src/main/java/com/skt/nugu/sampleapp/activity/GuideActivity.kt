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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.service.webkit.NuguWebView

/**
 * Demonstrate using nugu with webview.
 */
class GuideActivity : AppCompatActivity(), NuguWebView.WindowListener {
    companion object {
        private const val TAG = "GuideActivity"
        const val requestCode = 101
        const val REASON_WAKE_UP = "WAKE_UP"
        const val EXTRA_KEY_DEVICE_UNIQUEID = "key_device_unique_id"

        fun invokeActivity(activity: Activity, deviceUniqueId: String) {
            activity.startActivityForResult(
                Intent(activity, GuideActivity::class.java)
                    .putExtra(EXTRA_KEY_DEVICE_UNIQUEID, deviceUniqueId)
            , requestCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = NuguWebView(this)
        setContentView(webView)

        with(webView) {
            appVersion = BuildConfig.VERSION_NAME
            theme = NuguWebView.THEME.LIGHT
            windowListener = this@GuideActivity
        }
        ConfigurationStore.usageGuideUrl(intent.extras?.getString(EXTRA_KEY_DEVICE_UNIQUEID).toString()) { url, error ->
            error?.apply {
                Log.e(TAG, "[onCreate] error=$this")
                return@usageGuideUrl
            }
            webView.loadUrl(url)
        }
    }

    override fun onCloseWindow(reason: String?) {
        if (reason == REASON_WAKE_UP) {
            setResult(Activity.RESULT_FIRST_USER)
        }
        finish()
    }
}