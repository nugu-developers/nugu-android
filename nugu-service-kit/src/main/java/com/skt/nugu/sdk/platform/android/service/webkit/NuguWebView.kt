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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.annotation.Keep
import android.support.customtabs.CustomTabsIntent
import android.util.AndroidRuntimeException
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import com.skt.nugu.sdk.platform.android.service.BuildConfig


class NuguWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    JavascriptObjectReceiver.Listener {
    private val webView = WebView(context)

    interface Listener {
        fun onPageStarted(url: String?, favicon: Bitmap?)
        fun onPageFinished(url: String)
        fun onReceivedError(request: WebResourceRequest?, error: WebResourceError?)
        fun onProgressChanged(newProgress: Int)
    }

    @Keep
    enum class THEME { DARK, LIGHT }

    companion object {
        private val JS_INTERFACE_NAME = "NuguWebCommonHandler"
    }

    private fun cookies() = mutableMapOf(
        "Authorization" to authorization.toString(),
        "Poc-Id" to pocId.toString(),
        "App-Version" to appVersion.toString(),
        "Theme" to theme.name,
        "Os-Type-Code" to "MBL_AND",
        "Os-Version" to Build.VERSION.RELEASE,
        "Sdk-Version" to BuildConfig.VERSION_NAME,
        "Phone-Model-Name" to Build.MODEL,
        "Oauth-Redirect-Uri" to redirectUri.toString()
    )

    var authorization: String? = null
    var pocId: String? = null
    var appVersion: String? = null
    var theme: THEME = THEME.LIGHT
    var listener: Listener? = null
    var redirectUri : String? = null

    init {
        addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        WebViewUtils.setDefaultWebSettings(webView)
        webView.webChromeClient = DefaultWebChromeClient(context)
        webView.webViewClient = DefaultWebViewClient(context)
    }

    fun loadUrl(url: String) {
        updateCookies(url)
        webView.loadUrl(url)
    }

    override fun openExternalApp(androidScheme: String?, androidAppId: String?) {
        webView.post {
            val intent = androidScheme?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
                ?.takeIf { this.context.packageManager.resolveActivity(it, 0) != null }
                ?: androidAppId?.let {
                    this.context.packageManager.getLaunchIntentForPackage(it)
                        ?: WebViewUtils.buildGooglePlayIntent(context, it)
                }
            intent?.let {
                this.context.startActivity(it)
            }
        }
    }

    override fun openInAppBrowser(url: String) {
        webView.post {
            val intent = CustomTabsIntent.Builder()
                .enableUrlBarHiding()
                .build()
            intent.launchUrl(context, Uri.parse(url))
        }
    }

    override fun closeWindow() {
        webView.post {
            val activity = context as Activity
            activity.finish()
        }
    }

    fun setWebViewClient(client: WebViewClient) {
        webView.webViewClient = client
    }

    fun setWebChromeClient(client: WebChromeClient) {
        webView.webChromeClient = client
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        webView.addJavascriptInterface(JavascriptObjectReceiver(this), JS_INTERFACE_NAME)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webView.removeJavascriptInterface(JS_INTERFACE_NAME)
    }

    private fun updateCookies(url: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().removeAllCookies(null)
            } else {
                CookieManager.getInstance().removeAllCookie()
            }

            cookies().forEach { (key, value) ->
                val header = StringBuilder()
                header.append(key).append("=").append(value)
                Log.d("NuguWebView", "$key=$value")
                CookieManager.getInstance().setCookie(url, header.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush()
            } else {
                CookieSyncManager.getInstance().sync()
            }
        } catch (e: PackageManager.NameNotFoundException) {
        } catch (e: AndroidRuntimeException) {
        }
    }

    fun canGoBack(): Boolean {
        return webView.canGoBack()
    }

    fun goBack() {
        webView.goBack()
    }

    fun onNewIntent(intent: Intent?) {
        webView.reload()
    }

    inner class DefaultWebViewClient(private val context: Context) : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            listener?.onPageStarted(url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush()
            } else {
                CookieSyncManager.getInstance().sync()
            }
            listener?.onPageFinished(url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            listener?.onReceivedError(request, error)
        }
    }

    inner class DefaultWebChromeClient(private val context: Context) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            listener?.onProgressChanged(newProgress)
        }
    }
}
