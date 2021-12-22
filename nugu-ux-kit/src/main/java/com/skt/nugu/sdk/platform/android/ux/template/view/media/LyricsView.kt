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
package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.dpToPixel
import com.skt.nugu.sdk.platform.android.ux.template.enableMarquee
import com.skt.nugu.sdk.platform.android.ux.template.genColor
import com.skt.nugu.sdk.platform.android.ux.template.model.LyricsInfo
import kotlin.math.min

class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        private const val SIZE_SMALL = 0
        private const val SIZE_STANDARD = 1
    }

    private val lyrics: ArrayList<LyricsInfo> = ArrayList()
    private var adapter: LyricsAdapter

    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.rv_lyrics) }
    private val emptyView by lazy { findViewById<TextView>(R.id.tv_empty) }
    private val titleView by lazy { findViewById<TextView>(R.id.tv_lyrics_header) }

    private var enableAutoScroll = true

    internal var viewSize = SIZE_STANDARD
        private set

    @ColorInt
    private var bgColorLight = Color.TRANSPARENT

    @ColorInt
    private val bgColorDark = resources.genColor(R.color.media_template_bg_dark)

    @ColorInt
    private var titleFontColor = resources.genColor(R.color.media_template_text_header_light)

    @ColorInt
    private val titleFontColorDark = resources.genColor(R.color.media_template_text_header_dark)

    @ColorInt
    private var fontColorFocus = resources.genColor(R.color.media_template_lyrics_text_focus)

    @ColorInt
    private var fontColor = resources.genColor(R.color.media_template_lyrics_text)

    var isDark = false
        set(value) {
            val update = field != value
            field = value
            if (update) update()
        }

    init {
        init(attrs)

        LayoutInflater.from(context).inflate(R.layout.view_lyrics, this, true)
        adapter = LyricsAdapter(
            context,
            lyrics,
            viewSize,
            fontColor,
            fontColorFocus,
        )

        recyclerView.setOnClickListener {
            this@LyricsView.performClick()
        }

        emptyView.setOnClickListener {
            this@LyricsView.performClick()
        }

        recyclerView.layoutManager =
            object : LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                override fun canScrollVertically(): Boolean {
                    if (viewSize == SIZE_SMALL)
                        return false
                    return super.canScrollVertically()
                }
            }
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (viewSize == SIZE_STANDARD) {
                    enableAutoScroll = when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING,
                        RecyclerView.SCROLL_STATE_SETTLING,
                        -> false
                        RecyclerView.SCROLL_STATE_IDLE -> true
                        else -> enableAutoScroll
                    }
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })

        recyclerView.itemAnimator?.changeDuration = 0L
    }

    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.LyricsView, 0, 0
        ).apply {
            viewSize = getInt(R.styleable.LyricsView_sizes, SIZE_STANDARD)

            fontColor = getColor(R.styleable.LyricsView_fontColor, fontColor)
            fontColorFocus = getColor(R.styleable.LyricsView_fontColorFocus, fontColorFocus)
            titleFontColor = getColor(R.styleable.LyricsView_fontColorTitle, titleFontColor)
        }.recycle()

        (background as? ColorDrawable)?.color?.apply { bgColorLight = this }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onVisibilityChanged(this, visibility)
        }
    }

    override fun onDetachedFromWindow() {
        recyclerView.clearOnScrollListeners()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            enableAutoScroll = true
        }
    }

    fun setTitle(title: String?, marquee: Boolean = false) {
        titleView.text = title
        titleView.visibility = if (title != null) View.VISIBLE else View.INVISIBLE

        titleView.setTextColor(if (isDark) titleFontColorDark else titleFontColor)

        if (marquee) titleView.enableMarquee()
    }

    fun hasItems(): Boolean {
        return lyrics.size > 0
    }

    fun setItems(items: List<LyricsInfo>?) {
        lyrics.clear()
        items?.let {
            lyrics.addAll(items)
        }

        update()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setCurrentTimeMs(millis: Long) {
        if (visibility != View.VISIBLE) {
            return
        }

        var foundIndex = -1

        run {
            lyrics.forEachIndexed { index, lyricsInfo ->
                lyricsInfo.time ?: return@forEachIndexed
                val startTime = lyricsInfo.time
                val endTime = if (index < lyrics.size - 1) lyrics[index + 1].time ?: 0 else Int.MAX_VALUE
                if (millis in startTime until endTime) {
                    foundIndex = index
                    return@run
                }
            }
        }
        val previous = adapter.highlightedPosition
        if (foundIndex != -1 && previous != foundIndex) {
            adapter.highlightedPosition = foundIndex

            if (viewSize == SIZE_SMALL) {
                adapter.notifyDataSetChanged()
            } else {
                adapter.notifyItemChanged(foundIndex)
                if (previous in 0 until lyrics.size) adapter.notifyItemChanged(previous)
            }

            scrollToCenter(foundIndex)
        }
    }

    internal fun scrollToCenter(index: Int) {
        if (!enableAutoScroll) {
            return
        }

        recyclerView.post {
            val temp = dpToPixel(context, 31f)
            val scrollPositionOffset =
                if (viewSize == SIZE_STANDARD) recyclerView.measuredHeight / 2 - temp.toInt()
                else 0

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(index, scrollPositionOffset)
        }
    }

    fun controlPage(direction: Direction) {
        if (!enableAutoScroll || (viewSize != SIZE_STANDARD)) {
            return
        }

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

        val targetPosition =
            if (direction == Direction.NEXT) layoutManager.findLastVisibleItemPosition()
            else {
                (layoutManager.findFirstCompletelyVisibleItemPosition() - layoutManager.childCount + 1).coerceAtLeast(0)
            }

        layoutManager.scrollToPositionWithOffset(targetPosition, 0)
    }

    @SuppressLint("NotifyDataSetChanged")
    internal fun update() {
        emptyView.visibility = if (lyrics.size == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        adapter.isDark = isDark
        adapter.notifyDataSetChanged()

        setBackgroundColor(if (isDark) bgColorDark else bgColorLight)
        titleView.setTextColor(if (isDark) titleFontColorDark else titleFontColor)
    }

    class LyricsAdapter(
        val context: Context,
        private val lyrics: ArrayList<LyricsInfo>,
        private val viewSize: Int,
        @ColorInt private val fontColor: Int,
        @ColorInt private val fontColorFocus: Int,
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        @ColorInt
        private val fontColorDark = context.resources.genColor(R.color.media_template_lyrics_text_dark)

        var highlightedPosition: Int = -1
        var isDark = false

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view =
                LayoutInflater.from(context)
                    .inflate(if (viewSize == SIZE_SMALL) R.layout.view_item_small_lyrics else R.layout.view_item_lyrics, parent, false)
            view.setOnClickListener {
                parent.performClick()
            }
            return Holder(view)
        }

        override fun getItemCount(): Int {
            return if (viewSize == SIZE_SMALL) min(2, lyrics.size) else lyrics.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = holder as Holder
            val item = lyrics[position]

            // set text
            if (viewSize == SIZE_SMALL) {
                if (highlightedPosition == -1) {
                    viewHolder.line.text = item.text
                } else {
                    viewHolder.line.text = when {
                        position == 0 -> lyrics[highlightedPosition].text
                        position == 1 && highlightedPosition < lyrics.size - 1 -> lyrics[highlightedPosition + 1].text
                        else -> ""
                    }
                }
            } else {
                viewHolder.line.text = item.text
            }

            // set focus
            val highLightPos = if (viewSize == SIZE_SMALL) min(0, highlightedPosition) else highlightedPosition
            viewHolder.line.setTextColor(
                when {
                    highLightPos == position -> fontColorFocus
                    isDark -> fontColorDark
                    else -> fontColor
                }
            )
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var line: TextView = itemView.findViewById(R.id.tv_lyrics) as TextView
        }
    }
}