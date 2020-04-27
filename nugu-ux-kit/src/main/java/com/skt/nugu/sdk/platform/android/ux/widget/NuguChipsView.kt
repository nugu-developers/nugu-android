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

import android.content.Context
import android.graphics.Rect
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * Custom Horizontal RecyclerView by Nugu Design guide.
 *
 * @sample [NuguChipsView]
 * val chipsView = findViewById(R.id.chipsView)
 * chipsView.setOnChipsClickListener(object : ChipTrayView.OnChipsClickListener {
 *   override fun onClick(text: String) {
 *   }
 * }
 * chipsView.add(Item("text", false)
 */
class NuguChipsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    class Item (val text : String, val isAction: Boolean)

    private val adapter = AdapterChips(context)
    /**
     * Listener used to dispatch click events.
     */
    private var onItemClickListener: OnItemClickListener? = null
    private val containerView by lazy {
        RecyclerView(context)
    }

    init {
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = Gravity.CENTER_VERTICAL
        addView(containerView, layoutParams)
        containerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        containerView.adapter = adapter
        containerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                when (position) {
                    0  /* first */ -> {
                        // skip
                    }
                    state.itemCount - 1 /* end */-> {
                        outRect.left = resources.getDimension(R.dimen.chips_item_margin).toInt()
                    }
                    else -> {
                        outRect.left = view.context.resources.getDimension(R.dimen.chips_item_margin).toInt()
                    }
                }
            }
        })
    }

    /**
     * Add items
     * @param list items
     */
    fun addAll(list: ArrayList<Item>) {
        adapter.items.clear()
        containerView.removeAllViews()
        adapter.items.addAll(list)
        adapter.notifyDataSetChanged()
    }

    fun size() : Int{
        return adapter.itemCount
    }
    /**
     * Add item
     * @param item item
     */
    fun addItem(item: Item) {
        adapter.items.add(item)
        adapter.notifyDataSetChanged()
    }

    fun removeAll() {
        adapter.items.clear()
        adapter.notifyDataSetChanged()
        containerView.removeAllViews()
    }
    /**
     * Provides adapter for ChipTray
     * @param context is Context
     */
    inner class AdapterChips(val context: Context) : RecyclerView.Adapter<AdapterChips.ChipsViewHolder>() {
        /** items **/
        val items: MutableList<Item> = ArrayList()
        var defaultColor: Int = 0
        var highlightColor: Int = 0
        /**
         * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item.
         */
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ChipsViewHolder {
            val viewHolder = ChipsViewHolder(LayoutInflater.from(context).inflate(R.layout.item_chip, p0, false))
            defaultColor = viewHolder.titleView.textColors.defaultColor
            highlightColor = viewHolder.titleView.highlightColor
            return viewHolder
        }

        /**
         * count of item
         */
        override fun getItemCount() = items.size

        /**
         * Called by RecyclerView to display the data at the specified position. This method should update the contents of the itemView to reflect the item at the given position.
         */
        override fun onBindViewHolder(holder: ChipsViewHolder, position: Int) {
            holder.titleView.text = items[position].text
            if(items[position].isAction) {
                holder.titleView.setTextColor(highlightColor)
            } else {
                holder.titleView.setTextColor(defaultColor)
            }
            holder.titleView.setOnClickListener {
                onItemClickListener?.onItemClick(items[position].text, items[position].isAction)
            }
        }

        /**
         * view holder
         */
        inner class ChipsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            /**
             * A textview representing the title
             * */
            val titleView: TextView = itemView.findViewById(R.id.tv_chips) as TextView
        }
    }

    /**
     * Interface definition for a callback to be invoked when a view is clicked.
     */
    interface OnItemClickListener {
        /**
         * Called when a view has been clicked.
         * @param text The text that was clicked.
         */
        fun onItemClick(text: String, isAction: Boolean)
    }

    /**
     * Register a callback to be invoked when this view is clicked. If this view is not
     * clickable, it becomes clickable.
     *
     * @param task The callback that will run
     */
    fun setOnChipsClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }
}