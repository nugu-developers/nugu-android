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
        private val nativeTemplateTypes = listOf(AUDIO_PLAYER_TEMPLATE_1, AUDIO_PLAYER_TEMPLATE_2)

        fun createView(templateType: String, context: Context): TemplateView {
            Log.i(TAG, "createView(). templateType: $templateType, native? ${nativeTemplateTypes.contains(templateType)}")
            return if (nativeTemplateTypes.contains(templateType)) {
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