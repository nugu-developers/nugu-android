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
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.template.view.viewholder.TemplateViewHolder
import com.skt.nugu.sampleapp.widget.CircularProgressBar

abstract class AbstractDisplayWeather<T : View>
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, viewResId: Int) :
    AbstractDisplayView(context, attrs, defStyleAttr) {

    val recyclerView by lazy { findViewById<RecyclerView>(R.id.recycler_view) }

    var adapter: RecyclerView.Adapter<TemplateViewHolder<T>>? = null
        set(value) {
            field = value
            recyclerView.adapter = value
        }

    init {
        setContentView(viewResId)
    }

    val image by lazy { findViewById<ImageView>(R.id.iv_image) }

    val header by lazy { findViewById<TextView>(R.id.tv_header) }

    val footer by lazy { findViewById<TextView>(R.id.tv_footer) }

    val body by lazy { findViewById<TextView>(R.id.tv_body) }

    val current by lazy { findViewById<TextView>(R.id.tv_current) }

    val max by lazy { findViewById<TextView>(R.id.tv_max) }

    val min by lazy { findViewById<TextView>(R.id.tv_min) }

    val progress by lazy { findViewById<CircularProgressBar>(R.id.progress) }
}

class DisplayWeather1
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, viewResId: Int) :
    AbstractDisplayWeather<ItemWeather1>(context, attrs, defStyleAttr, viewResId)

class DisplayWeather3
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, viewResId: Int) :
    AbstractDisplayWeather<ItemWeather3>(context, attrs, defStyleAttr, viewResId)

class DisplayWeather4
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, viewResId: Int) :
    AbstractDisplayWeather<ItemWeather4>(context, attrs, defStyleAttr, viewResId)

class DisplayWeather5
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, viewResId: Int) :
    AbstractDisplayWeather<ItemWeather1>(context, attrs, defStyleAttr, viewResId)
