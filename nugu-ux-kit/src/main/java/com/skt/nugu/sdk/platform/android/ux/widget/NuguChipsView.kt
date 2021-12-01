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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.animation.ArgbEvaluator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.platform.android.ux.R


/**
 * Custom Horizontal RecyclerView by Nugu Design guide.
 *
 * @sample [NuguChipsView]
 * val chipsView = findViewById(R.id.chipsView)
 * chipsView.setOnChipsListener(object : NuguChipsView.OnChipsListener {
 *   override fun onClick(item: NuguChipsView.Item) {
 *   }
 *   override fun onScrolled(dx: Int, dy: Int) {
 *   }
 * })
 * chipsView.addItem(Item("text", false))
 */
class NuguChipsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    class Item(val text: String, val type: Chip.Type)
    companion object {
        private val DEFAULT_TEXT_COLOR = Color.parseColor("#404858")
        private val DEFAULT_HIGHLIGHT_TEXT_COLOR = Color.parseColor("#009DFF")
        private val DEFAULT_DRAWABLE_RESOURCE_ID = R.drawable.nugu_chips_button_light_selector
    }

    @VisibleForTesting
    internal var adapter = AdapterChips(context)

    private var defaultColor: Int = DEFAULT_TEXT_COLOR
    private var highlightColor: Int = DEFAULT_HIGHLIGHT_TEXT_COLOR
    private var defaultDrawableId: Int = DEFAULT_DRAWABLE_RESOURCE_ID
    private var isDark = false

    /**
     * Sets the maximum number of texts in the chips
     */
    var maxTextSize: Int = 25

    /**
     * Listener used to dispatch click events.
     */
    private var listener: OnChipsListener? = null
    private val containerView by lazy {
        RecyclerView(context)
    }

    val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            listener?.onScrolled(dx, dy)
        }
    }

    init {
        this.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        layoutParams.addRule(CENTER_VERTICAL)
        addView(containerView, layoutParams)
        containerView.isNestedScrollingEnabled = false
        containerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        containerView.adapter = adapter
        containerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                val position = parent.getChildAdapterPosition(view)
                when (position) {
                    0  /* first */ -> {
                        // skip
                    }
                    state.itemCount - 1 /* end */ -> {
                        outRect.left = resources.getDimension(R.dimen.chips_item_margin).toInt()
                        outRect.right = resources.getDimension(R.dimen.chips_item_margin).toInt()
                    }
                    else -> {
                        outRect.left =
                            view.context.resources.getDimension(R.dimen.chips_item_margin).toInt()
                    }
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        containerView.addOnScrollListener(onScrollListener)
    }

    override fun onDetachedFromWindow() {
        containerView.removeOnScrollListener(onScrollListener)
        adapter.clear()
        super.onDetachedFromWindow()
    }

    fun addAll(list: ArrayList<Item>) {
        adapter.items.clear()
        containerView.removeAllViews()
        adapter.items.addAll(list)
        adapter.notifyDataSetChanged()
        adapter.startNudgeAnimationIfNeeded()
    }

    fun size(): Int {
        return adapter.itemCount
    }

    /**
     * Add item
     * @param item item
     */
    fun addItem(item: Item) {
        adapter.items.add(item)
        adapter.notifyDataSetChanged()
        adapter.startNudgeAnimationIfNeeded()
    }

    fun removeAll() {
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        containerView.removeAllViews()
        adapter.stopNudgeAnimationIfNeeded()
    }

    fun onVoiceChromeHidden() {
        adapter.stopNudgeAnimationIfNeeded()
    }

    /**
     * Provides adapter for ChipTray
     * @param context is Context
     */
    inner class AdapterChips(val context: Context) :
        RecyclerView.Adapter<AdapterChips.ChipsViewHolder>() {
        /** items **/
        val items: MutableList<Item> = ArrayList()

        // for nudge border
        private val start = Color.parseColor("#009dff")
        private val end = Color.parseColor("#00e688")
        private val evaluator = ArgbEvaluator()
        private val animator = TimeAnimator.ofFloat(0.0f, 1.0f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener {
                for (index in 0 until containerView.childCount) {
                    containerView.getChildViewHolder(containerView.getChildAt(index)).let { viewHolder ->
                        if (viewHolder.itemViewType == Chip.Type.NUDGE.ordinal) {
                            ((((viewHolder as ChipsViewHolder).titleView.background as? StateListDrawable)?.current as? LayerDrawable)
                                ?.getDrawable(0) as? GradientDrawable)?.run {
                                val fraction = it.animatedFraction
                                val newStart = evaluator.evaluate(fraction, start, end) as Int
                                val newEnd = evaluator.evaluate(fraction, end, start) as Int

                                colors = intArrayOf(newStart, newEnd)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item.
         */
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ChipsViewHolder {
            val layout = if (viewType == Chip.Type.NUDGE.ordinal) R.layout.item_text_nudge else R.layout.item_text
            return ChipsViewHolder(LayoutInflater.from(context).inflate(layout, viewGroup, false))
        }

        /**
         * count of item
         */
        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int): Int {
            return items[position].type.ordinal
        }

        /**
         * Called by RecyclerView to display the data at the specified position. This method should update the contents of the itemView to reflect the item at the given position.
         */
        override fun onBindViewHolder(holder: ChipsViewHolder, position: Int) {
            holder.titleView.setEllipsizeText(items[position].text, maxTextSize)

            when (items[position].type) {
                Chip.Type.NUDGE -> {
                    holder.titleView.setTextColor(defaultColor)
                    holder.titleView.setBackgroundResource(if (isDark) R.drawable.nugu_chips_nudge_border_dark else R.drawable.nugu_chips_nudge_border_light)
                }
                Chip.Type.ACTION -> {
                    holder.titleView.setTextColor(highlightColor)
                    holder.titleView.setBackgroundResource(defaultDrawableId)
                }
                else -> {
                    holder.titleView.setTextColor(defaultColor)
                    holder.titleView.setBackgroundResource(defaultDrawableId)
                }
            }

        }

        fun startNudgeAnimationIfNeeded() {
            if (items.any { it.type == Chip.Type.NUDGE }) animator.start()
        }

        fun stopNudgeAnimationIfNeeded() {
            if (animator.isRunning || animator.isStarted) animator.cancel()
        }

        fun clear() {
            animator.removeAllUpdateListeners()
        }

        /**
         * view holder
         */
        inner class ChipsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            /**
             * A textview representing the title
             * */
            val titleView: TextView = itemView.findViewById(R.id.tv_chips)

            init{
                titleView.setThrottledOnClickListener {
                    listener?.onClick(items[adapterPosition])
                }
            }
        }
    }

    /**
     * Interface definition for a callback to be invoked when a view is clicked.
     */
    interface OnChipsListener {
        /**
         * Called when a view has been clicked.
         * @param item The item that was clicked.
         */
        fun onClick(item: Item)
        fun onScrolled(dx: Int, dy: Int)
    }

    /**
     * Register a callback to be invoked when this view is clicked. If this view is not
     * clickable, it becomes clickable.
     *
     * @param task The callback that will run
     */
    fun setOnChipsListener(listener: OnChipsListener) {
        this.listener = listener
    }


    fun TextView.setEllipsizeText(text: CharSequence, maxTextSize: Int) {
        var newText = text
        if (newText.length > maxTextSize) {
            newText = newText.substring(0, maxTextSize - 1) + Typography.ellipsis
        }
        this.text = newText
    }

    private fun applyThemeAttrs(@StyleRes resId: Int) {
        val attrs = intArrayOf(android.R.attr.textColor, android.R.attr.textColorHighlight, android.R.attr.background)
        val a: TypedArray = context.obtainStyledAttributes(resId, attrs)
        try {
            attrs.forEachIndexed { index, value ->
                when (value) {
                    android.R.attr.textColor -> defaultColor = a.getColor(index, DEFAULT_TEXT_COLOR)
                    android.R.attr.textColorHighlight -> highlightColor = a.getColor(index, DEFAULT_HIGHLIGHT_TEXT_COLOR)
                    android.R.attr.background -> defaultDrawableId = a.getResourceId(index, DEFAULT_DRAWABLE_RESOURCE_ID)
                }
            }
        } finally {
            a.recycle()
        }
        adapter.notifyDataSetChanged()
    }

    /**
     * Sets the dark mode.
     * @param darkMode the dark mode to set
     */
    fun setDarkMode(darkMode: Boolean) {
        isDark = darkMode
        applyThemeAttrs(
            when (darkMode) {
                true -> R.style.Nugu_Widget_Chips_Dark
                false -> R.style.Nugu_Widget_Chips_Light
            }
        )
    }
}