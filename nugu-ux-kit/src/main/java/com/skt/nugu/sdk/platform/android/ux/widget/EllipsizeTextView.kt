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
import android.util.AttributeSet
import android.widget.TextView

class EllipsizeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {
    companion object {
        private val maxTextSize = 12
        private val ellipseText = "..."
    }
    init {
        setSingleLine()
    }

    fun setEllipsizeText(text: CharSequence) {
        var newText = text
        if (newText.length > maxTextSize) {
            newText = newText.substring(0, maxTextSize) + ellipseText
        }
        super.setText(newText)
    }
}