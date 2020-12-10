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
package com.skt.nugu.sdk.platform.android.ux.template.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener

abstract class TemplateNativeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RelativeLayout(context, attrs, defStyleAttr), TemplateView {

    val logoView by lazy { findViewById<ImageView>(R.id.iv_logo) }

    val titleView by lazy { findViewById<TextView>(R.id.tv_title) }

    val close by lazy { findViewById<ImageView>(R.id.btn_close) }

    val collapsed by lazy { findViewById<ImageView>(R.id.btn_collapsed) }

    protected fun setContentView(layout: Int) {
        LayoutInflater.from(context).inflate(layout, this, true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        close.setThrottledOnClickListener {
            templateHandler?.onCloseClicked()
        }
    }
}