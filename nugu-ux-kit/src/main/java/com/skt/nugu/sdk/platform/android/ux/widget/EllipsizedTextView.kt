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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class EllipsizedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val ELLIPSIS_NORMAL = "\u2026" // HORIZONTAL ELLIPSIS (…)

    init {
        setSingleLine()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val screenWidth = measuredWidth.toFloat() - compoundPaddingLeft.toFloat() - compoundPaddingRight.toFloat()
        var ellipsizedText = TextUtils.ellipsize(text, paint, screenWidth, ellipsize)

        if (ellipsizedText != text) {
            val index = ellipsizedText.indexOf(ELLIPSIS_NORMAL)
            if(index >= 0) {
                ellipsizedText = ellipsizedText.substring(index + ELLIPSIS_NORMAL.length)
            }
            text = ellipsizedText
        }
    }
}