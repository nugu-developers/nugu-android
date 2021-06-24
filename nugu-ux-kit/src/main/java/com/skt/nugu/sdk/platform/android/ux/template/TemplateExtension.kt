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
package com.skt.nugu.sdk.platform.android.ux.template

import android.graphics.Bitmap
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy

fun TextView.updateText(text: String?, isMerge: Boolean = false, maintainLayout: Boolean = false) {
    if (isMerge && text.isNullOrBlank()) return

    this.text = getSpannable(text)
    visibility = if (text != null) {
        View.VISIBLE
    } else {
        if (maintainLayout) View.INVISIBLE else View.GONE
    }
}

fun ImageView.updateImage(
    url: String?,
    transformation: Transformation<Bitmap>?,
    isMerge: Boolean = false,
    @DrawableRes placeHolder: Int? = null,
    @DrawableRes loadingFailImage: Int? = null
) {
    if (isMerge && url.isNullOrBlank()) return

    visibility = if (url != null) View.VISIBLE else View.GONE

    post {
        Glide.with(context).load(url)
            .override(measuredWidth, measuredHeight)
            .apply {
                if (placeHolder != null) placeholder(placeHolder)
                if (loadingFailImage != null) error(loadingFailImage)
                if (transformation != null) transform(transformation)
            }
            .into(this)
    }
}

fun TextView.enableMarquee() {
    this.setSingleLine()
    this.ellipsize = TextUtils.TruncateAt.MARQUEE
    this.isSelected = true
}
