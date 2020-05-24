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
package com.skt.nugu.sampleapp.widget

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.OnScrollListener
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.template.LyricsInfo

class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val SIZE_SMALL = 0
        val SIZE_STANDARD = 1

        private fun dpToPixel(context: Context, dp: Float): Float {
            return (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics
            ) + 0.5f)
        }
    }

    private val lyrics: ArrayList<LyricsInfo> = ArrayList()
    private var adapter: LyricsAdapter

    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.rv_lyrics) }
    private val emptyView by lazy { findViewById<TextView>(R.id.tv_empty) }
    private val titleView by lazy { findViewById<TextView>(R.id.tv_header) }

    private var enableAutoScroll = true

    private var viewSize = SIZE_STANDARD

    init {
        init(attrs)

        LayoutInflater.from(context).inflate(R.layout.view_lyrics, this, true)
        adapter = LyricsAdapter(
            context,
            lyrics,
            if (viewSize == SIZE_STANDARD) R.layout.view_item_lyrics else R.layout.view_item_small_lyrics
        )
        recyclerView.layoutManager =
            object : LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                override fun canScrollVertically(): Boolean {
                    if (viewSize == SIZE_SMALL)
                        return false
                    return super.canScrollVertically()
                }
            }
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (viewSize == SIZE_STANDARD) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        enableAutoScroll = false
                    }
                }
                super.onScrollStateChanged(recyclerView, newState)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
            }
        })
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (viewSize == SIZE_SMALL) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
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

    fun setTitle(title: String?) {
        title?.let {
            this.titleView.text = title
            this.titleView.visibility = View.VISIBLE
            this.titleView.isSelected = true
        }
    }

    fun hasItems(): Boolean {
        return lyrics.size > 0
    }

    fun addItems(items: List<LyricsInfo>?): Boolean {
        items?.let {
            return lyrics.addAll(items)
        }
        return false
    }

    fun setCurrentTime(millis: Int) {
        if (visibility != View.VISIBLE) {
            return
        }
        val seconds = millis * 1000

        var foundIndex = -1
        lyrics.forEachIndexed { index, it ->
            if (seconds < it.time ?: 0 && foundIndex == -1) {
                foundIndex = index - 1
            }
        }

        if (foundIndex != -1) {
            adapter.setHighlighted(foundIndex)
            adapter.notifyDataSetChanged()
            scrollToCenter(foundIndex)
        }
    }

    private fun scrollToCenter(index: Int) {
        if (!enableAutoScroll) {
            return
        }
        val temp = dpToPixel(context, 31f)
        val scrollPositionOffset =
            if (viewSize == SIZE_STANDARD) recyclerView.height / 2 - temp.toInt()
            else 0

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(index, scrollPositionOffset)
    }

    fun notifyDataSetChanged() {
        emptyView.visibility = if (lyrics.size == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        adapter.notifyDataSetChanged()
    }

    class LyricsAdapter(
        val context: Context?,
        val lyrics: ArrayList<LyricsInfo>,
        val resourceId: Int
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var highlightedPosition: Int = -1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(context)
                .inflate(resourceId, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int {
            return lyrics.size
        }

        override fun getItemViewType(position: Int): Int {
            return super.getItemViewType(position)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = holder as Holder
            val item = lyrics.get(position)
            viewHolder.line.text = item.text
            viewHolder.line.setTextColor(
                if (highlightedPosition == position) {
                    Color.parseColor("#009dff")
                } else {
                    Color.parseColor("#444444")
                }
            )
        }

        fun setHighlighted(position: Int) {
            highlightedPosition = position
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var line: TextView

            init {
                line = itemView.findViewById(R.id.tv_lyrics) as TextView
            }
        }
    }
}