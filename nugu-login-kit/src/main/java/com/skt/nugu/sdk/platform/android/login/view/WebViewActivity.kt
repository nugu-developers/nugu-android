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
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import java.io.File

/**
 * This Activity is used as a fallback when there is no browser installed that supports
 * Chrome Custom Tabs
 */
class WebViewActivity : /**AppCompatActivity()**/
    Activity() {
    companion object {
        const val SCHEME_HTTPS = "https"
        const val SCHEME_HTTP = "http"
        var supportDeepLink = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra(NuguOAuth.EXTRA_OAUTH_ACTION)
        val webView = try { WebView(this) } catch (e: Throwable) {
                setResult(NuguOAuthCallbackActivity.RESULT_WEBVIEW_FAILED, Intent().apply {
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
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.apply {
                    val scheme = Uri.parse(this).scheme
                    val isWebScheme = SCHEME_HTTP == scheme || SCHEME_HTTPS == scheme
                    if (!isWebScheme) {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.putExtra(NuguOAuth.EXTRA_OAUTH_ACTION, action.toString())
                            setResult(NuguOAuthCallbackActivity.RESULT_WEBVIEW_SUCCESS, intent)
                            finish()
                            true
                        } catch (e: Throwable) {
                            val intent = Intent()
                            intent.putExtra(NuguOAuthCallbackActivity.EXTRA_ERROR, e)
                            setResult(NuguOAuthCallbackActivity.RESULT_WEBVIEW_FAILED, intent)
                            finish()
                            false
                        }
                    }
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                val intent = Intent()
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.putExtra(
                        NuguOAuthCallbackActivity.EXTRA_ERROR,
                        Throwable(error?.description.toString())
                    )
                } else {
                    intent.putExtra(
                        NuguOAuthCallbackActivity.EXTRA_ERROR,
                        Throwable(error?.toString())
                    )
                }
                setResult(NuguOAuthCallbackActivity.RESULT_WEBVIEW_FAILED, intent)
                finish()
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
            setAppCachePath(cacheDir.absolutePath)
            allowFileAccess = true
            setAppCacheEnabled(true)
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