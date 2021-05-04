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
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.ORIENTATION_UNDEFINED
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.AbsSavedState
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
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
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayerUpdate
import com.skt.nugu.sdk.platform.android.ux.template.model.LyricsType
import com.skt.nugu.sdk.platform.android.ux.template.model.Repeat
import com.skt.nugu.sdk.platform.android.ux.template.presenter.EmptyLyricsPresenter
import com.skt.nugu.sdk.platform.android.ux.template.view.TemplateNativeView
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener


@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class DisplayAudioPlayer @JvmOverloads
constructor(private val templateType: String, context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    TemplateNativeView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DisplayAudioPlayer"
    }

    private lateinit var player: View
    private lateinit var imageView: ImageView
    private lateinit var header: TextView
    private lateinit var body: TextView
    private lateinit var footer: TextView
    private lateinit var prev: ImageView
    private lateinit var play: ImageView
    private lateinit var next: ImageView
    private lateinit var progressView: SeekBar
    private lateinit var playtime: TextView
    private lateinit var fulltime: TextView
    private lateinit var badgeImage: ImageView
    private lateinit var badgeTextView: TextView
    private lateinit var lyricsView: LyricsView
    private lateinit var smallLyricsView: LyricsView
    private lateinit var showLyrics: TextView
    private lateinit var favoriteView: ImageView
    private lateinit var repeatView: ImageView
    private lateinit var shuffleView: ImageView
    private lateinit var controller: View
    private lateinit var albumCover: View

    /* Bar Player */
    private lateinit var bar_body: View
    private lateinit var bar_image: ImageView
    private lateinit var bar_title: TextView
    private lateinit var bar_subtitle: TextView
    private lateinit var bar_prev: ImageView
    private lateinit var bar_play: ImageView
    private lateinit var bar_next: ImageView
    private lateinit var bar_close: ImageView
    private lateinit var bar_progress: SeekBar

    private val gson = Gson()

    private val interpolator = AccelerateDecelerateInterpolator()
    private val transitionDuration = 400L
    private var mediaDurationMs = 0L
    private var mediaCurrentTimeMs = 0L
    private var mediaPlaying = false

    private val thumbTransformCorner10 = RoundedCorners(dpToPx(10.7f, context))
    private val thumbTransformCorner2 = RoundedCorners(dpToPx(2f, context))

    private var audioPlayerItem: AudioPlayer? = null

    private var currOrientation = ORIENTATION_UNDEFINED

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
                    getNuguClient().audioPlayerAgent?.setLyricsPresenter(lyricPresenter)
                }
            }
        }

    private val mediaListener = object : TemplateHandler.ClientListener {
        override fun onMediaStateChanged(activity: AudioPlayerAgentInterface.State, currentTimeMs: Long, currentProgress: Float) {
            post {
                mediaPlaying = activity == AudioPlayerAgentInterface.State.PLAYING
                if (mediaPlaying) {
                    play.setImageResource(R.drawable.nugu_btn_pause_48)
                    bar_play.setImageResource(R.drawable.nugu_btn_pause_32)
                } else {
                    play.setImageResource(R.drawable.nugu_btn_play_48)
                    bar_play.setImageResource(R.drawable.nugu_btn_play_32)
                }
            }
        }

        override fun onMediaDurationRetrieved(durationMs: Long) {
            audioPlayerItem?.run {
                if (content.durationSec == null) {
                    return
                }
            }

            fulltime.post {
                mediaDurationMs = durationMs
                fulltime.updateText(TemplateUtils.convertToTimeMs(durationMs.toInt()), true)
            }
        }

        override fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {
            mediaCurrentTimeMs = currentTimeMs

            audioPlayerItem?.run {
                if (content.durationSec == null) {
                    return
                }
            }

            post {
                progressView.isEnabled = true
                bar_progress.isEnabled = true
                playtime.updateText(TemplateUtils.convertToTimeMs(currentTimeMs.toInt()), true)
                progressView.progress = progress.toInt()
                bar_progress.progress = progress.toInt()
                lyricsView.setCurrentTimeMs(currentTimeMs)
                smallLyricsView.setCurrentTimeMs(currentTimeMs)
            }
        }
    }

    internal class SavedStates(
        superState: Parcelable,
        var durationMs: Long,
        var currentTimeMs: Long,
        var mediaPlaying: Int,
        var isBarType: Int,
        var isLyricShowing: Int
    ) :
        AbsSavedState(superState) {

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            super.writeToParcel(dest, flags)
            dest?.writeLong(durationMs)
            dest?.writeLong(currentTimeMs)
            dest?.writeInt(mediaPlaying)
            dest?.writeInt(isBarType)
            dest?.writeInt(isLyricShowing)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        super.onSaveInstanceState()
        return SavedStates(super.onSaveInstanceState() ?: Bundle.EMPTY,
            mediaDurationMs,
            mediaCurrentTimeMs,
            if (mediaPlaying) 1 else 0,
            if (player.y != 0f) 1 else 0,
            if (lyricsView.visibility == View.VISIBLE) 1 else 0)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)

        (state as? SavedStates)?.let { savedState ->
            mediaDurationMs = savedState.durationMs
            mediaCurrentTimeMs = savedState.currentTimeMs
            mediaPlaying = savedState.mediaPlaying == 1

            fulltime.post {
                fulltime.updateText(TemplateUtils.convertToTimeMs(mediaDurationMs.toInt()), true)
                playtime.updateText(TemplateUtils.convertToTimeMs(mediaCurrentTimeMs.toInt()), true)
                lyricsView.visibility = if (savedState.isLyricShowing == 1) View.VISIBLE else View.GONE
                lyricsView.setCurrentTimeMs(mediaCurrentTimeMs)
                smallLyricsView.setCurrentTimeMs(mediaCurrentTimeMs)
            }

            if (mediaPlaying) {
                play.post {
                    play.setImageResource(R.drawable.nugu_btn_pause_48)
                    bar_play.setImageResource(R.drawable.nugu_btn_pause_32)
                }
            }

            player.post {
                if (savedState.isBarType == 1) collapse(true)
                else expand(true)
            }
        }
    }

    data class RenderInfo(val lyricShowing: Boolean, val barType: Boolean)

    init {
        currOrientation = resources.configuration.orientation
        setContentView()
        isSaveEnabled = true

        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (currOrientation != resources.configuration.orientation) {
                currOrientation = resources.configuration.orientation
                setContentView(true)
            }

            if (currOrientation == ORIENTATION_PORTRAIT) {
                post {
                    val titleHeight = resources.getDimensionPixelSize(R.dimen.media_player_title_height)
                    val imageSize = ((measuredHeight - titleHeight).toFloat() * 0.4f).toInt()
                    val bottomMargin = ((measuredHeight - titleHeight).toFloat() * 0.1f).toInt()
                    val minContentHeight = dpToPx(388f, context)

                    if (measuredHeight - titleHeight <= minContentHeight) {
                        if (albumCover.visibility != View.GONE) {
                            albumCover.visibility = View.GONE
                        }
                    } else {
                        albumCover.visibility = View.VISIBLE
                        if (imageView.layoutParams.width != imageSize) {
                            imageView.layoutParams.width = imageSize
                            imageView.layoutParams.height = imageSize
                            imageView.requestLayout()
                        }
                    }

                    (controller.layoutParams as? FrameLayout.LayoutParams)?.let { controllerLayout ->
                        if (controllerLayout.bottomMargin != bottomMargin) {
                            controllerLayout.bottomMargin = bottomMargin
                            controller.postInvalidate()
                        }
                    }
                }
            }
        }
    }

    private fun initView() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            findViewById<View>(R.id.album_cover)?.elevation = dpToPx(11f, context).toFloat()
        }

        if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1) {
            header.maxLines = 1
            header.enableMarquee()
        } else if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_2) {
            header.maxLines = 2
            header.ellipsize = TextUtils.TruncateAt.END
        }

        bar_title.enableMarquee()

        collapsed.setThrottledOnClickListener {
            collapse()
        }

        bar_body.setThrottledOnClickListener {
            expand()
        }

        smallLyricsView.setThrottledOnClickListener {
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

        progressView.setOnTouchListener { _, _ -> true }
    }

    private fun setContentView(isRefresh: Boolean = false) {
        var savedState: SavedStates? = null
        val isFavoriteSelected = if (isRefresh) favoriteView.isSelected else false
        val isShuffle = if (isRefresh) shuffleView.isSelected else false
        val repeatInfo = if (isRefresh) repeatView.getTag(R.id.iv_repeat) else Unit

        if (isRefresh) {
            savedState = SavedStates(super.onSaveInstanceState() ?: Bundle.EMPTY,
                mediaDurationMs,
                mediaCurrentTimeMs,
                if (mediaPlaying) 1 else 0,
                if (player.y != 0f) 1 else 0,
                if (lyricsView.visibility == View.VISIBLE) 1 else 0)
        }

        if (currOrientation == ORIENTATION_PORTRAIT) {
            setContentView(R.layout.view_display_audioplayer_port)
        } else {
            setContentView(R.layout.view_display_audioplayer_land)
        }

        refreshView()

        initView()
        if (isRefresh) {
            audioPlayerItem?.run {
                load(this, false)
            }

            onRestoreInstanceState(savedState)

            repeatInfo?.let {
                when (it) {
                    Repeat.ALL -> repeatView.setImageResource(R.drawable.nugu_btn_repeat)
                    Repeat.ONE -> repeatView.setImageResource(R.drawable.nugu_btn_repeat_1)
                    Repeat.NONE -> repeatView.setImageResource(R.drawable.nugu_btn_repeat_inactive)
                }
            }
            shuffleView.isSelected = isShuffle
            favoriteView.isSelected = isFavoriteSelected
        }
    }

    private fun refreshView() {
        player = findViewById<View>(R.id.view_music_player)
        imageView = findViewById(R.id.iv_image)
        header = findViewById(R.id.tv_header)
        body = findViewById(R.id.tv_body)
        footer = findViewById(R.id.tv_footer)
        prev = findViewById(R.id.btn_prev)
        play = findViewById(R.id.btn_play)
        next = findViewById(R.id.btn_next)
        progressView = findViewById(R.id.sb_progress)
        playtime = findViewById(R.id.tv_playtime)
        fulltime = findViewById(R.id.tv_fulltime)
        badgeImage = findViewById(R.id.iv_badgeImage)
        badgeTextView = findViewById(R.id.tv_badgeMessage)
        lyricsView = findViewById(R.id.cv_lyrics)
        smallLyricsView = findViewById(R.id.cv_small_lyrics)
        showLyrics = findViewById(R.id.tv_show_lyrics)
        favoriteView = findViewById(R.id.iv_favorite)
        repeatView = findViewById(R.id.iv_repeat)
        shuffleView = findViewById(R.id.iv_shuffle)
        controller = findViewById<View>(R.id.controller_area)
        albumCover = findViewById<View>(R.id.album_cover)

        /* Bar Player */
        bar_body = findViewById<View>(R.id.bar_body)
        bar_image = findViewById(R.id.iv_bar_image)
        bar_title = findViewById(R.id.tv_bar_title)
        bar_subtitle = findViewById(R.id.tv_bar_subtitle)
        bar_prev = findViewById(R.id.btn_bar_prev)
        bar_play = findViewById(R.id.btn_bar_play)
        bar_next = findViewById(R.id.btn_bar_next)
        bar_close = findViewById(R.id.btn_bar_close)
        bar_progress = findViewById(R.id.sb_bar_progress)
    }

    override fun load(templateContent: String, deviceTypeCode: String, dialogRequestId: String, onLoadingComplete: (() -> Unit)?) {
        Logger.i(TAG, "load. dialogRequestId: $dialogRequestId/*, \n template $templateContent*/")

        fromJsonOrNull(templateContent, AudioPlayer::class.java)?.let { item ->
            load(item)
        }
        onLoadingComplete?.invoke()
    }

    override fun update(templateContent: String, dialogRequestedId: String, onLoadingComplete: (() -> Unit)?) {
        Logger.i(TAG, "update. dialogRequestId $dialogRequestedId")

        var audioPlayer: AudioPlayer? = null

        fromJsonOrNull(templateContent, AudioPlayerUpdate::class.java)?.let { item ->
            audioPlayer = item.player
        }

        // templateContent model be expected as AudioPlayerUpdate. but other case have found. try parsing as AudioPlayer
        if (audioPlayer == null) {
            fromJsonOrNull(templateContent, AudioPlayer::class.java)?.let { item ->
                audioPlayer = item
            }
        }

        audioPlayer.run {
            if (this != null) {
                load(this, true)
            } else {
                Logger.e(TAG, "update. fail. audioPlayer info not exists")
            }
        }
    }

    private fun load(item: AudioPlayer, isMerge: Boolean = false) {
        if (!isMerge) audioPlayerItem = item

        item.title?.run {
            titleView.updateText(text, isMerge)
            logoView.updateImage(iconUrl, thumbTransformCorner2, isMerge)
        }

        item.content?.run {
            imageView.updateImage(imageUrl, thumbTransformCorner10, isMerge)
            header.updateText(title, isMerge)
            body.updateText(subtitle1, isMerge)
            footer.updateText(subtitle2, isMerge, true)
            badgeImage.updateImage(badgeImageUrl, thumbTransformCorner2, isMerge)
            badgeTextView.updateText(badgeMessage, isMerge)

            if (durationSec != null) {
                progressView.isEnabled = true
                bar_progress.isEnabled = true
                playtime.updateText(TemplateUtils.convertToTime(0), isMerge)
            } else if (!isMerge) {
                progressView.isEnabled = false
                progressView.visibility = View.INVISIBLE
                bar_progress.isEnabled = false
                bar_progress.visibility = View.INVISIBLE
                playtime.visibility = View.INVISIBLE
                fulltime.visibility = View.INVISIBLE
            }

            if (lyrics != null && lyrics.lyricsType != LyricsType.NONE) {
                lyricsView.setTitle(lyrics.title, templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1)
                lyricsView.setItems(lyrics.lyricsInfoList)
                smallLyricsView.setItems(lyrics.lyricsInfoList)

                when (lyrics.lyricsType) {
                    LyricsType.SYNC -> {
                        smallLyricsView.visibility = View.VISIBLE
                        showLyrics.visibility = View.GONE
                    }
                    LyricsType.NON_SYNC -> {
                        with(showLyrics) {
                            visibility = View.VISIBLE
                            text = lyrics.showButton?.text ?: resources.getString(R.string.lyrics_button_default_text)
                        }
                        smallLyricsView.visibility = View.GONE
                    }
                    else -> Unit
                }
            } else if (!isMerge) {
                lyricPresenter.hide()
                showLyrics.visibility = View.GONE
                smallLyricsView.visibility = View.GONE
            }

            bar_image.updateImage(imageUrl, thumbTransformCorner2, isMerge)
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
                    Repeat.ALL -> repeatView.setImageResource(R.drawable.nugu_btn_repeat)
                    Repeat.ONE -> repeatView.setImageResource(R.drawable.nugu_btn_repeat_1)
                    Repeat.NONE -> repeatView.setImageResource(R.drawable.nugu_btn_repeat_inactive)
                }
                repeatView.setTag(R.id.iv_repeat, repeat)
                repeatView.setThrottledOnClickListener { _ ->
                    templateHandler?.onPlayerCommand(PlayerCommand.REPEAT.command, it.name)
                }
            }

            shuffle?.let {
                shuffleView.isSelected = it
            }
        }

        if (player.visibility != View.VISIBLE) {
            collapse(true)
        }

    }

    private fun collapse(immediately: Boolean = false) {
        player.post {
            if (immediately) {
                player.y = player.height.toFloat()
            } else {
                player.animate().y(player.height.toFloat()).setDuration(transitionDuration).interpolator = interpolator
            }
        }
    }

    private fun expand(immediately: Boolean = false) {
        player.visibility = View.VISIBLE
        player.post {
            if (immediately) {
                player.y = 0f
            } else {
                player.animate().y(0f).setDuration(transitionDuration).interpolator = interpolator
            }
        }
    }

    private val lyricPresenter = object : LyricsPresenter {
        override fun getVisibility(): Boolean {
            return lyricsView.visibility == View.VISIBLE
        }

        override fun show(): Boolean {
            lyricsView.post {
                lyricsView.visibility = View.VISIBLE
                lyricsView.setCurrentTimeMs(mediaCurrentTimeMs)
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
            Logger.d(TAG, "controlPage $direction")
            lyricsView.post {
                lyricsView.controlPage(direction)
            }
            return true
        }
    }

    fun applyPreviousRenderInfo(previousRenderInfo: RenderInfo) {
        if (previousRenderInfo.lyricShowing && audioPlayerItem?.content?.lyrics?.lyricsType != LyricsType.NONE) {
            lyricsView.post {
                lyricsView.visibility = View.VISIBLE
                lyricsView.setCurrentTimeMs(mediaCurrentTimeMs)
            }
        }

        if (!previousRenderInfo.barType) {
            expand(true)
        }
    }

    fun getRenderInfo() = RenderInfo(lyricsView.visibility == View.VISIBLE, player.y != 0f)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        (templateHandler as? DefaultTemplateHandler)?.run {
            if (getNuguClient().audioPlayerAgent?.lyricsPresenter == lyricPresenter) {
                getNuguClient().audioPlayerAgent?.setLyricsPresenter(EmptyLyricsPresenter)
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        (templateHandler as? DefaultTemplateHandler)?.getNuguClient()?.localStopTTS()
        return super.onInterceptTouchEvent(ev)
    }
}