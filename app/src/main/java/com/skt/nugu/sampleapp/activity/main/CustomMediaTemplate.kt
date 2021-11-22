/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sampleapp.activity.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.MediaTemplateResources


@SuppressLint("ViewConstructor")
class CustomMediaTemplate(templateType: String, context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    DisplayAudioPlayer(templateType, context) {
    inner class CustomMediaTemplateResources : MediaTemplateResources() {
        // Set your custom layout resource.
        // It must contain all View resource which is used DisplayAudioPlayer.
        // Take a look 'R.layout.custom_media_player'
        override val layoutResIdPort: Int
            get() = R.layout.custom_media_player

        override val layoutResIdLand: Int
            get() = super.layoutResIdLand
    }

    override val mediaTemplateResources: MediaTemplateResources
        get() = CustomMediaTemplateResources()

    override fun onCloseClicked() {
        super.onCloseClicked()
        // called when close button clicked
    }

    override fun onCollapseButtonClicked() {
        super.onCollapseButtonClicked()
        // called when collapse button clicked
    }

    override fun onBarPlayerClicked() {
        super.onBarPlayerClicked()
        // called when bar player clicked
        // at this event, super function make player expanded
    }
}
