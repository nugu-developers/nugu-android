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

import android.os.SystemClock
import android.view.View

/**
 * An implementation of OnClickListener that prevent multiple rapid clicks.
 */
class ThrottledOnClickListener(
    private val listener: (view: View?) -> Unit,
    private val throttleInMillis: Long
) : View.OnClickListener {
    private var previousMillis = 0L
    override fun onClick(v: View?) {
        val currentMillis = System.currentTimeMillis()
        if (currentMillis - previousMillis < throttleInMillis) {
            return
        }
        previousMillis = currentMillis
        listener(v)
    }
}

fun View.setThrottledOnClickListener(listener: (view: View?) -> Unit) =
    setOnClickListener(
        ThrottledOnClickListener(listener = listener, throttleInMillis = 1000L))