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
package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer

/**
 * TemplateHandler focused on interaction with NUGU component
 */
open class TemplateNuguHandler(
    protected val nuguProvider: TemplateRenderer.NuguClientProvider,
    override val templateInfo: TemplateHandler.TemplateInfo,
) : TemplateHandler {

    companion object {
        private const val TAG = "TemplateNuguHandler"
    }

    protected var controlListener: TemplateHandler.ClientListener? = null
        private set

    internal val displayController = object : DisplayAggregatorInterface.Controller {
        override fun controlFocus(direction: Direction): Boolean {
            return (controlListener?.controlFocus(direction) ?: false).also {
                Logger.i(TAG, "controlFocus() $direction. return $it")
            }
        }

        override fun controlScroll(direction: Direction): Boolean {
            return (controlListener?.controlScroll(direction) ?: false).also {
                Logger.i(TAG, "controlScroll() $direction. return $it")
            }
        }

        override fun getFocusedItemToken(): String? {
            return controlListener?.getFocusedItemToken().also {
                Logger.i(TAG, "getFocusedItemToken(). return $it")
            }
        }

        override fun getVisibleTokenList(): List<String>? {
            return controlListener?.getVisibleTokenList().also {
                Logger.i(TAG, "getVisibleTokenList(). return $it")
            }
        }
    }

    override fun onElementSelected(tokenId: String, postback: String?) {
        Logger.i(TAG, "onElementSelected() $tokenId, postback $postback")
        getNuguClient().getDisplay()?.setElementSelected(templateId = templateInfo.templateId, token = tokenId, postback = postback)
    }

    override fun onChipSelected(text: String) {
        Logger.i(TAG, "ohChipSelected() $text")
        getNuguClient().asrAgent?.stopRecognition()
        getNuguClient().requestTextInput(text)
    }

    override fun onNuguButtonSelected() {
        Logger.w(TAG, "onNuguButtonSelected()")
        getNuguClient().asrAgent?.startRecognition(initiator = ASRAgentInterface.Initiator.TAP)
    }

    override fun playTTS(text: String) {
        Logger.i(TAG, "onTTSRequested() $text")
        getNuguClient().requestTTS(text)
    }

    override fun setClientListener(listener: TemplateHandler.ClientListener?) {
        controlListener = listener
    }

    override fun clear() {
        Logger.i(TAG, "clear")
        controlListener = null
    }

    override fun getNuguClient(): NuguAndroidClient = nuguProvider.getNuguClient()
}