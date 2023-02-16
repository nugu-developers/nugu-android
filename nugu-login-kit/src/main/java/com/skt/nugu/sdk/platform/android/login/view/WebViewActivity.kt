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
package com.skt.nugu.sdk.platform.android.login.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.webkit.*
import androidx.annotation.RequiresApi
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import java.io.File

/**
 * This Activity is used as a fallback when there is no browser installed that supports
 * Chrome Custom Tabs
 */
class WebViewActivity : /**AppCompatActivity()**/
    Activity() {
    companion object {
        private const val TAG = "WebViewActivity"
        const val SCHEME_HTTPS = "https"
        const val SCHEME_HTTP = "http"
        var supportDeepLink = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_ACTION)
        val webView = try { WebView(this) } catch (e: Throwable) {
                setResult(NuguOAuthCallbackActivity.WEBVIEW_RESULT_FAILED, Intent().apply {
                    putExtra(NuguOAuthCallbackActivity.EXTRA_ERROR, e)
                })
                finish()
                return
            }
        setContentView(webView)
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)
        //supportActionBar?.setDisplayShowTitleEnabled(false)
        setDefaultWebSettings(webView)
        webView.webViewClient = object : WebViewClient() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleUri(uri)
            }

            @Deprecated("deprecated")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUri(Uri.parse(url))
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    "[onReceivedError] url=${view?.url}, requestUrl=${request?.url}, errorCode=${error?.errorCode}, description=${error?.description.toString()}"
                     else "[onReceivedError] url=${view?.url}, error=${error?.toString()}"
                Logger.e(TAG, message)
            }

            private fun handleUri(uri: Uri) : Boolean {
                val scheme = uri.scheme
                val isWebScheme = SCHEME_HTTP == scheme || SCHEME_HTTPS == scheme
                if (!isWebScheme) {
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        intent.putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, action.toString())
                        setResult(NuguOAuthCallbackActivity.WEBVIEW_RESULT_SUCCESS, intent)
                        finish()
                        true
                    } catch (e: Throwable) {
                        Logger.e(TAG, "[shouldOverrideUrlLoading] uri=$uri, cause=${e.cause}, message=${e.message}")
                        val intent = Intent()
                        intent.putExtra(NuguOAuthCallbackActivity.EXTRA_ERROR, e)
                        setResult(NuguOAuthCallbackActivity.WEBVIEW_RESULT_FAILED, intent)
                        finish()
                        false
                    }
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush()
                } else {
                    CookieSyncManager.getInstance().sync()
                }
            }
        }
        intent.data?.apply {
            webView.loadUrl(this.toString())
        }
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
//            setAppCachePath(cacheDir.absolutePath)
            allowFileAccess = true
//            setAppCacheEnabled(true)
        }
        webView.isScrollbarFadingEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().run {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}