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
package com.skt.nugu.sdk.platform.android.service.webkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.File

object WebViewUtils {
    fun buildGooglePlayIntent(context: Context, packageName: String): Intent {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        if (context.packageManager.resolveActivity(appIntent, 0) == null) {
            return Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
        }
        return appIntent
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setDefaultWebSettings(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            databaseEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            val cacheDir = File(webView.context.cacheDir, "webviewcache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            setAppCachePath(cacheDir.absolutePath)
            allowFileAccess = true
            setAppCacheEnabled(true)
        }
        webView.isScrollbarFadingEnabled = true
        /*
        // for debug
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().run {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }
}