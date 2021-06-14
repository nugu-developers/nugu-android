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

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.dpToPixel
import com.skt.nugu.sdk.platform.android.ux.template.enableMarquee
import com.skt.nugu.sdk.platform.android.ux.template.genColor
import com.skt.nugu.sdk.platform.android.ux.template.model.LyricsInfo

class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
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
            viewSize
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
                        RecyclerView.SCROLL_STATE_SETTLING
                        -> false
                        RecyclerView.SCROLL_STATE_IDLE -> true
                        else -> enableAutoScroll
                    }
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })
    }

    private fun init(attrs: AttributeSet?) {
        context.obtainStyledAttributes(
            attrs, R.styleable.LyricsView, 0, 0
        ).apply {
            viewSize = getInt(R.styleable.LyricsView_sizes, SIZE_STANDARD)
        }.recycle()
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

    fun addItem(item: LyricsInfo) {
        lyrics.add(item)
    }

    fun setTitle(title: String?, marquee: Boolean = false) {
        titleView.text = title
        titleView.visibility = if (title != null) View.VISIBLE else View.INVISIBLE

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

    fun setCurrentTimeMs(millis: Long) {
        if (visibility != View.VISIBLE) {
            return
        }

        var foundIndex = -1
        lyrics.forEachIndexed { index, it ->
            if (millis < it.time ?: 0 && foundIndex == -1) {
                foundIndex = index - 1
            }
        }

        if (foundIndex != -1) {
            adapter.setHighlighted(foundIndex)
            adapter.notifyDataSetChanged()
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

    internal fun update() {
        emptyView.visibility = if (lyrics.size == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        adapter.isDark = isDark
        adapter.notifyDataSetChanged()

        setBackgroundColor(resources.genColor(if (isDark) R.color.media_template_bg_dark else R.color.media_template_bg_light))
        titleView.setTextColor(resources.genColor(if (isDark) R.color.media_template_text_header_dark else R.color.media_template_text_header_light))
    }

    class LyricsAdapter(
        val context: Context?,
        private val lyrics: ArrayList<LyricsInfo>,
        private val viewSize: Int
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var highlightedPosition: Int = -1
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
            return lyrics.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = holder as Holder
            val item = lyrics.get(position)
            viewHolder.line.text = item.text
            viewHolder.line.setTextColor(
                if (highlightedPosition == position) {
                    Color.parseColor("#009dff")
                } else {
                    getItemViewType(position)
                    if (viewSize == SIZE_STANDARD && !isDark) {
                        Color.parseColor("#444444")
                    } else {
                        Color.parseColor("#888888")
                    }
                }
            )
        }

        fun setHighlighted(position: Int) {
            highlightedPosition = position
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var line: TextView = itemView.findViewById(R.id.tv_lyrics) as TextView
        }
    }
}