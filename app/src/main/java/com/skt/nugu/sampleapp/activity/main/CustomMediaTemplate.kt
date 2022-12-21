/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
import android.view.View
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.MediaTemplateResources

/**
 * This is sample of customising layout or resources of MediaTemplate.
 * It is for special case.
 * In most cases you would not use it.
 */
@SuppressLint("ViewConstructor")
class CustomMediaTemplate(templateType: String, context: Context, isPlaylistSupport : Boolean,  attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    DisplayAudioPlayer(templateType, context, CustomMediaTemplateResources(), isPlaylistSupport) {

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
    
    // code below of overriding updateLayoutForPlaylist() and onPlaylistHidden() is example how DisplayAudioPlayer's layout  could be customized according to Playlist visibility.
    // this example hide and show repeat and shuffle button when playlist shown or hidden.
    override fun updateLayoutForPlaylist() {
        super.updateLayoutForPlaylist()
        findViewById<View>(R.id.iv_repeat).visibility = View.GONE
        findViewById<View>(R.id.iv_shuffle).visibility = View.GONE
    }

    override fun onPlaylistHidden() {
        super.onPlaylistHidden()
        findViewById<View>(R.id.iv_repeat).visibility = if (audioPlayerItem?.content?.settings?.repeat != null) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.iv_shuffle).visibility = if (audioPlayerItem?.content?.settings?.shuffle != null) View.VISIBLE else View.INVISIBLE
    }

    // Playlist basically shown upper media control area. This make user can keep see media progress and control.
    // For it Playlist should know DisplayAudioPlayer's Control area height and getPlaylistBottomMargin() will provide it.
    // If your layout doesn't follow this rule. You can customize it by overriding getPlaylistBottomMargin()
    override fun getPlaylistBottomMargin(): Int {
        return super.getPlaylistBottomMargin()
    }
}

class CustomMediaTemplateResources : MediaTemplateResources() {
    // Set your custom layout resource.
    // It must contain all View resource which is used DisplayAudioPlayer.
    // Take a look 'R.layout.custom_media_player'
    override val layoutResIdPort: Int
        get() = R.layout.custom_media_player

    override val repeatAllResId: Int
        get() = super.repeatAllResId
    override val repeatOneResId: Int
        get() = super.repeatOneResId
    override val repeatNoneResId: Int
        get() = android.R.drawable.btn_radio
}