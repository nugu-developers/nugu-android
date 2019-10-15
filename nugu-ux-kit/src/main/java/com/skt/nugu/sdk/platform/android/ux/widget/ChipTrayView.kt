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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.ux.R

/**
 * Custom Horizontal RecyclerView by Nugu Design guide.
 *
 * @sample [ChipTrayView]
 * val chipTray = findViewById(R.id.chipTray)
 * chipTray.addAll(resources.getStringArray(R.array.chips))
 * chipTray.setOnChipsClickListener(object : ChipTrayView.OnChipsClickListener {
 *   override fun onClick(text: String) {
 *   }
 * }
 */
class ChipTrayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private val adapter = AdapterChipTray(context)
    private val tray by lazy {
        RecyclerView(context)
    }

    init {
        addView(tray, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        tray.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        tray.adapter = adapter
        tray.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                when (position) {
                    0 -> {
                        outRect.left = view.context.resources.getDimension(R.dimen.chiptray_start_margin).toInt()
                        outRect.right = view.context.resources.getDimension(R.dimen.chiptray_item_margin).toInt()
                    }
                    state.itemCount - 1 -> {
                        outRect.left = view.context.resources.getDimension(R.dimen.chiptray_item_margin).toInt()
                        outRect.right = view.context.resources.getDimension(R.dimen.chiptray_start_margin).toInt()
                    }
                    else -> {
                        outRect.left = view.context.resources.getDimension(R.dimen.chiptray_item_margin).toInt()
                        outRect.right = view.context.resources.getDimension(R.dimen.chiptray_item_margin).toInt()
                    }
                }
            }
        })
    }

    /**
     * Add items
     * @param list items
     */
    fun addAll(list: Array<String>) {
        list.forEach {
            adapter.items.add(it)
        }
    }

    /**
     * Provides adapter for ChipTray
     * @param context is Context
     */
    class AdapterChipTray(val context: Context) : RecyclerView.Adapter<AdapterChipTray.ChipTrayViewHolder>() {
        /** items **/
        val items: MutableList<String> = ArrayList()
        /**
         * Listener used to dispatch click events.
         */
        var onChipsClickListener: OnChipsClickListener? = null

        /**
         * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item.
         */
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ChipTrayViewHolder {
            return ChipTrayViewHolder(LayoutInflater.from(context).inflate(R.layout.item_chip, p0, false))
        }

        /**
         * count of item
         */
        override fun getItemCount() = items.size

        /**
         * Called by RecyclerView to display the data at the specified position. This method should update the contents of the itemView to reflect the item at the given position.
         */
        override fun onBindViewHolder(holder: ChipTrayViewHolder, position: Int) {
            holder.titleView.text = items[position]
            holder.titleView.setOnClickListener {
                onChipsClickListener?.onClick(items[position])
            }
        }

        /**
         * view holder
         */
        inner class ChipTrayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            /**
             * A textview representing the title
             * */
            val titleView: TextView = itemView.findViewById(R.id.tv_chips) as TextView
        }
    }

    /**
     * Interface definition for a callback to be invoked when a view is clicked.
     */
    interface OnChipsClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param text The text that was clicked.
         */
        fun onClick(text: String)
    }

    /**
     * Register a callback to be invoked when this view is clicked. If this view is not
     * clickable, it becomes clickable.
     *
     * @param task The callback that will run
     */
    fun setOnChipsClickListener(task: OnChipsClickListener) {
        adapter.onChipsClickListener = task
    }
}