package com.skt.nugu.sdk.platform.android.ux.template

import android.content.Context
import android.support.annotation.MainThread
import android.util.Log
import android.view.View
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.webview.TemplateWebView

interface TemplateView {
    companion object {
        private const val TAG = "TemplateView"

        const val AUDIO_PLAYER_TEMPLATE_1 = "AudioPlayer.Template1"
        const val AUDIO_PLAYER_TEMPLATE_2 = "AudioPlayer.Template2"
        val mediaTemplateTypes = listOf(AUDIO_PLAYER_TEMPLATE_1, AUDIO_PLAYER_TEMPLATE_2)

        /**
         * @param forceToWebView : If it is true, WebView always be returned.
         * Set this 'true' if you want template as a webView.
         *
         * @return appropriate TemplateView object which is determined by templateType
         */
        fun createView(templateType: String, context: Context, forceToWebView: Boolean = false): TemplateView {
            Log.i(TAG, "createView(). templateType: $templateType, native? ${mediaTemplateTypes.contains(templateType)}")
            return if (!forceToWebView && mediaTemplateTypes.contains(templateType)) {
                DisplayAudioPlayer(templateType, context)
            } else {
                TemplateWebView(context)
            }
        }
    }

    var templateHandler: TemplateHandler?

    fun setServerUrl(url: String) {}

    /**
     *  @param templateContent contains all template items to be rendered.
     */
    @MainThread
    fun load(templateContent: String, deviceTypeCode: String, dialogRequestId: String, onLoadingComplete: (() -> Unit)? = null)

    /**
     * @param templateContent contains only template items to be updated.
     */
    @MainThread
    fun update(templateContent: String, dialogRequestedId: String, onLoadingComplete: (() -> Unit)? = null)

    fun asView(): View = this as View
}