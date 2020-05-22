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
package com.skt.nugu.sampleapp.template

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sampleapp.template.view.ItemTextList2
import com.skt.nugu.sampleapp.template.view.viewholder.TemplateViewHolder
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.template.view.AbstractDisplayText
import com.skt.nugu.sampleapp.template.view.BaseView
import com.skt.nugu.sampleapp.template.view.DisplayAudioPlayer
import com.skt.nugu.sdk.agent.display.ElementSelectedHandler

class TemplateViews {
    companion object {
        private const val TAG = "TemplateViews"

        private const val AUDIO_PLAYER_TEMPLATE_1 = "AudioPlayer.Template1"
        private const val AUDIO_PLAYER_TEMPLATE_2 = "AudioPlayer.Template2"
        private const val DISPLAY_DEFAULT = "Display.Default"
        private const val DISPLAY_FULL_TEXT_1 = "Display.FullText1"
        private const val DISPLAY_FULL_TEXT_2 = "Display.FullText2"
        private const val DISPLAY_IMAGE_TEXT_1 = "Display.ImageText1"
        private const val DISPLAY_IMAGE_TEXT_2 = "Display.ImageText2"
        private const val DISPLAY_IMAGE_TEXT_3 = "Display.ImageText3"
        private const val DISPLAY_IMAGE_TEXT_4 = "Display.ImageText4"
        private const val DISPLAY_TEXT_LIST_1 = "Display.TextList1"
        private const val DISPLAY_TEXT_LIST_2 = "Display.TextList2"
        private const val DISPLAY_TEXT_LIST_3 = "Display.TextList3"
        private const val DISPLAY_IMAGE_LIST_1 = "Display.ImageList1"
        private const val DISPLAY_IMAGE_LIST_2 = "Display.ImageList2"

        private val gson = Gson()

        fun createView(context: Context, name: String, displayId: String, template: String): BaseView {
            Log.d(TAG, "[createView] name: $name, template: $template")

            return when (name) {
                AUDIO_PLAYER_TEMPLATE_1,
                AUDIO_PLAYER_TEMPLATE_2 -> DisplayAudioPlayer(context).apply {
                    if(name == AUDIO_PLAYER_TEMPLATE_2) {
                        body.visibility = View.GONE
                        footer.visibility = View.GONE
                    }

                    fromJsonOrNull(template, AudioPlayer::class.java)?.let { item ->
                        item.title.text?.let {
                            title.text = it
                        }

                        val iconUrl = item.title.iconUrl
                        if(iconUrl == null) {
                            logo.visibility = View.GONE
                        } else {
                            logo.visibility = View.VISIBLE
                            Glide.with(logo).load(iconUrl).into(logo)
                        }

                        item.content.imageUrl?.let {
                            Glide.with(image).load(it).into(image)
                        }

                        item.content.title?.let {
                            header.text = it
                        }

                        item.content.subtitle1?.let {
                            body.text = it
                        }

                        item.content.subtitle2?.let {
                            footer.text = it
                        }

                        val duration = try {
                            item.content.durationSec?.toInt()
                        } catch (e: Exception) {
                            null
                        }

                        if (duration == null) {
                            progress.isEnabled = false
                        } else {
                            progress.isEnabled = true
                            progress.max = duration
                        }

                        item.content.backgroundColor?.let {
                            background.setBackgroundColor(parseColor(it, ContextCompat.getColor(context, R.color.white)))
                        }
                        item.content.backgroundImageUrl?.let {
                            Glide.with(this).load(it).into(background)
                        }

                        prev.setOnClickListener {
                            ClientManager.getClient().getPlaybackRouter().buttonPressed(
                                PlaybackButton.PREVIOUS)
                        }

                        play.setOnClickListener {
                            if(ClientManager.playerActivity == AudioPlayerAgentInterface.State.PLAYING) {
                                ClientManager.getClient().getPlaybackRouter().buttonPressed(
                                    PlaybackButton.PAUSE)
                            } else {
                                ClientManager.getClient().getPlaybackRouter().buttonPressed(
                                    PlaybackButton.PLAY)
                            }
                        }

                        next.setOnClickListener {
                            ClientManager.getClient().getPlaybackRouter().buttonPressed(
                                PlaybackButton.NEXT)
                        }
                    }
                }
                DISPLAY_DEFAULT,
                DISPLAY_IMAGE_TEXT_1,
                DISPLAY_IMAGE_TEXT_4,
                DISPLAY_FULL_TEXT_2 -> object : com.skt.nugu.sampleapp.template.view.AbstractDisplayText(context){
                    override val viewResId: Int
                        get() = R.layout.view_display_image_text_1
                }.apply {
                    applyDisplayTextData(template, this)
                }
                DISPLAY_FULL_TEXT_1,
                DISPLAY_IMAGE_TEXT_2,
                DISPLAY_IMAGE_TEXT_3 -> object : com.skt.nugu.sampleapp.template.view.AbstractDisplayText(context){
                    override val viewResId: Int
                        get() = R.layout.view_display_image_text_2
                }.apply {
                    applyDisplayTextData(template, this)
                }
                DISPLAY_TEXT_LIST_1,
                DISPLAY_TEXT_LIST_2,
                DISPLAY_IMAGE_LIST_1 -> com.skt.nugu.sampleapp.template.view.DisplayTextList2(context).apply {
                    fromJsonOrNull(template, TextList2::class.java)?.let { textList1 ->
                        setTitle(this.logo, this.title, textList1.title)

                        this.adapter = object : RecyclerView.Adapter<TemplateViewHolder<ItemTextList2>>() {

                            override fun onCreateViewHolder(
                                parent: ViewGroup,
                                viewType: Int
                            ): TemplateViewHolder<ItemTextList2> {
                                return TemplateViewHolder(
                                    ItemTextList2(
                                        parent.context
                                    )
                                ).apply {
                                    view.setOnClickListener {
                                        // no-op
                                    }
                                }
                            }

                            override fun getItemCount(): Int {
                                return textList1.listItems.size
                            }

                            override fun onBindViewHolder(holder: TemplateViewHolder<ItemTextList2>, position: Int) {
                                textList1.listItems[position].let { item ->
                                    if (textList1.badgeNumber == true) {
                                        holder.view.badge.text = (position + 1).toString()
                                        holder.view.badge.visibility = View.VISIBLE
                                    } else {
                                        holder.view.badge.visibility = View.GONE
                                    }

                                    val source = item.image?.sources?.find(Size.MEDIUM)
                                    if (source != null) {
                                        holder.view.image.visibility = View.VISIBLE
                                        Glide.with(holder.view.image).load(source.url).into(holder.view.image)
                                    } else {
                                        holder.view.image.visibility = View.GONE
                                    }

                                    val header = item.header
                                    if(header != null) {
                                        holder.view.header.visibility = View.VISIBLE
                                        setText(holder.view.header, header)
                                    } else {
                                        holder.view.header.visibility = View.GONE
                                    }

                                    item.body?.let {
                                        setText(holder.view.body, it)
                                    }

                                    item.footer?.let {
                                        setText(holder.view.footer, it)
                                    }

                                    holder.view.setOnClickListener {
                                        try {
                                            ClientManager.getClient().getDisplay()
                                                ?.setElementSelected(
                                                    displayId,
                                                    item.token,
                                                    null,
                                                    null,
                                                    object :
                                                        ElementSelectedHandler.OnElementSelectedCallback {
                                                        override fun onSuccess(dialogRequestId: String) {
                                                            Log.d(
                                                                TAG,
                                                                "[setElementSelected::onSuccess] dialogRequestId: $dialogRequestId"
                                                            )
                                                        }

                                                        override fun onError(
                                                            dialogRequestId: String,
                                                            errorType: ElementSelectedHandler.ErrorType
                                                        ) {
                                                            Log.d(
                                                                TAG,
                                                                "[setElementSelected::onError] dialogRequestId: $dialogRequestId / errorType: $errorType"
                                                            )
                                                        }
                                                    }
                                                )
                                        } catch (e: IllegalStateException) {
                                            Log.w(TAG, "[setElementSelected]", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                else -> object : BaseView(context) {}
            }
        }

        private fun applyDisplayTextData(template: String, displayTextView: AbstractDisplayText) {
            fromJsonOrNull(template, ImageText2::class.java)?.let { item ->
                with(displayTextView) {
                    item.background?.let {
                        setBackground(this, it, ContextCompat.getColor(context, R.color.ice_blue))
                    }

                    setTitle(this.logo, this.title, item.title)

                    val source = item.content.image?.sources?.find(Size.MEDIUM)
                    if (source != null) {
                        image.visibility = View.VISIBLE
                        Glide.with(image).load(source.url).into(image)
                    } else {
                        image.visibility = View.GONE
                    }

                    item.content.imageAlign?.let {
                        // TODO : XXX
                    }

                    item.content.header?.let {
                        setText(header, it, ContextCompat.getColor(context, R.color.battleship_grey))
                    }
                    item.content.body?.let {
                        setText(body, it, ContextCompat.getColor(context, R.color.battleship_grey))
                    }
                    item.content.footer?.let {
                        setText(footer, it, ContextCompat.getColor(context, R.color.battleship_grey))
                    }
                }
            }
        }

        private fun <T> fromJsonOrNull(json: String, classOfT: Class<T>): T? {
            return try {
                TemplateViews.gson.fromJson(json, classOfT)
            } catch (e: Throwable) {
                null
            }
        }
    }
}

enum class Size {
    @SerializedName("X_SMALL")
    X_SMALL,
    @SerializedName("SMALL")
    SMALL,
    @SerializedName("MEDIUM")
    MEDIUM,
    @SerializedName("LARGE")
    LARGE,
    @SerializedName("X_LARGE")
    X_LARGE,
}

class Title(
    @SerializedName("logo") val logo: Image,
    @SerializedName("text") val text: Text,
    @SerializedName("subtext") val subtext: Text?
)

class Text(
    @SerializedName("text") val text: String,
    @SerializedName("color") val color: String?
)

class Image(
    @SerializedName("contentDescription") val contentDescription: String?,
    @SerializedName("sources") val sources: List<Source>
)

class Source(
    @SerializedName("url") val url: String,
    @SerializedName("size") val size: Size?,
    @SerializedName("widthPixels") val widthPixels: Long?,
    @SerializedName("heightPixels") val heightPixels: Long?
)

class Background(
    @SerializedName("color") val color: String?,
    @SerializedName("image") val image: Image?
)

class AudioPlayer(
    @SerializedName("title") val title: AudioPlayerTitle,
    @SerializedName("content") val content: AudioPlayerContent
)

data class AudioPlayerTitle(
    @SerializedName("iconUrl") val iconUrl: String?,
    @SerializedName("text") val text: String?
)

data class AudioPlayerContent(
    @SerializedName("title") val title: String?,
    @SerializedName("subtitle1") val subtitle1: String?,
    @SerializedName("subtitle2") val subtitle2: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("durationSec") val durationSec: String?,
    @SerializedName("backgroundImageUrl") val backgroundImageUrl: String?,
    @SerializedName("backgroundColor") val backgroundColor: String?
)

class FullText1(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("content") val content: FullText1Content
)

class FullText1Content(
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text,
    @SerializedName("footer") val footer: Text?
)

class FullText2(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("content") val content: FullText2Content
)

class FullText2Content(
    @SerializedName("body") val body: Text,
    @SerializedName("footer") val footer: Text?
)

class ImageText2(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("content") val content: ImageText2Content
)

class ImageText2Content(
    @SerializedName("image") val image: Image?,
    @SerializedName("imageAlign") val imageAlign: String?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("footer") val footer: Text?
)

class TextList1(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<TextList1Item>
)

class TextList1Item(
    @SerializedName("token") val token: String,
    @SerializedName("header") val header: Text,
    @SerializedName("body") val body: Text,
    @SerializedName("footer") val footer: Text?
)

class TextList2(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<TextList2Item>
)

class TextList2Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("footer") val footer: Text?
)

private fun setBackground(view: View, background: Background, defaultBackgroundColor: Int = 0xffffffff.toInt()) {
    view.setBackgroundColor(parseColor(background.color, defaultBackgroundColor))

    background.image?.let { image ->
        val found = image.sources.find(Size.X_LARGE)
        if (found != null) {
            Glide.with(view)
                .load(found.url)
                .into(object : CustomViewTarget<View, Drawable>(view) {
                    override fun onLoadFailed(errorDrawable: Drawable?) {

                    }

                    override fun onResourceCleared(placeholder: Drawable?) {

                    }

                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        view.background = resource
                    }

                })
        }
    }
}

private fun setTitle(imageView: ImageView, textView: TextView, title: Title) {
    title.logo.sources.find(Size.MEDIUM)?.let { source ->
        Glide.with(imageView).load(source.url).into(imageView)
    }

    textView.text = title.text.text
}

private fun setText(textView: TextView, text: Text, defaultTextColor: Int = 0xff000000.toInt()) {
    textView.setTextColor(parseColor(text.color, defaultTextColor))
    textView.text = text.text
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
            return null
        }
    }
}

private fun parseColor(color: String?, default: Int): Int {
    if (color == null) {
        return default
    }

    return try {
        Color.parseColor(color)
    } catch (e: Throwable) {
        default
    }
}