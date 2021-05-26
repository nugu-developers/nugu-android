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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.skt.nugu.sdk.platform.android.ux.template.model.Image
import com.skt.nugu.sdk.platform.android.ux.template.model.Size
import com.skt.nugu.sdk.platform.android.ux.template.model.Source

fun TextView.updateText(text: String?, isMerge: Boolean = false, maintainLayout: Boolean = false) {
    if (isMerge && text.isNullOrBlank()) return

    this.text = TemplateUtils.getSpannable(text)
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
    Glide.with(context).load(url).apply {
        if (placeHolder != null) placeholder(placeHolder)
        if (loadingFailImage != null) error(loadingFailImage)
        if (transformation != null) transform(transformation)
    }.into(this)
}

private fun String.setText(widget: TextView?) {
    if (this.isEmpty()) {
        return
    }
    widget?.visibility = View.VISIBLE
    widget?.text = TemplateUtils.getSpannable(this)
}

interface RequestListener {
    fun onResourceReady(resource: Drawable)
}

fun Image.drawableRequest(
    context: Context?,
    size: Size,
    requestListener: RequestListener
) {
    if (context == null) {
        return
    }

    this.sources.find(size)?.let {
        Handler().post {
            Glide.with(context)
                .load(it.url)
                .into(object : CustomTarget<Drawable>() {
                    override fun onLoadCleared(placeholder: Drawable?) {
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        requestListener.onResourceReady(resource)
                    }
                })
        }
    }
}

private fun List<Source>.find(preferred: Size): Source? {
    if (this.isEmpty()) {
        return null
    }

    val enumsOfSize = Size.values()
    val sources = Array<Source?>(enumsOfSize.size) { null }
    this.forEach { source ->
        source.size?.let {
            sources[it.ordinal] = source
        }
    }

    val start = preferred.ordinal
    sources[start]?.let {
        return it
    }

    var little = start - 1
    var big = start + 1
    while (true) {
        if (big < enumsOfSize.size) {
            sources[big]?.let {
                return it
            } ?: big++
        }

        if (little >= 0) {
            sources[little]?.let {
                return it
            } ?: little--
        }

        if (little < 0 && big >= enumsOfSize.size) {
            return firstOrNull()
        }
    }
}

fun TextView.enableMarquee() {
    this.setSingleLine()
    this.ellipsize = TextUtils.TruncateAt.MARQUEE
    this.isSelected = true
}
