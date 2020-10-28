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
package com.skt.nugu.sampleapp.template

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.template.view.AbstractDisplayView
import com.skt.nugu.sampleapp.template.view.DisplayAudioPlayer
import com.skt.nugu.sampleapp.template.view.DisplayWeather3
import com.skt.nugu.sampleapp.template.view.ItemWeather3
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener

fun String.setDuration(view: DisplayAudioPlayer) {
    val duration = try {
        this.toInt()
    } catch (e: Exception) {
        0
    }
    if (duration > 0) {
        view.progress.isEnabled = true
        view.progress.max = duration
        view.bar_progress.max = duration
        view.playtime.text = TemplateUtils.convertToTime(0)
        view.fulltime.text = TemplateUtils.convertToTime(duration)
    }
}

fun Lyrics.setLyricsView(view: DisplayAudioPlayer): Boolean {
    if (this.lyricsType == null) {
        return false
    }
    if (view.lyricsView.addItems(this.lyricsInfoList)) {
        view.lyricsView.notifyDataSetChanged()
    }
    if(this.lyricsType == LyricsType.SYNC) {
        if (view.smallLyricsView.addItems(this.lyricsInfoList)) {
            view.smallLyricsView.notifyDataSetChanged()
            view.smallLyricsView.visibility = View.VISIBLE
            view.smallLyricsView.setOnClickListener {
                view.lyricsView.visibility = View.VISIBLE
            }
        }
    } else if(this.lyricsType == LyricsType.NON_SYNC) {
        view.showLyrics.visibility = View.VISIBLE
        view.showLyrics.setOnClickListener {
            view.lyricsView.visibility = View.VISIBLE
        }
    }

    return true
}

fun Background.setBackground(
    context: Context,
    view: View
) {
    TemplateUtils.parseColor(this.color)?.let { backgroundColor ->
        view.setBackgroundColor(backgroundColor)
    }
    this.image?.drawableRequest(context, Size.X_LARGE, object : RequestListener {
        override fun onResourceReady(resource: Drawable) {
            view.background = resource
        }
    })
}

fun Image.setImage(size: Size, widget: ImageView?) {
    this.sources.find(size)?.url?.setImage(widget)
}

fun String.setImage(widget: ImageView?) {
    widget?.let {
        if (it.visibility != View.VISIBLE) {
            it.visibility = View.VISIBLE
        }
        val context = widget.context
        Handler().post {
            Glide.with(context).load(this).into(it)
        }
    }
}

fun Title.setTitle(view: AbstractDisplayView) {
    this.text.setText(view.title)
    this.logo.setImage(Size.MEDIUM, view.logo)
    this.subtext?.setText(view.subtext)
    this.subicon?.setImage(Size.MEDIUM, view.subicon)
}

fun AudioPlayerTitle.setTitle(view: AbstractDisplayView) {
    this.text?.setText(view.title)
    this.iconUrl?.setImage(view.logo)
}

fun AudioPlayerContent.setBarContent(view: DisplayAudioPlayer) {
    imageUrl?.setImage(view.bar_image)
    title?.setText(view.bar_header)
    subtitle1?.setText(view.bar_body)
}

fun AudioPlayerContent.setContent(view: DisplayAudioPlayer) {
    imageUrl?.setImage(view.image)
    title?.setText(view.header)
    subtitle1?.setText(view.body)
    subtitle2?.setText(view.footer)
    badgeImageUrl?.setImage(view.badgeImage)
    badgeMessage?.setText(view.badgeMessage)
    durationSec?.setDuration(view)
    lyrics?.setLyricsView(view)
    view.lyricsView.setTitle(title)
}

fun Weather1Content.setContent(view: DisplayWeather3) {
    image?.setImage(Size.MEDIUM, view.image)
    header?.setText(view.header)
    body?.setText(view.body)
}

fun Weather1Item.setTemperature(view: ItemWeather3) {
    temperature?.min?.setText(view.min)
    temperature?.max?.setText(view.max)
}

fun List<Text>.setText(widget: TextView) {
    if (this.isEmpty()) {
        return
    }
    val builder = StringBuilder()
    this.forEachIndexed { index, value ->
        if (index != 0) {
            builder.append("\n")
        }
        builder.append(value.text)
        value.setColor(widget)
        value.setStyle(widget)
    }
    builder.toString().setText(widget)
}

fun ToggleButton.setToggle(
    context: Context,
    toggleStyle: ToggleStyle?,
    widget: android.widget.ToggleButton?
) {
    if (toggleStyle == null) {
        return
    }

    if (this.style == "text") {
        widget?.setBackgroundResource(R.drawable.template_text_button_selector)
        widget?.textOn = toggleStyle.text?.on?.text
        widget?.textOff = toggleStyle.text?.off?.text
        widget?.isChecked = this.status == ToggleStatus.ON
        widget?.visibility = View.VISIBLE
    } else if (this.style == "image") {
        TemplateUtils.updateLayoutParams(widget, 40F, 40F)

        when (this.status) {
            ToggleStatus.ON -> toggleStyle.image?.on?.drawableRequest(
                context,
                Size.MEDIUM,
                object : RequestListener {
                    override fun onResourceReady(resource: Drawable) {
                        widget?.background = resource
                        widget?.visibility = View.VISIBLE
                    }
                })
            ToggleStatus.OFF -> toggleStyle.image?.off?.drawableRequest(
                context,
                Size.MEDIUM,
                object : RequestListener {
                    override fun onResourceReady(resource: Drawable) {
                        widget?.background = resource
                        widget?.visibility = View.VISIBLE
                    }
                })
        }
    }
}

fun Boolean.setBadge(position: Int, widget: TextView?) {
    val isShown = this
    if (isShown) {
        widget?.text = (position + 1).toString()
        widget?.visibility = View.VISIBLE
    }
}

fun Button.setButton(templateId: String, widget: TextView) {
    if (this.text.isNullOrEmpty()) {
        return
    }

    widget.visibility = View.VISIBLE
    widget.text = this.text
    widget.setThrottledOnClickListener {
        TemplateViews.handleOnClickEvent(
            eventType = this.eventType,
            textInput = this.textInput,
            templateId = templateId,
            token = this.token,
            postback = this.postback
        )
    }
}

fun Text.setText(widget: TextView) {
    if (this.text.isNullOrEmpty()) {
        return
    }
    this.text.setText(widget)
    this.setColor(widget)
    this.setStyle(widget)
}

private fun Text.setColor(widget: TextView?) {
    TemplateUtils.parseColor(this.color)?.let {
        widget?.setTextColor(it)
    }
}

private fun String.setText(widget: TextView?) {
    if(this.isEmpty()) {
        return
    }
    widget?.visibility = View.VISIBLE
    widget?.text = TemplateUtils.getSpannable(this)
}

private fun Text.setStyle(widget: TextView) {
    this.style?.apply {
        when (align) {
            TextAlign.LEFT -> {
                widget.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            }
            TextAlign.CENTER -> {
                widget.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            TextAlign.RIGHT -> {
                widget.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
        }
        opacity?.let {
            widget.alpha = it
        }
        display?.let {
            widget.visibility = if (it == Display.NONE) View.GONE else View.VISIBLE
        }
    }
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
