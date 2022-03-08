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
package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.ORIENTATION_UNDEFINED
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
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
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface.THEME.DARK
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface.THEME.SYSTEM
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.*
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayerUpdate
import com.skt.nugu.sdk.platform.android.ux.template.model.LyricsType
import com.skt.nugu.sdk.platform.android.ux.template.model.Repeat
import com.skt.nugu.sdk.platform.android.ux.template.presenter.EmptyLyricsPresenter
import com.skt.nugu.sdk.platform.android.ux.template.view.TemplateNativeView
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener


@SuppressLint("ClickableViewAccessibility")
open class DisplayAudioPlayer constructor(
    private val templateType: String,
    context: Context,
) : TemplateNativeView(context, null, 0), ThemeManagerInterface.ThemeListener {

    private constructor(context: Context) : this("", context)
    private constructor(context: Context, attrs: AttributeSet?) : this("", context)
    private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this("", context)

    companion object {
        private const val TAG = "DisplayAudioPlayer"
    }

    open val mediaTemplateResources = MediaTemplateResources()

    private lateinit var expandedPlayer: View
    private lateinit var albumImage: ImageView
    private lateinit var header: TextView
    private lateinit var body: TextView
    private lateinit var footer: TextView
    private lateinit var btnPrev: ImageView
    private lateinit var btnPlay: ImageView
    private lateinit var btnPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var progressView: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeEnd: TextView
    private lateinit var badgeImage: ImageView      //the image icon at right top of album cover
    private lateinit var badgeTextView: TextView    //the text icon at right bottom of album cover
    private lateinit var lyricsView: LyricsView
    private lateinit var smallLyricsView: LyricsView
    private lateinit var btnShowLyrics: TextView
    private lateinit var btnFavorite: ImageView
    private lateinit var btnRepeat: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var mediaController: View
    private lateinit var albumCover: View

    /* Bar Player */
    private lateinit var barPlayer: View
    private lateinit var barBody: View
    private lateinit var barImage: ImageView
    private lateinit var barTitle: TextView
    private lateinit var barSubtitle: TextView
    private lateinit var btnBarPrev: ImageView
    private lateinit var btnBarPlay: ImageView
    private lateinit var btnBarPause: ImageView
    private lateinit var btnBarNext: ImageView
    private lateinit var btnBarClose: ImageView
    private lateinit var barProgress: SeekBar

    private val gson = Gson()

    private val interpolator = AccelerateDecelerateInterpolator()
    private var mediaDurationMs = 0L
    private var mediaCurrentTimeMs = 0L
    private var mediaPlaying = false

    private val thumbTransformCornerAlbumCover by lazy { RoundedCorners(dpToPx(mediaTemplateResources.mainImageRoundingRadiusDp, context)) }
    private val thumbTransformCornerAlbumBadge by lazy { RoundedCorners(dpToPx(mediaTemplateResources.badgeImageRoundingRadiusDp, context)) }

    private var bgColorLight = resources.genColor(R.color.media_template_bg_light)

    @VisibleForTesting
    internal var audioPlayerItem: AudioPlayer? = null

    private var currOrientation = ORIENTATION_UNDEFINED

    private var isDark: Boolean = false

    private fun <T> fromJsonOrNull(json: String, classOfT: Class<T>): T? {
        return runCatching { gson.fromJson(json, classOfT) }.getOrNull()
    }

    override var templateHandler: TemplateHandler? = null
        set(value) {
            field = value
            value?.run {
                setClientListener(mediaListener)
                getNuguClient()?.audioPlayerAgent?.setLyricsPresenter(lyricPresenter)
                getNuguClient()?.themeManager?.addListener(this@DisplayAudioPlayer)

                updateThemeIfNeeded()
            }
        }

    @VisibleForTesting
    internal val mediaListener = object : TemplateHandler.ClientListener {
        override fun onMediaStateChanged(
            activity: AudioPlayerAgentInterface.State,
            currentTimeMs: Long,
            currentProgress: Float,
            showController: Boolean,
        ) {
            post {
                mediaPlaying = activity == AudioPlayerAgentInterface.State.PLAYING
                btnPlay.visibility = if (mediaPlaying) View.GONE else View.VISIBLE
                btnPause.visibility = if (mediaPlaying) View.VISIBLE else View.GONE

                btnBarPlay.visibility = if (mediaPlaying) View.GONE else View.VISIBLE
                btnBarPause.visibility = if (mediaPlaying) View.VISIBLE else View.GONE
            }
        }

        override fun onMediaDurationRetrieved(durationMs: Long) {
            audioPlayerItem?.run {
                if (content.durationSec == null) {
                    return
                }
            }

            timeEnd.post {
                mediaDurationMs = durationMs
                timeEnd.updateText(convertToTimeMs(durationMs.toInt()), true)
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
                barProgress.isEnabled = true
                updateCurrentTimeInfo(currentTimeMs, progress)
            }
        }
    }

    internal class SavedStates(
        superState: Parcelable,
        var durationMs: Long,
        var currentTimeMs: Long,
        var mediaPlaying: Int,
        var isBarType: Int,
        var isLyricShowing: Int,
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
            if (expandedPlayer.y != 0f) 1 else 0,
            if (lyricsView.visibility == View.VISIBLE) 1 else 0)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)

        (state as? SavedStates)?.let { savedState ->
            mediaDurationMs = savedState.durationMs
            mediaCurrentTimeMs = savedState.currentTimeMs
            mediaPlaying = savedState.mediaPlaying == 1

            timeEnd.post {
                if (timeEnd.visibility == View.VISIBLE) {
                    timeEnd.updateText(convertToTimeMs(mediaDurationMs.toInt()), true)
                }
                lyricsView.visibility = if (savedState.isLyricShowing == 1) View.VISIBLE else View.GONE

                updateCurrentTimeInfo(mediaCurrentTimeMs)
            }

            if (mediaPlaying) {
                btnPlay.post {
                    btnPlay.visibility = View.GONE
                    btnPause.visibility = View.VISIBLE

                    btnBarPlay.visibility = View.GONE
                    btnBarPause.visibility = View.VISIBLE
                }
            }

            expandedPlayer.post {
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

        this.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (currOrientation != resources.configuration.orientation) {
                currOrientation = resources.configuration.orientation
                setContentView(true)
            }

            if (mediaTemplateResources.layoutResIdPort == R.layout.view_display_audioplayer_port && currOrientation == ORIENTATION_PORTRAIT) {
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
                        if (albumImage.layoutParams.width != imageSize) {
                            albumImage.layoutParams.width = imageSize
                            albumImage.layoutParams.height = imageSize
                            albumImage.requestLayout()
                        }
                    }

                    (mediaController.layoutParams as? FrameLayout.LayoutParams)?.let { controllerLayout ->
                        if (controllerLayout.bottomMargin != bottomMargin) {
                            controllerLayout.bottomMargin = bottomMargin
                            mediaController.postInvalidate()
                        }
                    }
                }
            }
        }
    }

    private fun initViews() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            findViewById<View>(R.id.album_cover)?.elevation = dpToPx(mediaTemplateResources.albumCoverElevation, context).toFloat()
        }

        if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1) {
            header.maxLines = 1
            header.enableMarquee()
        } else if (templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_2) {
            header.maxLines = 2
            header.ellipsize = TextUtils.TruncateAt.END
        }

        barTitle.enableMarquee()

        btnCollapse.setThrottledOnClickListener {
            onCollapseButtonClicked()
        }

        barBody.setThrottledOnClickListener {
            onBarPlayerClicked()
        }

        smallLyricsView.setThrottledOnClickListener {
            lyricPresenter.show()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        lyricsView.setThrottledOnClickListener {
            lyricPresenter.hide()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnShowLyrics.setThrottledOnClickListener {
            lyricPresenter.show()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnPrev.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.PREV)
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnBarPrev.setThrottledOnClickListener {
            btnPrev.callOnClick()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnPlay.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.PLAY)
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnPause.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.PAUSE)
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnBarPlay.setThrottledOnClickListener {
            btnPlay.performClick()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnBarPause.setThrottledOnClickListener {
            btnPause.performClick()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnNext.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.NEXT)
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnBarNext.setThrottledOnClickListener {
            btnNext.performClick()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnBarClose.setThrottledOnClickListener {
            onCloseClicked()
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnFavorite.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.FAVORITE, btnFavorite.isSelected.toString())
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        btnShuffle.setThrottledOnClickListener {
            templateHandler?.onPlayerCommand(PlayerCommand.SHUFFLE, btnShuffle.isSelected.toString())
            onViewClicked(it ?: return@setThrottledOnClickListener)
        }

        progressView.setOnTouchListener { _, _ -> true }
        barProgress.setOnTouchListener { _, _ -> true }
    }

    open fun onCollapseButtonClicked() {
        collapse()
    }

    open fun onBarPlayerClicked() {
        expand()
    }

    open fun onViewClicked(v: View) {
        // do nothing. This function is only for notifying clicked view information to Custom Media Template
    }

    /**
     * @param isRefresh : Whether Player is newly created or not.
    If it is true, Player is not newly created and just update content views.
     */
    private fun setContentView(isRefresh: Boolean = false) {
        var savedState: SavedStates? = null
        val isFavoriteSelected = if (isRefresh) btnFavorite.isSelected else false
        val isShuffle = if (isRefresh) btnShuffle.isSelected else false
        val repeatInfo = if (isRefresh) btnRepeat.getTag(R.id.iv_repeat) else Unit

        // if isRefresh, save current state to restore after setContentView
        if (isRefresh) {
            savedState = SavedStates(super.onSaveInstanceState() ?: Bundle.EMPTY,
                mediaDurationMs,
                mediaCurrentTimeMs,
                if (mediaPlaying) 1 else 0,
                if (expandedPlayer.y != 0f) 1 else 0,
                if (lyricsView.visibility == View.VISIBLE) 1 else 0)
        }

        if (currOrientation == ORIENTATION_PORTRAIT) {
            setContentView(mediaTemplateResources.layoutResIdPort)
        } else {
            setContentView(mediaTemplateResources.layoutResIdLand)
        }

        isDark = false // initialize

        setViews()
        initViews()

        // if isRefresh, restore previous state which is saved just before
        if (isRefresh) {
            audioPlayerItem?.run {
                load(this, false)
            }

            onRestoreInstanceState(savedState)

            (repeatInfo as? Repeat)?.let {
                setRepeatMode(it)
            }

            setShuffle(isShuffle)

            btnFavorite.isSelected = isFavoriteSelected
        }

        updateThemeIfNeeded()
    }

    override fun setViews() {
        super.setViews()

        expandedPlayer = findViewById(R.id.view_music_player)
        albumImage = findViewById(R.id.iv_image)
        header = findViewById(R.id.tv_header)
        body = findViewById(R.id.tv_body)
        footer = findViewById(R.id.tv_footer)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlay = findViewById(R.id.btn_play)
        btnPause = findViewById(R.id.btn_pause)
        btnNext = findViewById(R.id.btn_next)
        progressView = findViewById(R.id.sb_progress)
        timeCurrent = findViewById(R.id.tv_playtime)
        timeEnd = findViewById(R.id.tv_fulltime)
        badgeImage = findViewById(R.id.iv_badgeImage)
        badgeTextView = findViewById(R.id.tv_badgeMessage)
        lyricsView = findViewById(R.id.cv_lyrics)
        smallLyricsView = findViewById(R.id.cv_small_lyrics)
        btnShowLyrics = findViewById(R.id.tv_show_lyrics)
        btnFavorite = findViewById(R.id.iv_favorite)
        btnRepeat = findViewById(R.id.iv_repeat)
        btnShuffle = findViewById(R.id.iv_shuffle)
        mediaController = findViewById(R.id.controller_area)
        albumCover = findViewById(R.id.album_cover)

        /* Bar Player */
        barPlayer = findViewById(R.id.bar_player)
        barBody = findViewById(R.id.bar_body)
        barImage = findViewById(R.id.iv_bar_image)
        barTitle = findViewById(R.id.tv_bar_title)
        barSubtitle = findViewById(R.id.tv_bar_subtitle)
        btnBarPrev = findViewById(R.id.btn_bar_prev)
        btnBarPlay = findViewById(R.id.btn_bar_play)
        btnBarPause = findViewById(R.id.btn_bar_pause)
        btnBarNext = findViewById(R.id.btn_bar_next)
        btnBarClose = findViewById(R.id.btn_bar_close)
        barProgress = findViewById(R.id.sb_bar_progress)

        (expandedPlayer.background as? ColorDrawable)?.color?.apply { bgColorLight = this }
        barPlayer.setBackgroundColor(bgColorLight)
    }

    override fun load(
        templateContent: String,
        deviceTypeCode: String,
        dialogRequestId: String,
        onLoadingComplete: (() -> Unit)?,
        onLoadingFail: ((String?) -> Unit)?,
        disableCloseButton: Boolean,
    ) {
        Logger.i(TAG, "load. dialogRequestId: $dialogRequestId/*, \n template $templateContent*/")

        fromJsonOrNull(templateContent, AudioPlayer::class.java)?.let { item ->
            load(item)
        }
        onLoadingComplete?.invoke()
    }

    override fun update(templateContent: String, dialogRequestedId: String) {
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

    @VisibleForTesting
    internal fun load(item: AudioPlayer, isMerge: Boolean = false) {
        if (!isMerge) audioPlayerItem = item

        item.title?.run {
            title.updateText(text, isMerge)
        }

        item.title?.iconUrl.let {
            if (it.isNullOrBlank()) {
                if (!isMerge) {
                    logo.visibility = View.VISIBLE
                    logo.setImageResource(mediaTemplateResources.nuguLogoDefault)
                }
            } else {
                logo.updateImage(it,
                    thumbTransformCornerAlbumBadge,
                    isMerge,
                    placeHolder = mediaTemplateResources.nuguLogoPlaceHolder,
                    loadingFailImage = mediaTemplateResources.nuguLogoDefault)
            }
        }

        item.content?.run {
            albumImage.updateImage(imageUrl, thumbTransformCornerAlbumCover, isMerge)
            header.updateText(title, isMerge)
            body.updateText(subtitle1, isMerge)
            footer.updateText(subtitle2, isMerge, true)
            badgeImage.updateImage(badgeImageUrl, thumbTransformCornerAlbumBadge, isMerge)
            badgeTextView.updateText(badgeMessage, isMerge)

            if (durationSec != null) {
                progressView.isEnabled = true
                barProgress.isEnabled = true
                timeCurrent.updateText(convertToTime(0), isMerge)
            } else if (!isMerge) {
                progressView.isEnabled = false
                progressView.visibility = View.INVISIBLE
                barProgress.isEnabled = false
                barProgress.visibility = View.INVISIBLE
                timeCurrent.visibility = View.INVISIBLE
                timeEnd.visibility = View.INVISIBLE
            }

            lyrics?.title?.apply { lyricsView.setTitle(this, templateType == TemplateView.AUDIO_PLAYER_TEMPLATE_1) }

            if (lyrics != null && lyrics.lyricsType != LyricsType.NONE) {
                lyricsView.setItems(lyrics.lyricsInfoList)
                smallLyricsView.setItems(lyrics.lyricsInfoList)

                when (lyrics.lyricsType) {
                    LyricsType.SYNC -> {
                        smallLyricsView.visibility = View.VISIBLE
                        btnShowLyrics.visibility = View.GONE
                    }
                    LyricsType.NON_SYNC -> {
                        with(btnShowLyrics) {
                            visibility = View.VISIBLE
                            text = lyrics.showButton?.text ?: resources.getString(R.string.lyrics_button_default_text)
                        }
                        smallLyricsView.visibility = View.GONE
                    }
                    else -> Unit
                }
            } else if (!isMerge) {
                lyricsView.setItems(null)
                smallLyricsView.setItems(null)
                lyricPresenter.hide()
                btnShowLyrics.visibility = View.GONE
                smallLyricsView.visibility = View.GONE
            }

            barImage.updateImage(imageUrl, thumbTransformCornerAlbumBadge, isMerge)
            barTitle.updateText(title, isMerge)
            barSubtitle.updateText(subtitle1, isMerge)
        }

        item.content?.settings?.run {
            if (!isMerge) {
                btnFavorite.visibility = if (favorite != null) View.VISIBLE else View.INVISIBLE
                btnRepeat.visibility = if (repeat != null) View.VISIBLE else View.INVISIBLE
                btnShuffle.visibility = if (shuffle != null) View.VISIBLE else View.INVISIBLE
            }

            favorite?.let {
                btnFavorite.isSelected = it
            }

            repeat?.let {
                setRepeatMode(it)
                btnRepeat.setThrottledOnClickListener { v ->
                    templateHandler?.onPlayerCommand(PlayerCommand.REPEAT, (btnRepeat.getTag(R.id.iv_repeat) as? Repeat)?.name ?: it.name)
                    onViewClicked(v ?: return@setThrottledOnClickListener)
                }
            }

            shuffle?.let {
                setShuffle(it)
            }
        }

        if (expandedPlayer.visibility != View.VISIBLE) {
            collapse(true)
        }
    }

    override fun onThemeChange(theme: ThemeManagerInterface.THEME) {
        updateThemeIfNeeded()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig ?: return
        updateThemeIfNeeded()
    }

    @VisibleForTesting
    internal fun updateThemeIfNeeded() {
        fun update() {
            expandedPlayer.setBackgroundColor(if (isDark) resources.genColor(R.color.media_template_bg_dark) else bgColorLight)
            title.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_title_dark else R.color.media_template_text_title_light))
            header.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_header_dark else R.color.media_template_text_header_light))
            body.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_body_dark else R.color.media_template_text_body_light))
            footer.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_footer_dark else R.color.media_template_text_footer_light))
            progressView.setBackgroundColor(resources.genColor(if (isDark) R.color.white_40 else R.color.black_10))

            barPlayer.setBackgroundColor(if (isDark) resources.genColor(R.color.media_template_bg_dark) else bgColorLight)
            barTitle.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_header_dark else R.color.media_template_text_header_light))
            barSubtitle.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_body_dark else R.color.media_template_text_body_light))

            lyricsView.isDark = isDark
            smallLyricsView.isDark = isDark

            setShuffle(btnShuffle.isSelected)
            (btnRepeat.getTag(R.id.iv_repeat) as? Repeat)?.apply(::setRepeatMode)

            if (isDark) {
                btnCollapse.setColorFilter(resources.genColor(R.color.media_title_button_filter), PorterDuff.Mode.SRC_IN)
                btnClose.setColorFilter(resources.genColor(R.color.media_title_button_filter), PorterDuff.Mode.SRC_IN)

                btnNext.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnPrev.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnPlay.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnPause.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

                btnBarPlay.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnBarPause.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnBarPrev.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnBarNext.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            } else {
                btnCollapse.colorFilter = null
                btnClose.colorFilter = null

                btnNext.colorFilter = null
                btnPrev.colorFilter = null
                btnPlay.colorFilter = null
                btnPause.colorFilter = null

                btnBarPlay.colorFilter = null
                btnBarPause.colorFilter = null
                btnBarPrev.colorFilter = null
                btnBarNext.colorFilter = null
            }
        }

        templateHandler?.getNuguClient()?.themeManager?.run {
            val newIsDark = theme == DARK ||
                    (theme == SYSTEM && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            Logger.i(TAG, "updateThemeIfNeeded. currentTheme $theme current isDark? $isDark,  new isDark? $newIsDark")

            if (isDark != newIsDark) {
                isDark = newIsDark
                update()
            }
        }
    }

    private fun updateCurrentTimeInfo(currentTimeMs: Long, progress: Float? = null) {
        val p = progress ?: run {
            val offset = currentTimeMs.toFloat()
            val duration = mediaDurationMs.coerceAtLeast(1L)
            (offset / duration * 100f).coerceIn(0f, 100f)
        }

        timeCurrent.post {
            if (timeCurrent.visibility == View.VISIBLE) {
                timeCurrent.updateText(convertToTimeMs(currentTimeMs.toInt()), true)
            }
            progressView.progress = p.toInt()
            barProgress.progress = p.toInt()
            lyricsView.setCurrentTimeMs(currentTimeMs)
            smallLyricsView.setCurrentTimeMs(currentTimeMs)
        }
    }

    protected fun collapse(immediately: Boolean = false) {
        expandedPlayer.post {
            if (immediately) {
                expandedPlayer.y = expandedPlayer.height.toFloat()
            } else {
                expandedPlayer.animate().y(expandedPlayer.height.toFloat())
                    .setDuration(mediaTemplateResources.barPlayerTransitionDurationMs).interpolator = interpolator
            }
        }
    }

    protected fun expand(immediately: Boolean = false) {
        expandedPlayer.visibility = View.VISIBLE
        expandedPlayer.post {
            if (immediately) {
                expandedPlayer.y = 0f
            } else {
                expandedPlayer.animate().y(0f).setDuration(mediaTemplateResources.barPlayerTransitionDurationMs).interpolator = interpolator
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

    override fun getRenderInfo() = RenderInfo(lyricsView.visibility == View.VISIBLE, expandedPlayer.y != 0f)

    override fun applyRenderInfo(renderInfo: Any) {
        (renderInfo as? RenderInfo)?.run {
            if (lyricShowing) {
                lyricsView.post {
                    lyricsView.visibility = View.VISIBLE
                    lyricsView.setCurrentTimeMs(mediaCurrentTimeMs)
                }
            }

            if (!barType) {
                expand(true)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        templateHandler?.getNuguClient()?.run {
            if (audioPlayerAgent?.lyricsPresenter == lyricPresenter) {
                audioPlayerAgent?.setLyricsPresenter(EmptyLyricsPresenter)
            }

            themeManager.removeListener(this@DisplayAudioPlayer)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        templateHandler?.onTemplateTouched()
        return super.onInterceptTouchEvent(ev)
    }

    private fun setShuffle(shuffle: Boolean) {
        with(btnShuffle) {
            isSelected = shuffle
            if (isDark && !isSelected) {
                setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            } else {
                colorFilter = null
            }
        }
    }

    private fun setRepeatMode(repeat: Repeat) {
        when (repeat) {
            Repeat.ALL -> {
                btnRepeat.setImageResource(mediaTemplateResources.repeatAllResId)
                btnRepeat.colorFilter = null
            }
            Repeat.ONE -> {
                btnRepeat.setImageResource(mediaTemplateResources.repeatOneResId)
                btnRepeat.colorFilter = null
            }
            Repeat.NONE -> {
                btnRepeat.setImageResource(mediaTemplateResources.repeatNoneResId)
                if (isDark) {
                    btnRepeat.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                } else {
                    btnRepeat.colorFilter = null
                }
            }
        }

        btnRepeat.setTag(R.id.iv_repeat, repeat)
    }
}