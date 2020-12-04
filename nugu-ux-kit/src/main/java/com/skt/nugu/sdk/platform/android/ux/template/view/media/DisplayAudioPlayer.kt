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
package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
<<<<<<< HEAD
=======
import android.util.Log
>>>>>>> Apply template server #1231
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.*
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.TemplateNativeView
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.model.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class DisplayAudioPlayer @JvmOverloads
constructor(private val templateType: String, context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    TemplateNativeView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DisplayAudioPlayer"
    }

    init {
        setContentView(R.layout.view_display_audioplayer)
    }

    private val player: View by lazy { findViewById<View>(R.id.view_music_player) }
    private val imageView by lazy { findViewById<ImageView>(R.id.iv_image) }
    private val header by lazy { findViewById<TextView>(R.id.tv_header) }
    private val body by lazy { findViewById<TextView>(R.id.tv_body) }
    private val footer by lazy { findViewById<TextView>(R.id.tv_footer) }
    private val prev by lazy { findViewById<ImageView>(R.id.btn_prev) }
    private val play by lazy { findViewById<ImageView>(R.id.btn_play) }
    private val next by lazy { findViewById<ImageView>(R.id.btn_next) }
    private val progressView by lazy { findViewById<SeekBar>(R.id.sb_progress) }
    private val playtime by lazy { findViewById<TextView>(R.id.tv_playtime) }
    private val fulltime by lazy { findViewById<TextView>(R.id.tv_fulltime) }
    private val badgeImage by lazy { findViewById<ImageView>(R.id.iv_badgeImage) }
    private val badgeTextView by lazy { findViewById<TextView>(R.id.tv_badgeMessage) }
    private val lyricsView by lazy { findViewById<LyricsView>(R.id.cv_lyrics) }
    private val smallLyricsView by lazy { findViewById<LyricsView>(R.id.cv_small_lyrics) }
    private val showLyrics by lazy { findViewById<TextView>(R.id.tv_show_lyrics) }
    private val favoriteView by lazy { findViewById<ImageView>(R.id.iv_favorite) }
    private val repeatView by lazy { findViewById<ImageView>(R.id.iv_repeat) }
    private val shuffleView by lazy { findViewById<ImageView>(R.id.iv_shuffle) }

    /* Bar Player */

    private val bar_body: View by lazy { findViewById<View>(R.id.bar_body) }
    private val bar_image by lazy { findViewById<ImageView>(R.id.iv_bar_image) }
    private val bar_title by lazy { findViewById<TextView>(R.id.tv_bar_title) }
    private val bar_subtitle by lazy { findViewById<TextView>(R.id.tv_bar_subtitle) }
    private val bar_prev by lazy { findViewById<ImageView>(R.id.btn_bar_prev) }
    private val bar_play by lazy { findViewById<ImageView>(R.id.btn_bar_play) }
    private val bar_next by lazy { findViewById<ImageView>(R.id.btn_bar_next) }
    private val bar_close by lazy { findViewById<ImageView>(R.id.btn_bar_close) }
    private val bar_progress by lazy { findViewById<SeekBar>(R.id.sb_bar_progress) }

    private val gson = Gson()

    private val interpolator = AccelerateDecelerateInterpolator()
    private val transitionDuration = 400L
    private var mediaDurationMs = 0L

    private val thumbTransform = RoundedCorners(dpToPx(10.7f, context))

    private fun <T> fromJsonOrNull(json: String, classOfT: Class<T>): T? {
        return try {
            gson.fromJson(json, classOfT)
        } catch (e: Throwable) {
            null
        }
    }

    override var templateHandler: TemplateHandler? = null
        set(value) {
            field = value
            value?.run {
                (this as? DefaultTemplateHandler)?.run {
                    observeMediaState()
                    setClientListener(mediaListener)
                    androidClientRef.get()?.audioPlayerAgent?.setLyricsPresenter(lyricPresenter)
                }
            }
        }

    private val mediaListener = object : TemplateHandler.ClientListener {
        override fun onMediaStateChanged(activity: AudioPlayerAgentInterface.State, currentTime: Long, currentProgress: Float) {
            post {
                if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                    play.setImageResource(R.drawable.btn_pause_48)
                    bar_play.setImageResource(R.drawable.btn_pause_32)
                } else {
                    play.setImageResource(R.drawable.btn_play_48)
                    bar_play.setImageResource(R.drawable.btn_play_32)
                }
            }
        }

        override fun onMediaDurationRetrieved(durationMs: Long) {
            fulltime.post {
                mediaDurationMs = durationMs
                fulltime.updateText(TemplateUtils.convertToTimeMs(durationMs.toInt()), true)
            }
        }

        override fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {
            post {
                progressView.isEnabled = true
                playtime.updateText(TemplateUtils.convertToTimeMs(currentTimeMs.toInt()), true)
                progressView.progress = progress.toInt()
                bar_progress.progress = progress.toInt()
                lyricsView.setCurrentTimeMs(currentTimeMs)
                smallLyricsView.setCurrentTimeMs(currentTimeMs)
            }
        }
    }

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            findViewById<View>(R.id.album_cover)?.elevation = dpToPx(11f, context).toFloat()
        }

        if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1) {
            header.enableMarquee()
        } else if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_2) {
            header.maxLines = 4
        }

        collapsed.setThrottledOnClickListener {
            collapse()
        }

        bar_body.setThrottledOnClickListener {
            expand()
        }

        smallLyricsView.setOnClickListener {
            lyricPresenter.show()
        }

        lyricsView.setThrottledOnClickListener {
            lyricPresenter.hide()
        }

        showLyrics.setThrottledOnClickListener {
            lyricPresenter.show()
        }

        prev.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.PREV.command)
        }

        bar_prev.setThrottledOnClickListener {
            prev.callOnClick()
        }

        play.setThrottledOnClickListener {
            if ((templateHandler as? DefaultTemplateHandler)?.currentMediaState == AudioPlayerAgentInterface.State.PLAYING) {
                templateHandler?.onPlayerCommand(PlayerCommand.PAUSE.command)
            } else {
                templateHandler?.onPlayerCommand(PlayerCommand.PLAY.command)
            }
        }

        bar_play.setThrottledOnClickListener {
            play.performClick()
        }

        next.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.NEXT.command)
        }

        bar_next.setThrottledOnClickListener {
            next.performClick()
        }

        bar_close.setThrottledOnClickListener {
            close.performClick()
        }

        favoriteView.setThrottledOnClickListener { _ ->
            templateHandler?.onPlayerCommand(PlayerCommand.FAVORITE.command, favoriteView.isSelected.toString())
        }

        shuffleView.setThrottledOnClickListener { _ ->
            templateHandler?.onPlayerCommand(PlayerCommand.SHUFFLE.command, shuffleView.isSelected.toString())
        }

        progressView.post {
            progressView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        Logger.i(TAG, "onProgressChanged fromUser $progress")
                        val offset = mediaDurationMs / 100 * progressView.progress
                        playtime.updateText(TemplateUtils.convertToTimeMs(offset.toInt()), true)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    Logger.i(TAG, "onStopTrackingTouch ${progressView.progress}")
                    (templateHandler as? DefaultTemplateHandler)?.androidClientRef?.get()?.run {
                        val offset = mediaDurationMs / 100 * progressView.progress
                        audioPlayerAgent?.seek(offset)
                    }
                }
            })


            progressView.setOnTouchListener(object : OnTouchListener {
                var touchDownTime = 0L

                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    event ?: return false

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touchDownTime = System.currentTimeMillis()
                        }

                        MotionEvent.ACTION_UP -> {
                            if (System.currentTimeMillis() - touchDownTime < ViewConfiguration.getTapTimeout()) {
                                (templateHandler as? DefaultTemplateHandler)?.androidClientRef?.get()?.run {
                                    val offset = event.x / progressView.width.toFloat() * mediaDurationMs
                                    audioPlayerAgent?.seek(offset.toLong())
                                }
                            }
                        }
                    }
                    return false
                }
            })
        }
    }

    override fun load(templateContent: String, deviceTypeCode: String, dialogRequestId: String, onLoadingComplete: (() -> Unit)?) {
        Logger.i(TAG, "load. $templateContent")

        fromJsonOrNull(templateContent, AudioPlayer::class.java)?.let { item ->
            load(item)
        }
        onLoadingComplete?.invoke()
    }

    override fun update(templateContent: String, dialogRequestedId: String, onLoadingComplete: (() -> Unit)?) {
        fromJsonOrNull(templateContent, AudioPlayerUpdate::class.java)?.let { item ->
            load(item.player, true)
        }
    }

    private fun load(item: AudioPlayer, isMerge: Boolean = false) {
        item.title?.run {
            titleView.updateText(text, isMerge)
            logoView.updateImage(iconUrl, thumbTransform, isMerge)
        }

        item.content?.run {
            imageView.updateImage(imageUrl, thumbTransform, isMerge)
            header.updateText(title, isMerge)
            body.updateText(subtitle1, isMerge)
            footer.updateText(subtitle2, isMerge)
            badgeImage.updateImage(badgeImageUrl, thumbTransform, isMerge)
            badgeTextView.updateText(badgeMessage, isMerge)

            if (durationSec != null) {
                progressView.isEnabled = true
                playtime.updateText(TemplateUtils.convertToTime(0), isMerge)
            } else if (!isMerge) {
                progressView.isEnabled = false
                playtime.visibility = View.INVISIBLE
                fulltime.visibility = View.INVISIBLE
            }

            if (lyrics != null) {
                lyricsView.setTitle(lyrics.title, templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1)
                lyricsView.setItems(lyrics.lyricsInfoList)
                smallLyricsView.setItems(lyrics.lyricsInfoList)

                if (lyrics.lyricsType == LyricsType.SYNC) {
                    smallLyricsView.visibility = View.VISIBLE
                    showLyrics.visibility = View.GONE
                } else if (lyrics.lyricsType == LyricsType.NON_SYNC) {
                    showLyrics.visibility = View.VISIBLE
                    smallLyricsView.visibility = View.GONE
                } else {
                    smallLyricsView.visibility = View.GONE
                }
            } else if (!isMerge) {
                lyricPresenter.hide()
                showLyrics.visibility = View.GONE
                smallLyricsView.visibility = View.GONE
            }

            bar_image.updateImage(imageUrl, thumbTransform, isMerge)
            bar_title.updateText(title, isMerge)
            bar_subtitle.updateText(subtitle1, isMerge)
        }

        item.content?.settings?.run {
            if (!isMerge) {
                favoriteView.visibility = if (favorite != null) View.VISIBLE else View.INVISIBLE
                repeatView.visibility = if (repeat != null) View.VISIBLE else View.INVISIBLE
                shuffleView.visibility = if (shuffle != null) View.VISIBLE else View.INVISIBLE
            }

            favorite?.let {
                favoriteView.isSelected = it
            }

            repeat?.let {
                when (it) {
                    Repeat.ALL -> repeatView.setImageResource(R.drawable.btn_repeat)
                    Repeat.ONE -> repeatView.setImageResource(R.drawable.btn_repeat_1)
                    Repeat.NONE -> repeatView.setImageResource(R.drawable.btn_repeat_inactive)
                }
                repeatView.setThrottledOnClickListener { _ ->
                    templateHandler?.onPlayerCommand(PlayerCommand.REPEAT.command, it.name)
                }
            }

            shuffle?.let {
                shuffleView.isSelected = it
            }
        }
    }

    private fun collapse() {
        player.animate().y(player.height.toFloat()).setDuration(transitionDuration).interpolator = interpolator
    }

    private fun expand() {
        player.animate().y(0f).setDuration(transitionDuration).interpolator = interpolator
    }

    private val lyricPresenter = object : LyricsPresenter {
        override fun getVisibility(): Boolean {
            return lyricsView.visibility == View.VISIBLE
        }

        override fun show(): Boolean {
            lyricsView.post {
                lyricsView.visibility = View.VISIBLE
            }

            return true
        }

        override fun hide(): Boolean {
            lyricsView.post {
                lyricsView.visibility = View.GONE
            }

            return true
        }

        override fun controlPage(direction: Direction): Boolean {
            return false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        templateHandler?.clear()
    }
}