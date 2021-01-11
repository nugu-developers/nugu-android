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
package com.skt.nugu.sdk.platform.android.ux.template

import android.content.Context
import android.support.annotation.MainThread
import android.view.View
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.webview.TemplateWebView

interface TemplateView {
    companion object {
        private const val TAG = "TemplateView"

        const val AUDIO_PLAYER_TEMPLATE_1 = "AudioPlayer.Template1"
        const val AUDIO_PLAYER_TEMPLATE_2 = "AudioPlayer.Template2"
        val MEDIA_TEMPLATE_TYPES = listOf(AUDIO_PLAYER_TEMPLATE_1, AUDIO_PLAYER_TEMPLATE_2)

        /**
         * @param forceToWebView : If it is true, WebView always be returned.
         * Set this 'true' if you want template as a webView.
         *
         * @return appropriate TemplateView object which is determined by templateType
         */
        fun createView(templateType: String, context: Context, forceToWebView: Boolean = false): TemplateView {
            Logger.i(TAG, "createView(). templateType: $templateType, native? ${MEDIA_TEMPLATE_TYPES.contains(templateType)}")
            return if (!forceToWebView && MEDIA_TEMPLATE_TYPES.contains(templateType)) {
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