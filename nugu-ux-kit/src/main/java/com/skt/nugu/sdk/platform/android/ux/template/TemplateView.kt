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
import android.view.View
import androidx.annotation.MainThread
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.webview.TemplateWebView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

interface TemplateView {
    companion object {
        private const val TAG = "TemplateView"

        const val AUDIO_PLAYER_TEMPLATE_1 = "AudioPlayer.Template1"
        const val AUDIO_PLAYER_TEMPLATE_2 = "AudioPlayer.Template2"
        val MEDIA_TEMPLATE_TYPES = listOf(AUDIO_PLAYER_TEMPLATE_1, AUDIO_PLAYER_TEMPLATE_2)

        enum class NuguButtonColor(val clientInfoString: String) { BLUE("blue"), WHITE("white") }

        internal val nuguButtonColorFlow = MutableSharedFlow<NuguButtonColor?>()
        var nuguButtonColor: NuguButtonColor? = null
            set(value) {
                if (field != value) {
                    field = value
                    GlobalScope.launch {
                        nuguButtonColorFlow.emit(field)
                    }
                }
            }

        /**
         * The basic rule is that SDK show close button on right top of template which is not media Type.
         * The function below determines whether template must show close button or not.
         * If you want to have your own rule. Rewrite the function.
         */
        var enableCloseButton: (String, String, DisplayAggregatorInterface.Type?) -> Boolean = { templateType, serviceID, displayType ->
            !MEDIA_TEMPLATE_TYPES.contains(templateType)
        }

        /**
         * key : TemplateType list
         * value : TemplateView Constructor
         */
        val templateConstructor: HashMap<List<String>, (String, Context) -> TemplateView> by lazy {
            HashMap<List<String>, (String, Context) -> TemplateView>().also {
                it[MEDIA_TEMPLATE_TYPES] = { templateType, context ->
                    DisplayAudioPlayer(templateType, context)
                }
            }
        }

        /**
         * @param forceToWebView : If it is true, WebView always be returned.
         * Set this 'true' if you want template as a webView.
         *
         * @return appropriate TemplateView object which is determined by templateType
         */
        fun createView(templateType: String, context: Context, forceToWebView: Boolean = false): TemplateView {
            Logger.i(TAG, "createView(). templateType: $templateType, native? ${MEDIA_TEMPLATE_TYPES.contains(templateType)}")

            if (!forceToWebView) {
                templateConstructor.keys.find { it.contains(templateType) }?.let { key ->
                    templateConstructor[key]?.invoke(templateType, context)?.run {
                        this.asView().id = R.id.template_view
                        return this
                    }
                }
            }

            return TemplateWebView(context).apply { id = R.id.template_view }
        }
    }

    var templateHandler: TemplateHandler?

    fun setServerUrl(url: String) {
        //If your TemplateView use server connect and url can be changed use this interface to make user change it.
    }

    /**
     *  @param templateContent contains all template items to be rendered.
     */
    @MainThread
    fun load(
        templateContent: String,
        deviceTypeCode: String,
        dialogRequestId: String,
        onLoadingComplete: (() -> Unit)? = null,
        onLoadingFail: ((String?) -> Unit)? = null,
        disableCloseButton : Boolean,
    )

    /**
     * @param templateContent contains only template items to be updated.
     */
    @MainThread
    fun update(templateContent: String, dialogRequestedId: String)

    fun asView(): View = this as View

    fun isNuguButtonVisible(): Boolean = false

    fun getRenderInfo(): Any

    fun applyRenderInfo(renderInfo: Any)
}