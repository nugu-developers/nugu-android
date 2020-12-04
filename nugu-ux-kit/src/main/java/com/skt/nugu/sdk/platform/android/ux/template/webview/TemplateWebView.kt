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
package com.skt.nugu.sdk.platform.android.ux.template.webview

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface.State
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.BuildConfig
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.model.TemplateContext
import java.lang.ref.SoftReference
import java.net.URLEncoder

class TemplateWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attrs, defStyle), TemplateView, TemplateHandler.ClientListener {

    companion object {
        private const val TAG = "TemplateWebView"
        private const val defaultTemplateServerUrl = "http://template.sktnugu.com/view"

        private const val KEY_POST_PARAM_DEVICE_TYPE_CODE = "device_type_code"
        private const val KEY_POST_PARAM_DATA = "data"
        private const val KEY_POST_PARAM_DIALOG_REQUEST_ID = "dialog_request_id"
    }

    private var templateUrl = defaultTemplateServerUrl

    override var templateHandler: TemplateHandler? = null
        set(value) {
            value?.run {
                addJavascriptInterface(WebAppInterface(value), "Android")
                value.setClientListener(this@TemplateWebView)
            }
        }

    private var mediaDurationMs = 1L
    private var focusedItemToken: String? = null
    private var visibleTokenList: List<String>? = null
    private var lyricsVisible: Boolean = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setInitialScale(100)
        settings.apply {
            defaultTextEncodingName = "UTF-8"
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            savePassword = false
            saveFormData = false
            setSupportZoom(false)
            builtInZoomControls = false
            useWideViewPort = false
            loadWithOverviewMode = false
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        if (BuildConfig.DEBUG) {
            setWebContentsDebuggingEnabled(true)
        }

        //todo. legacy code. monitoring for while and remove if it runs okay without this code
//        settings.setAppCachePath(activity?.cacheDir?.absolutePath)
//        settings.setAppCacheEnabled(true)
//        settings.cacheMode = WebSettings.LOAD_DEFAULT
    }

    override fun setServerUrl(url: String) {
        templateUrl = url
    }

    override fun load(templateContent: String, deviceTypeCode: String, dialogRequestId: String, onLoadingComplete: (() -> Unit)?) {
        Logger.i(TAG, "load() $templateContent")

        onLoadingComplete?.let {
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    Logger.i(TAG, "progressChanged() $newProgress")
                    if (newProgress == 100) it.invoke()
                }
            }
        }

        postUrl(templateUrl, buildString {
            append(String.format("%s=%s", KEY_POST_PARAM_DEVICE_TYPE_CODE, deviceTypeCode))
            append(
                String.format(
                    "&%s=%s",
                    KEY_POST_PARAM_DATA,
                    URLEncoder.encode(templateContent, "UTF-8")
                )
            )
            append(
                String.format(
                    "&%s=%s",
                    KEY_POST_PARAM_DIALOG_REQUEST_ID,
                    URLEncoder.encode(dialogRequestId, "UTF-8")
                )
            )
        }.toByteArray())
    }

    override fun update(templateContent: String, dialogRequestedId: String, onLoadingComplete: (() -> Unit)?) {
        loadUrl(JavaScriptHelper.onDuxReceived(dialogRequestedId, templateContent))
        onLoadingComplete?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Logger.i(TAG, "onDetachedFromWindow")
        templateHandler?.clear()
    }

    /**
     * Instantiate the interface and set the context
     */
    inner class WebAppInterface constructor(handler: TemplateHandler) {
        private var weakReference = SoftReference(handler)

        private val gson = Gson()

        /**
         * Show a toast from the web page
         */
        @JavascriptInterface
        fun showToast(toast: String) {
            weakReference.get()?.run {
                showToast(toast)
            }
        }

        @JavascriptInterface
        fun close() {
            weakReference.get()?.run {
                onCloseClicked()
            }
        }

        @JavascriptInterface
        fun playerCommand(command: String, param: String) {
            Logger.d(TAG, "[JavascriptInterface] [playerCommand] command: $command, param: $param")
            weakReference.get()?.run {
                onPlayerCommand(command, param)
            }
        }

        @JavascriptInterface
        fun invokeActivity(className: String) {
            Logger.d(TAG, "[JavascriptInterface] [invokeActivity] className: $className")
            weakReference.get()?.run {
                showActivity(className)
            }
        }

        @JavascriptInterface
        fun requestTTS(text: String) {
            Logger.d(TAG, "[Javascript Interface] requestTTS : $text")
            weakReference.get()?.run {
                playTTS(text)
            }
        }

        @JavascriptInterface
        fun onElementSelected(tokenId: String) {
            Logger.d(TAG, "[Javascript Interface] onElementSelected : $tokenId")
            weakReference.get()?.run {
                onElementSelected(tokenId)
            }
        }

        @JavascriptInterface
        fun onControlResult(action: String, result: String) {
            Logger.i(TAG, "[onControlResult] action: $action, result: $result")

            weakReference.get()?.run {
                onControlResult(action, result)
            }
        }

        @JavascriptInterface
        fun onContextChanged(context: String) {
            Logger.i(TAG, "[onContextChanged] context: $context")

            weakReference.get()?.run {
                onContextChanged(context)
            }

            val templateContext = gson.fromJson<TemplateContext>(context, TemplateContext::class.java)
            templateContext.focusedItemToken?.let { focusedItemToken = it }
            templateContext.visibleTokenList?.let { visibleTokenList = it }
            templateContext.lyricsVisible?.let { lyricsVisible = it }
        }

        @JavascriptInterface
        fun onNuguButtonSelected() {
            Logger.i(TAG, "[onNuguButtonSelected] ${weakReference.get()}")
            weakReference.get()?.run { onNuguButtonSelected() }
        }

        @JavascriptInterface
        fun onChipSelected(text: String) {
            Logger.i(TAG, "[onChipSelected] text : $text")
            weakReference.get()?.run { onChipSelected(text) }
        }
    }

    override fun onMediaStateChanged(activity: AudioPlayerAgentInterface.State, currentTimeMs: Long, currentProgress: Float) {
        when (activity) {
            State.IDLE -> loadUrl(JavaScriptHelper.onPlayStopped())
            State.PLAYING -> {
                loadUrl(JavaScriptHelper.setCurrentTime(currentTimeMs))
                loadUrl(JavaScriptHelper.setProgress(currentProgress))
                loadUrl(JavaScriptHelper.onPlayStarted())
                loadUrl(JavaScriptHelper.setEndTime(mediaDurationMs))
            }
            State.STOPPED -> loadUrl(JavaScriptHelper.onPlayStopped())
            State.PAUSED -> loadUrl(JavaScriptHelper.onPlayPaused())
            State.FINISHED -> {
                loadUrl(JavaScriptHelper.setCurrentTime(mediaDurationMs))
                loadUrl(JavaScriptHelper.setProgress(100f))
                loadUrl(JavaScriptHelper.onPlayEnd())
            }
        }
    }

    override fun onMediaDurationRetrieved(durationMs: Long) {
        mediaDurationMs = durationMs
    }

    override fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {
        loadUrl(JavaScriptHelper.setProgress(progress))
    }

    override fun controlFocus(direction: Direction): Boolean {
        loadUrl(JavaScriptHelper.controlFocus(direction))
        return true
    }

    override fun controlScroll(direction: Direction): Boolean {
        loadUrl(JavaScriptHelper.controlScroll(direction))
        return true
    }

    override fun getFocusedItemToken(): String? {
        return focusedItemToken
    }

    override fun getVisibleTokenList(): List<String>? {
        return visibleTokenList
    }

}