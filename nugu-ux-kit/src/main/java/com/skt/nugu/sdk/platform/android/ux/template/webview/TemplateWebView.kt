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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.MotionEvent
import android.webkit.*
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface.State
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.BuildConfig
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.isSupportFocusedItemToken
import com.skt.nugu.sdk.platform.android.ux.template.isSupportVisibleTokenList
import com.skt.nugu.sdk.platform.android.ux.template.model.ClientInfo
import com.skt.nugu.sdk.platform.android.ux.template.model.TemplateContext
import com.skt.nugu.sdk.platform.android.ux.template.presenter.EmptyLyricsPresenter
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference
import java.net.URLEncoder
import java.util.*
import kotlin.concurrent.fixedRateTimer

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
class TemplateWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : WebView(context, attrs, defStyle),
    TemplateView,
    TemplateHandler.ClientListener,
    ThemeManagerInterface.ThemeListener {

    companion object {
        private const val TAG = "TemplateWebView"
        private const val defaultTemplateServerUrl = "http://template.sktnugu.com/view"

        private const val KEY_POST_PARAM_DEVICE_TYPE_CODE = "device_type_code"
        private const val KEY_POST_PARAM_DATA = "data"
        private const val KEY_POST_PARAM_DIALOG_REQUEST_ID = "dialog_request_id"
    }

    private val gson = Gson()
    private val clientInfo = ClientInfo()

    private var templateUrl = defaultTemplateServerUrl

    private var nuguButtonColorJob: Job? = null

    @VisibleForTesting
    internal lateinit var webinterface: WebAppInterface

    override var templateHandler: TemplateHandler? = null
        set(value) {
            field = value
            value?.run {
                WebAppInterface(value).apply {
                    webinterface = this
                    addJavascriptInterface(this, "Android")
                }

                value.setClientListener(this@TemplateWebView)
                this.getNuguClient()?.run {
                    audioPlayerAgent?.setLyricsPresenter(lyricPresenter)
                    themeManager.addListener(this@TemplateWebView)
                    clientInfo.theme = themeToString(themeManager.theme, resources.configuration)
                }

                nuguButtonColorJob = CoroutineScope(Dispatchers.Main).launch {
                    TemplateView.nuguButtonColorFlow.asSharedFlow().collect {
                        clientInfo.buttonColor = it?.clientInfoString ?: ""
                        Logger.d(TAG, "Nugu Button Color changed to  ${clientInfo.buttonColor}")
                        callJSFunction(JavaScriptHelper.updateClientInfo(gson.toJson(clientInfo)))
                    }
                }
            }
        }

    @VisibleForTesting
    internal val lyricPresenter = object : LyricsPresenter {
        override fun getVisibility(): Boolean {
            return lyricsVisible
        }

        override fun show(): Boolean {
            callJSFunction(JavaScriptHelper.showLyrics())
            return true
        }

        override fun hide(): Boolean {
            callJSFunction(JavaScriptHelper.hideLyrics())
            return true
        }

        override fun controlPage(direction: Direction): Boolean {
            callJSFunction(JavaScriptHelper.controlScroll(direction))
            return true
        }
    }

    private var mediaDurationMs = 1L
    private var mediaCurrentTimeMs = 1L
    private var mediaPlaying = false
    private var focusedItemToken: String? = null
    private var visibleTokenList: List<String>? = null
    private var lyricsVisible: Boolean = false

    private var notifyUserInteractionTimer: Timer? = null
    private var onLoadingComplete: (() -> Unit)? = null
    private var isSupportVisibleOrFocusedToken: Boolean = false

    data class RenderInfo(val lyricShowing: Boolean)

    init {
        isSaveEnabled = true
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
            useWideViewPort = true
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

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startNotifyDisplayInteraction()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopNotifyDisplayInteraction()
            }
            false
        }
    }

    internal class SavedStates(
        superState: Parcelable,
        var durationMs: Long,
        var currentTimeMs: Long,
        var mediaPlaying: Int,
    ) :
        AbsSavedState(superState) {

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            super.writeToParcel(dest, flags)
            dest?.writeLong(durationMs)
            dest?.writeLong(currentTimeMs)
            dest?.writeInt(mediaPlaying)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        super.onSaveInstanceState()
        return SavedStates(super.onSaveInstanceState() ?: Bundle.EMPTY,
            mediaDurationMs,
            mediaCurrentTimeMs,
            if (mediaPlaying) 1 else 0)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)

        (state as? SavedStates)?.let { savedState ->
            mediaDurationMs = savedState.durationMs
            mediaCurrentTimeMs = savedState.currentTimeMs
            mediaPlaying = savedState.mediaPlaying == 1
        }
    }

    override fun setServerUrl(url: String) {
        templateUrl = url
    }

    override fun load(
        templateContent: String,
        deviceTypeCode: String,
        dialogRequestId: String,
        onLoadingComplete: (() -> Unit)?,
        onLoadingFail: ((String?) -> Unit)?,
        disableCloseButton: Boolean,
    ) {
        clientInfo.buttonColor = TemplateView.nuguButtonColor?.clientInfoString ?: ""
        Logger.d(TAG, "load() currentTheme $clientInfo")

        this.onLoadingComplete = onLoadingComplete
        isSupportVisibleOrFocusedToken = isSupportFocusedItemToken(templateContent) || isSupportVisibleTokenList(templateContent)

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Logger.d(TAG, "progressChanged() $newProgress, isSupportVisibleOrFocusToken $isSupportVisibleOrFocusedToken ")
                if (newProgress == 100 && !isSupportVisibleOrFocusedToken) {
                    onLoadingComplete()
                }
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Logger.d(TAG, "onReceivedError() ${templateHandler?.templateInfo?.templateId}, $errorCode, $description")

                onLoadingFail?.invoke(description)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                Logger.d(TAG, "onReceivedError() ${templateHandler?.templateInfo?.templateId}," +
                        " ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.description else error?.toString()}")
                onLoadingFail?.invoke(error.toString())
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Logger.d(TAG,
                    "onReceivedHttpError() ${templateHandler?.templateInfo?.templateId}, ${errorResponse.toString()}")
                onLoadingFail?.invoke(errorResponse.toString())
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                super.onReceivedSslError(view, handler, error)
                Logger.d(TAG, "onReceivedSslError() ${templateHandler?.templateInfo?.templateId}, ${error.toString()}")
                onLoadingFail?.invoke(error.toString())
            }
        }

        clientInfo.disableCloseButton = disableCloseButton

        postUrl(templateUrl, buildString {
            append(String.format("%s=%s", KEY_POST_PARAM_DEVICE_TYPE_CODE, deviceTypeCode))
            append(String.format("&%s=%s", KEY_POST_PARAM_DATA, URLEncoder.encode(templateContent, "UTF-8")))
            append(String.format("&%s=%s", KEY_POST_PARAM_DIALOG_REQUEST_ID, URLEncoder.encode(dialogRequestId, "UTF-8")))
            append(
                String.format(
                    "&%s=%s",
                    "client_info",
                    URLEncoder.encode(gson.toJson(clientInfo), "UTF-8")
                )
            )
        }.toByteArray())
    }

    override fun update(templateContent: String, dialogRequestedId: String) {
        Logger.d(TAG, "update() $templateContent")

        isSupportVisibleOrFocusedToken = isSupportFocusedItemToken(templateContent) || isSupportVisibleTokenList(templateContent)

        if (TemplateView.MEDIA_TEMPLATE_TYPES.contains(templateHandler?.templateInfo?.templateType)) {
            callJSFunction(JavaScriptHelper.updatePlayerMetadata(templateContent))
        } else {
            callJSFunction(JavaScriptHelper.updateDisplay(templateContent))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        newConfig ?: return

        templateHandler?.getNuguClient()?.themeManager?.run {
            themeToString(theme, newConfig).let { newTheme ->
                if (clientInfo.theme != newTheme) {
                    Logger.d(TAG, "onConfigurationChanged. and theme changed to $newTheme")
                    clientInfo.theme = newTheme
                    callJSFunction(JavaScriptHelper.updateClientInfo(gson.toJson(clientInfo)))
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Logger.d(TAG, "onDetachedFromWindow")

        templateHandler?.getNuguClient()?.run {
            if (audioPlayerAgent?.lyricsPresenter == lyricPresenter) {
                audioPlayerAgent?.setLyricsPresenter(EmptyLyricsPresenter)
            }

            themeManager.removeListener(this@TemplateWebView)
            nuguButtonColorJob?.cancel()
        }
    }

    override fun onThemeChange(theme: ThemeManagerInterface.THEME) {
        Logger.d(TAG, "onThemeChange to $theme")
        clientInfo.theme = themeToString(theme, resources.configuration)
        callJSFunction(JavaScriptHelper.updateClientInfo(gson.toJson(clientInfo)))
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
        fun closeAll() {
            weakReference.get()?.run {
                onCloseAllClicked()
            }
        }

        @JavascriptInterface
        fun playerCommand(command: String, param: String) {
            Logger.d(TAG, "[JavascriptInterface] [playerCommand] command: $command, param: $param")
            weakReference.get()?.run {
                onPlayerCommand(PlayerCommand.from(command), param)
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
        fun onButtonEvent(eventType: String, data: String) {
            Logger.d(TAG, "[Javascript Interface] onButtonEvent : $eventType $data")
            ButtonEvent.get(eventType)?.handle(weakReference.get() ?: return, data)
        }

        @JavascriptInterface
        fun onControlResult(action: String, result: String) {
            Logger.d(TAG, "[onControlResult] action: $action, result: $result")

            weakReference.get()?.run {
                onControlResult(action, result)
            }
        }

        @JavascriptInterface
        fun onContextChanged(context: String) {
            Logger.d(TAG, "[onContextChanged] isSupportVisibleOrFocusToken $isSupportVisibleOrFocusedToken \n context: $context")

            if (isSupportVisibleOrFocusedToken) {
                onLoadingComplete()
            }

            weakReference.get()?.run {
                onContextChanged(context)
            }

            val templateContext = runCatching { gson.fromJson(context, TemplateContext::class.java) }.getOrNull()
            templateContext?.focusedItemToken?.let { focusedItemToken = it }
            templateContext?.visibleTokenList?.let { visibleTokenList = it }
            templateContext?.lyricsVisible?.let { lyricsVisible = it }
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

    override fun onMediaStateChanged(
        activity: AudioPlayerAgentInterface.State,
        currentTimeMs: Long,
        currentProgress: Float,
        showController: Boolean,
    ) {
        mediaPlaying = activity == State.PLAYING
        mediaCurrentTimeMs = currentTimeMs

        when (activity) {
            State.IDLE -> callJSFunction(JavaScriptHelper.onPlayStopped())
            State.PLAYING -> {
                callJSFunction(JavaScriptHelper.setCurrentTime(currentTimeMs))
                callJSFunction(JavaScriptHelper.setProgress(currentProgress))
                callJSFunction(JavaScriptHelper.onPlayStarted())
                callJSFunction(JavaScriptHelper.setEndTime(mediaDurationMs))
            }
            State.STOPPED -> callJSFunction(JavaScriptHelper.onPlayStopped())
            State.PAUSED -> callJSFunction(JavaScriptHelper.onPlayPaused(showController))
            State.FINISHED -> {
                callJSFunction(JavaScriptHelper.setCurrentTime(mediaDurationMs))
                callJSFunction(JavaScriptHelper.setProgress(100f))
                callJSFunction(JavaScriptHelper.onPlayEnd())
            }
        }
    }

    override fun onMediaDurationRetrieved(durationMs: Long) {
        mediaDurationMs = durationMs
    }

    override fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {
        callJSFunction(JavaScriptHelper.setProgress(progress))
        callJSFunction(JavaScriptHelper.setCurrentTime(currentTimeMs))

        mediaCurrentTimeMs = currentTimeMs
    }

    override fun controlFocus(direction: Direction): Boolean {
        callJSFunction(JavaScriptHelper.controlFocus(direction))
        return true
    }

    override fun controlScroll(direction: Direction): Boolean {
        callJSFunction(JavaScriptHelper.controlScroll(direction))
        return true
    }

    override fun getFocusedItemToken(): String? {
        return focusedItemToken
    }

    override fun getVisibleTokenList(): List<String>? {
        return visibleTokenList
    }

    @VisibleForTesting
    internal fun onLoadingComplete() {
        onLoadingComplete?.invoke()
        onLoadingComplete = null

        if (TemplateView.MEDIA_TEMPLATE_TYPES.contains(templateHandler?.templateInfo?.templateType)) {
            callJSFunction(JavaScriptHelper.setCurrentTime(mediaCurrentTimeMs))
            callJSFunction(JavaScriptHelper.setEndTime(mediaDurationMs))
            if (mediaPlaying) {
                callJSFunction(JavaScriptHelper.onPlayStarted())
            }
        }
    }

    @VisibleForTesting
    internal fun startNotifyDisplayInteraction() {
        fun notifyDisplayInteraction(): String? {
            val handler = templateHandler ?: return "templateHandler is null"
            handler.getNuguClient()?.displayAgent?.notifyUserInteraction(handler.templateInfo.templateId)
            return null
        }

        notifyUserInteractionTimer?.cancel()
        notifyUserInteractionTimer = fixedRateTimer(initialDelay = 0, period = 1000, action = {
            notifyDisplayInteraction()?.run {
                Logger.w(TAG, "notifyDisplayInteraction() not handled. reason: $this")
            }
        })
    }

    private fun stopNotifyDisplayInteraction() {
        notifyUserInteractionTimer?.cancel()
    }

    @VisibleForTesting
    internal fun callJSFunction(script: String) {
        if (isAttachedToWindow) {
            post {
                evaluateJavascript(script, null)
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        templateHandler?.onTemplateTouched()
        return super.onInterceptTouchEvent(ev)
    }

    override fun isNuguButtonVisible(): Boolean = true

    override fun getRenderInfo() = RenderInfo(lyricPresenter.getVisibility())

    override fun applyRenderInfo(renderInfo: Any) {
        (renderInfo as? RenderInfo)?.run {
            if (lyricShowing) {
                lyricPresenter.show()
            }
        }
    }

    private fun themeToString(theme: ThemeManagerInterface.THEME, configuration: Configuration): String {
        return when (theme) {
            ThemeManagerInterface.THEME.LIGHT -> "light"
            ThemeManagerInterface.THEME.DARK -> "dark"
            ThemeManagerInterface.THEME.SYSTEM -> {
                when (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> "dark"
                    else -> "light"
                }
            }
        }
    }
}