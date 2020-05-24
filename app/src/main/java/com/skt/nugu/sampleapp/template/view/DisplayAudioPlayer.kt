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
package com.skt.nugu.sampleapp.template.view

import android.content.Context
import android.util.AttributeSet
import android.widget.*
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.widget.LyricsView

class DisplayAudioPlayer @JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayView(context, attrs, defStyleAttr) {

    init {
        setContentView(R.layout.view_display_audioplayer)
    }

    val image by lazy { findViewById<ImageView>(R.id.iv_image) }

    val header by lazy { findViewById<TextView>(R.id.tv_header) }

    val body by lazy { findViewById<TextView>(R.id.tv_body) }

    val footer by lazy { findViewById<TextView>(R.id.tv_footer) }

    val prev by lazy { findViewById<ImageView>(R.id.btn_prev) }

    val play by lazy { findViewById<ImageView>(R.id.btn_play) }

    val next by lazy { findViewById<ImageView>(R.id.btn_next) }

    val progress by lazy { findViewById<SeekBar>(R.id.sb_progress) }

    val playtime by lazy { findViewById<TextView>(R.id.tv_playtime) }

    val fulltime by lazy { findViewById<TextView>(R.id.tv_fulltime) }

    val badgeImage by lazy { findViewById<ImageView>(R.id.iv_badgeImage) }

    val badgeMessage by lazy { findViewById<TextView>(R.id.tv_badgeMessage) }

    val lyricsView by lazy { findViewById<LyricsView>(R.id.cv_lyrics) }

    val smallLyricsView by lazy { findViewById<LyricsView>(R.id.cv_small_lyrics) }

    val showLyrics by lazy { findViewById<TextView>(R.id.tv_lyrics_show) }

    val favorite by lazy { findViewById<ImageView>(R.id.iv_favorite) }

    val repeat by lazy { findViewById<ImageView>(R.id.iv_repeat) }

    val shuffle by lazy { findViewById<ImageView>(R.id.iv_shuffle) }

    /* Bar Player */
    val bar_image by lazy { findViewById<ImageView>(R.id.iv_bar_image) }
    val bar_header by lazy { findViewById<TextView>(R.id.tv_bar_header) }
    val bar_body by lazy { findViewById<TextView>(R.id.tv_bar_body) }
    val bar_prev by lazy { findViewById<ImageView>(R.id.btn_bar_prev) }
    val bar_play by lazy { findViewById<ImageView>(R.id.btn_bar_play) }
    val bar_next by lazy { findViewById<ImageView>(R.id.btn_bar_next) }
    val bar_close by lazy { findViewById<ImageView>(R.id.btn_bar_close) }
    val bar_progress by lazy { findViewById<SeekBar>(R.id.sb_bar_progress) }


}