package com.skt.nugu.sdk.platform.android.login.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import java.io.File

class WebViewActivity : Activity() {
    companion object {
        const val SCHEME_HTTPS = "https"
        const val SCHEME_HTTP = "http"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = RelativeLayout(this)
        layout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(layout)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = ""

        val webView = WebView(this)
        setDefaultWebSettings(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.apply {
                    val scheme = Uri.parse(this).scheme
                    val isWebScheme = SCHEME_HTTP == scheme || SCHEME_HTTPS == scheme
                    if (!isWebScheme) {
                        return try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
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
                intent.putExtra(NuguOAuthCallbackActivity.EXTRA_ERROR, Throwable(error?.description.toString()))
                setResult(NuguOAuthCallbackActivity.RESULT_WEBVIEW_FAILED, intent)
                finish()
            }
        }
        intent.data?.apply {
            webView.loadUrl(this.toString())
        }

        layout.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
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
                setResult(Activity.RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}