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
import android.graphics.Rect
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.skt.nugu.sampleapp.template.view.viewholder.TemplateViewHolder
import com.skt.nugu.sampleapp.R

abstract class AbstractDisplayList<T : View>
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayView(context, attrs, defStyleAttr) {

    val recyclerView by lazy { findViewById<RecyclerView>(R.id.recycler_view) }

    var adapter: RecyclerView.Adapter<TemplateViewHolder<T>>? = null
    set(value) {
        field = value
        recyclerView.adapter = value
    }

    companion object {
        private fun dpToPixel(context: Context, dp: Float): Int {
            return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics) + 0.5f).toInt()
        }
    }
    init {
        setContentView(R.layout.view_display_text_list_2)

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.addItemDecoration(VerticalSpaceItemDecoration(dpToPixel(context, 4F)))
    }

    inner class VerticalSpaceItemDecoration(val height : Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
            outRect.bottom = height
        }
    }

}

class DisplayTextList2
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemTextList2>(context, attrs, defStyleAttr)

class DisplayTextList4
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemTextList4>(context, attrs, defStyleAttr)

class ImageList1
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemTextList2>(context, attrs, defStyleAttr)

class DisplayImageList1
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemImageList1>(context, attrs, defStyleAttr)

class DisplayImageList2
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemImageList2>(context, attrs, defStyleAttr)

class DisplayImageList3
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractDisplayList<ItemImageList3>(context, attrs, defStyleAttr)
