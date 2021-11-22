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

import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand

/**
 * TemplateHandler which is used by default in ux-kit.
 * This composites TemplateAndroidHandler, TemplateMediaHandler, TemplateNuguHandler and execute one of them appropriately.
 */

open class DefaultTemplateHandler(
    protected val nuguProvider: TemplateRenderer.NuguClientProvider,
    final override val templateInfo: TemplateHandler.TemplateInfo,
    fragment: Fragment,
) : TemplateHandler {
    protected var androidHandler = TemplateAndroidHandler(fragment, templateInfo)
    protected var nuguHandler = TemplateNuguHandler(nuguProvider, templateInfo)
    protected var mediaHandler: TemplateHandler = TemplateMediaHandler(nuguProvider, templateInfo)

    override fun onElementSelected(tokenId: String, postback: String?) {
        nuguHandler.onElementSelected(tokenId, postback)
    }

    override fun onChipSelected(text: String) {
        nuguHandler.onChipSelected(text)
    }

    override fun onCloseClicked() {
        androidHandler.onCloseClicked()
    }

    override fun onCloseWithParents() {
        androidHandler.onCloseWithParents()
    }

    override fun onCloseAllClicked() {
        androidHandler.onCloseAllClicked()
    }

    override fun onNuguButtonSelected() {
        nuguHandler.onNuguButtonSelected()
    }

    override fun onPlayerCommand(command: PlayerCommand, param: String) {
        mediaHandler.onPlayerCommand(command, param)
    }

    override fun onContextChanged(context: String) {
        nuguHandler.onContextChanged(context)
    }

    override fun onControlResult(action: String, result: String) {
        nuguHandler.onControlResult(action, result)
    }

    override fun showToast(text: String) {
        androidHandler.showToast(text)
    }

    override fun showActivity(className: String) {
        androidHandler.showActivity(className)
    }

    override fun playTTS(text: String) {
        nuguHandler.playTTS(text)
    }

    override fun setClientListener(listener: TemplateHandler.ClientListener?) {
        if (TemplateView.MEDIA_TEMPLATE_TYPES.contains(templateInfo.templateType)) {
            mediaHandler.setClientListener(listener)
        }
        nuguHandler.setClientListener(listener)
    }

    override fun clear() {
        mediaHandler.clear()
        androidHandler.clear()
        nuguHandler.clear()
    }

    override fun getNuguClient(): NuguAndroidClient = nuguProvider.getNuguClient()

    open val displayController = nuguHandler.displayController

    fun updateFragment(fragment: Fragment) {
        androidHandler.updateFragment(fragment)
    }
}