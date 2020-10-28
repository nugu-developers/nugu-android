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
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import com.google.gson.Gson
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.template.view.*
import com.skt.nugu.sampleapp.template.view.viewholder.TemplateViewHolder
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.display.DisplayInterface
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.platform.android.ux.widget.setThrottledOnClickListener

class TemplateViews {
    companion object {
        private const val TAG = "TemplateViews"

        private const val AUDIO_PLAYER_TEMPLATE_1 = "AudioPlayer.Template1"
        private const val AUDIO_PLAYER_TEMPLATE_2 = "AudioPlayer.Template2"
        private const val DISPLAY_DEFAULT = "Display.Default"
        private const val DISPLAY_FULL_TEXT_1 = "Display.FullText1"
        private const val DISPLAY_FULL_TEXT_2 = "Display.FullText2"
        private const val DISPLAY_FULL_TEXT_3 = "Display.FullText3"
        private const val DISPLAY_IMAGE_TEXT_1 = "Display.ImageText1"
        private const val DISPLAY_IMAGE_TEXT_2 = "Display.ImageText2"
        private const val DISPLAY_IMAGE_TEXT_3 = "Display.ImageText3"
        private const val DISPLAY_IMAGE_TEXT_4 = "Display.ImageText4"
        private const val DISPLAY_TEXT_LIST_1 = "Display.TextList1"
        private const val DISPLAY_TEXT_LIST_2 = "Display.TextList2"
        private const val DISPLAY_TEXT_LIST_3 = "Display.TextList3"
        private const val DISPLAY_TEXT_LIST_4 = "Display.TextList4"
        private const val DISPLAY_IMAGE_LIST_1 = "Display.ImageList1"
        private const val DISPLAY_IMAGE_LIST_2 = "Display.ImageList2"
        private const val DISPLAY_IMAGE_LIST_3 = "Display.ImageList3"
        private const val DISPLAY_FULL_IMAGE = "Display.FullImage"
        private const val DISPLAY_WEATHER_1 = "Display.Weather1"
        private const val DISPLAY_WEATHER_2 = "Display.Weather2"
        private const val DISPLAY_WEATHER_3 = "Display.Weather3"
        private const val DISPLAY_WEATHER_4 = "Display.Weather4"
        private const val DISPLAY_WEATHER_5 = "Display.Weather5"

        private val gson = Gson()

        private fun <T> fromJsonOrNull(json: String, classOfT: Class<T>): T? {
            return try {
                gson.fromJson(json, classOfT)
            } catch (e: Throwable) {
                null
            }
        }

        fun createView(
            context: Context,
            name: String,
            displayId: String,
            template: String
        ): BaseView {
            Log.d(TAG, "[createView] name: $name, template: $template")

            return when (name) {
                AUDIO_PLAYER_TEMPLATE_1,
                AUDIO_PLAYER_TEMPLATE_2 -> DisplayAudioPlayer(context).apply {
                    when (name) {
                        AUDIO_PLAYER_TEMPLATE_1 -> header.enableMarquee()
                        AUDIO_PLAYER_TEMPLATE_2 -> header.maxLines = 4
                    }
                    this.applyDisplayAudioPlayer(template)
                }
                DISPLAY_DEFAULT,
                DISPLAY_FULL_IMAGE,
                DISPLAY_FULL_TEXT_1, DISPLAY_FULL_TEXT_2, DISPLAY_FULL_TEXT_3,
                DISPLAY_IMAGE_TEXT_1, DISPLAY_IMAGE_TEXT_2, DISPLAY_IMAGE_TEXT_3, DISPLAY_IMAGE_TEXT_4 ->
                    object : AbstractDisplayText(context) {
                        override val viewResId: Int
                            get() = when (name) {
                                DISPLAY_FULL_TEXT_1 -> R.layout.view_display_full_text_1
                                DISPLAY_FULL_TEXT_2 -> R.layout.view_display_full_text_2
                                DISPLAY_FULL_TEXT_3 -> R.layout.view_display_full_text_3
                                DISPLAY_IMAGE_TEXT_1 -> R.layout.view_display_image_text_1
                                DISPLAY_IMAGE_TEXT_2 -> R.layout.view_display_image_text_2
                                DISPLAY_IMAGE_TEXT_3 -> R.layout.view_display_image_text_3
                                DISPLAY_IMAGE_TEXT_4 -> R.layout.view_display_image_text_4
                                DISPLAY_FULL_IMAGE -> R.layout.view_display_full_image
                                else -> R.layout.view_display_full_text_2
                            }
                    }.apply {
                        if (name == DISPLAY_FULL_TEXT_1) {
                            (subLayout.layoutParams as FrameLayout.LayoutParams).gravity =
                                Gravity.LEFT
                        }
                        applyDisplayTextData(template, this)
                    }
                DISPLAY_TEXT_LIST_4,
                DISPLAY_TEXT_LIST_3 -> DisplayTextList4(context).apply {
                    fromJsonOrNull(template, TextList3::class.java)?.let { textList ->
                        val badgeNumber = textList.badgeNumber ?: false
                        textList.title.setTitle(this)

                        this.adapter =
                            object : RecyclerView.Adapter<TemplateViewHolder<ItemTextList4>>() {

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int
                                ): TemplateViewHolder<ItemTextList4> {
                                    return TemplateViewHolder(
                                        ItemTextList4(parent.context)
                                    )
                                }

                                override fun getItemCount() = textList.listItems.size
                                override fun onBindViewHolder(
                                    holder: TemplateViewHolder<ItemTextList4>,
                                    position: Int
                                ) {
                                    textList.listItems[position].let { item ->
                                        badgeNumber.setBadge(position, holder.view.badge)
                                        item.image?.setImage(Size.MEDIUM, holder.view.image)
                                        item.header?.setText(holder.view.header)
                                        item.body?.setText(holder.view.body)
                                        item.footer?.setText(holder.view.footer)
                                        item.button?.setButton(displayId, holder.view.button)

                                        holder.view.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                eventType = item.eventType,
                                                textInput = item.textInput,
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                DISPLAY_IMAGE_LIST_2 -> DisplayImageList2(context).apply {
                    fromJsonOrNull(template, ImageList2::class.java)?.let { imageList ->
                        imageList.title.setTitle(this)
                        val badgeNumber = imageList.badgeNumber ?: false

                        this.adapter =
                            object : RecyclerView.Adapter<TemplateViewHolder<ItemImageList2>>() {

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int
                                ): TemplateViewHolder<ItemImageList2> {
                                    return TemplateViewHolder(
                                        ItemImageList2(parent.context)
                                    )
                                }

                                override fun getItemCount(): Int {
                                    return imageList.listItems.size
                                }

                                override fun onBindViewHolder(
                                    holder: TemplateViewHolder<ItemImageList2>,
                                    position: Int
                                ) {

                                    imageList.listItems[position].let { item ->
                                        badgeNumber.setBadge(position, holder.view.badge)
                                        item.image?.setImage(Size.MEDIUM, holder.view.image)
                                        item.header?.setText(holder.view.header)
                                        item.footer?.setText(holder.view.footer)
                                        item.icon?.setImage(Size.MEDIUM, holder.view.icon)

                                        holder.view.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                eventType = item.eventType,
                                                textInput = item.textInput,
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                DISPLAY_IMAGE_LIST_3 -> DisplayImageList3(context).apply {
                    fromJsonOrNull(template, ImageList3::class.java)?.let { imageList ->
                        imageList.title.setTitle(this)

                        this.adapter =
                            object : RecyclerView.Adapter<TemplateViewHolder<ItemImageList3>>() {

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int
                                ): TemplateViewHolder<ItemImageList3> {
                                    return TemplateViewHolder(
                                        ItemImageList3(parent.context)
                                    )
                                }

                                override fun getItemCount(): Int {
                                    return imageList.listItems.size
                                }

                                override fun onBindViewHolder(
                                    holder: TemplateViewHolder<ItemImageList3>,
                                    position: Int
                                ) {
                                    imageList.listItems[position].let { item ->
                                        item.image?.setImage(Size.MEDIUM, holder.view.image)
                                        item.header?.setText(holder.view.header)
                                        item.icon?.setImage(Size.MEDIUM, holder.view.icon)

                                        holder.view.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                eventType = item.eventType,
                                                textInput = item.textInput,
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                DISPLAY_IMAGE_LIST_1 -> DisplayImageList1(context).apply {
                    fromJsonOrNull(template, ImageList1::class.java)?.let { imageList ->
                        imageList.title.setTitle(this)
                        val badgeNumber = imageList.badgeNumber ?: false
                        this.recyclerView.layoutManager = GridLayoutManager(context, 2)

                        this.adapter =
                            object : RecyclerView.Adapter<TemplateViewHolder<ItemImageList1>>() {

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int
                                ): TemplateViewHolder<ItemImageList1> {
                                    return TemplateViewHolder(ItemImageList1(parent.context))
                                }

                                override fun getItemCount(): Int {
                                    return imageList.listItems.size
                                }

                                override fun onBindViewHolder(
                                    holder: TemplateViewHolder<ItemImageList1>,
                                    position: Int
                                ) {
                                    imageList.listItems[position].let { item ->
                                        badgeNumber.setBadge(position, holder.view.badge)
                                        item.image?.setImage(Size.MEDIUM, holder.view.image)
                                        item.header?.setText(holder.view.header)
                                        item.footer?.setText(holder.view.footer)

                                        holder.view.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                eventType = item.eventType,
                                                textInput = item.textInput,
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                DISPLAY_TEXT_LIST_2,
                DISPLAY_TEXT_LIST_1 -> DisplayTextList2(context).apply {
                    fromJsonOrNull(template, TextList2::class.java)?.let { textList ->
                        textList.title.setTitle(this)
                        val badgeNumber = textList.badgeNumber ?: false

                        this.adapter =
                            object : RecyclerView.Adapter<TemplateViewHolder<ItemTextList2>>() {

                                override fun onCreateViewHolder(
                                    parent: ViewGroup,
                                    viewType: Int
                                ): TemplateViewHolder<ItemTextList2> {
                                    return TemplateViewHolder(
                                        ItemTextList2(parent.context)
                                    )
                                }

                                override fun getItemCount(): Int {
                                    return textList.listItems.size
                                }

                                override fun onBindViewHolder(
                                    holder: TemplateViewHolder<ItemTextList2>,
                                    position: Int
                                ) {
                                    textList.listItems[position].let { item ->
                                        badgeNumber.setBadge(position, holder.view.badge)
                                        item.image?.setImage(Size.MEDIUM, holder.view.image)
                                        item.header?.setText(holder.view.header)
                                        item.body?.setText(holder.view.body)
                                        item.footer?.setText(holder.view.footer)
                                        item.toggle?.setToggle(
                                            context,
                                            textList.toggleStyle,
                                            holder.view.button
                                        )
                                        holder.view.button?.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                        holder.view.setThrottledOnClickListener {
                                            handleOnClickEvent(
                                                eventType = item.eventType,
                                                textInput = item.textInput,
                                                templateId = displayId,
                                                token = item.token
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
                DISPLAY_WEATHER_1,
                DISPLAY_WEATHER_2 -> {
                    DisplayWeather1(
                        context = context, viewResId = when (name) {
                            DISPLAY_WEATHER_1 -> R.layout.view_display_weather_1
                            DISPLAY_WEATHER_2 -> R.layout.view_display_weather_2
                            else -> R.layout.view_display_weather_1
                        }
                    ).apply {
                        applyDisplayWeatherData(template, this)
                    }
                }
                DISPLAY_WEATHER_3 -> {
                    DisplayWeather3(
                        context = context,
                        viewResId = R.layout.view_display_weather_3
                    ).apply {
                        this.recyclerView.layoutManager = GridLayoutManager(context, 2)
                        this.recyclerView.addItemDecoration(
                            DividerItemDecoration(
                                context,
                                DividerItemDecoration.HORIZONTAL
                            )
                        )

                        fromJsonOrNull(template, Weather1::class.java)?.let { item ->
                            with(this) {
                                item.background?.setBackground(context, this)
                                item.title.setTitle(this)
                                item.content.setContent(this)

                                this.adapter = object :
                                    RecyclerView.Adapter<TemplateViewHolder<ItemWeather3>>() {
                                    override fun onCreateViewHolder(
                                        parent: ViewGroup,
                                        viewType: Int
                                    ): TemplateViewHolder<ItemWeather3> {
                                        return TemplateViewHolder(
                                            ItemWeather3(parent.context)
                                        )
                                    }

                                    override fun getItemCount(): Int {
                                        return item.content.listItems.size
                                    }

                                    override fun onBindViewHolder(
                                        holder: TemplateViewHolder<ItemWeather3>,
                                        position: Int
                                    ) {
                                        item.content.listItems[position].let { item ->
                                            item.image?.setImage(Size.MEDIUM, holder.view.image)
                                            item.header?.setText(holder.view.header)
                                            item.body?.setText(holder.view.body)
                                            item.footer?.setText(holder.view.footer)
                                            item.setTemperature(holder.view)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DISPLAY_WEATHER_4 -> {
                    DisplayWeather4(
                        context = context,
                        viewResId = R.layout.view_display_weather_3
                    ).apply {
                        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                            override fun getItemOffsets(
                                outRect: Rect,
                                itemPosition: Int,
                                parent: RecyclerView
                            ) {
                                outRect.bottom = TemplateUtils.dpToPixel(context, 4f).toInt()
                            }
                        })

                        fromJsonOrNull(template, Weather1::class.java)?.let { item ->
                            with(this) {
                                item.background?.setBackground(context, this)
                                item.title.setTitle(this)
                                item.content.image?.setImage(Size.MEDIUM, image)
                                item.content.header?.setText(header)
                                item.content.body?.setText(body)

                                this.adapter = object :
                                    RecyclerView.Adapter<TemplateViewHolder<ItemWeather4>>() {
                                    override fun onCreateViewHolder(
                                        parent: ViewGroup,
                                        viewType: Int
                                    ): TemplateViewHolder<ItemWeather4> {
                                        return TemplateViewHolder(
                                            ItemWeather4(parent.context)
                                        )
                                    }

                                    override fun getItemCount(): Int {
                                        return item.content.listItems.size
                                    }

                                    override fun onBindViewHolder(
                                        holder: TemplateViewHolder<ItemWeather4>,
                                        position: Int
                                    ) {
                                        item.content.listItems[position].let { item ->
                                            item.image?.setImage(Size.MEDIUM, holder.view.image)
                                            item.header?.setText(holder.view.header)
                                            item.body?.setText(holder.view.body)
                                            item.footer?.setText(holder.view.footer)
                                            item.temperature?.min?.setText(holder.view.min)
                                            item.temperature?.max?.setText(holder.view.max)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DISPLAY_WEATHER_5 -> {
                    DisplayWeather5(
                        context = context,
                        viewResId = R.layout.view_display_weather_5
                    ).apply {

                        fromJsonOrNull(template, Weather5::class.java)?.let { item ->
                            with(this) {
                                item.title.setTitle(this)
                                item.content.header?.setText(header)
                                item.content.footer?.setText(footer)
                                item.content.body?.setText(body)
                                item.content.image?.drawableRequest(
                                    context,
                                    Size.MEDIUM,
                                    object : RequestListener {
                                        override fun onResourceReady(resource: Drawable) {
                                            progress.setDrawable(resource)
                                        }
                                    })
                                item.content.min?.setText(min)
                                item.content.max?.setText(max)

                                item.content.max?.text?.toFloat()?.let {
                                    progress?.setMax(it)
                                }
                                item.content.progress?.let {
                                    progress?.setProgress(it)
                                }
                                item.content.progressColor?.let {
                                    progress?.setProgressColor(it)
                                }
                            }
                        }
                    }
                }
                else -> object : BaseView(context) {}
            }
        }

        private fun applyDisplayWeatherData(
            template: String,
            displayWeather: DisplayWeather1
        ) {
            fromJsonOrNull(template, Weather1::class.java)?.let { item ->
                with(displayWeather) {
                    item.background?.setBackground(context, this)
                    item.title.setTitle(this)
                    item.content.image?.setImage(Size.MEDIUM, image)
                    item.content.header?.setText(header)
                    item.content.body?.setText(body)
                    item.content.temperature?.current?.setText(current)
                    item.content.temperature?.min?.setText(min)
                    item.content.temperature?.max?.setText(max)

                    this.recyclerView.layoutManager =
                        GridLayoutManager(context, item.content.listItems.size)

                    this.adapter =
                        object : RecyclerView.Adapter<TemplateViewHolder<ItemWeather1>>() {
                            override fun onCreateViewHolder(
                                parent: ViewGroup,
                                viewType: Int
                            ): TemplateViewHolder<ItemWeather1> {
                                return TemplateViewHolder(
                                    ItemWeather1(parent.context)
                                )
                            }

                            override fun getItemCount(): Int {
                                return item.content.listItems.size
                            }

                            override fun onBindViewHolder(
                                holder: TemplateViewHolder<ItemWeather1>,
                                position: Int
                            ) {
                                item.content.listItems[position].let { item ->
                                    item.image?.setImage(Size.MEDIUM, holder.view.image)
                                    item.header?.setText(holder.view.header)
                                    item.body?.setText(holder.view.body)
                                    item.footer?.setText(holder.view.footer)
                                }
                            }
                        }
                }
            }
        }

        private fun DisplayAudioPlayer.applyDisplayAudioPlayer(template: String) {
            fromJsonOrNull(template, AudioPlayer::class.java)?.let { item ->
                item.title.setTitle(this)
                item.content.setContent(this)
                item.content.setBarContent(this)

                collapsed.visibility = View.VISIBLE

                prev.setThrottledOnClickListener {
                    ClientManager.getClient().getPlaybackRouter().buttonPressed(
                        PlaybackButton.PREVIOUS
                    )
                }
                bar_prev.setThrottledOnClickListener {
                    prev.callOnClick()
                }
                play.setThrottledOnClickListener {
                    if (ClientManager.playerActivity == AudioPlayerAgentInterface.State.PLAYING) {
                        ClientManager.getClient().getPlaybackRouter().buttonPressed(
                            PlaybackButton.PAUSE
                        )
                    } else {
                        ClientManager.getClient().getPlaybackRouter().buttonPressed(
                            PlaybackButton.PLAY
                        )
                    }
                }
                bar_play.setThrottledOnClickListener {
                    play.callOnClick()
                }

                next.setThrottledOnClickListener {
                    ClientManager.getClient().getPlaybackRouter().buttonPressed(
                        PlaybackButton.NEXT
                    )
                }
                bar_next.setThrottledOnClickListener {
                    next.callOnClick()
                }
                bar_close.setThrottledOnClickListener {
                    close.callOnClick()
                }
                item.content.settings?.let {
                    it.favorite?.let {
                        favorite.setImageResource(
                            if (it) R.drawable.btn_like_inactive_2
                            else R.drawable.btn_like_inactive
                        )
                        favorite.visibility = View.VISIBLE
                        favorite.setThrottledOnClickListener { _ ->
                            ClientManager.getClient().audioPlayerAgent?.requestFavoriteCommand(it)
                        }
                    }
                    it.repeat?.let {
                        when (it) {
                            Repeat.ALL -> repeat.setImageResource(R.drawable.btn_repeat)
                            Repeat.ONE -> repeat.setImageResource(R.drawable.btn_repeat_1)
                            Repeat.NONE -> repeat.setImageResource(R.drawable.btn_repeat_inactive)
                        }
                        repeat.visibility = View.VISIBLE
                        repeat.setThrottledOnClickListener { _ ->
                            ClientManager.getClient().audioPlayerAgent?.requestRepeatCommand(
                                when (it) {
                                    Repeat.ALL -> AudioPlayerAgentInterface.RepeatMode.ALL
                                    Repeat.ONE -> AudioPlayerAgentInterface.RepeatMode.ONE
                                    Repeat.NONE -> AudioPlayerAgentInterface.RepeatMode.NONE
                                }
                            )
                        }
                    }
                    it.shuffle?.let {
                        shuffle.setImageResource(
                            if (it) R.drawable.btn_random
                            else R.drawable.btn_random_inactive
                        )
                        shuffle.visibility = View.VISIBLE
                        shuffle.setThrottledOnClickListener { _ ->
                            ClientManager.getClient().audioPlayerAgent?.requestShuffleCommand(it)
                        }
                    }
                }
                progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            ClientManager.getClient().audioPlayerAgent?.seek(progress * 1000L)
                        }
                        bar_progress.progress = progress
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        // nothing to do
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        // nothing to do
                    }
                })
            }
        }

        private fun applyDisplayTextData(template: String, displayTextView: AbstractDisplayText) {
            fromJsonOrNull(template, ImageText2::class.java)?.let { item ->
                with(displayTextView) {
                    item.background?.setBackground(context, this)
                    item.title.setTitle(this)
                    item.content.image?.setImage(Size.MEDIUM, image)
                    item.content.imageAlign?.let {
                        val params = image.layoutParams as LinearLayout.LayoutParams
                        when (it) {
                            "LEFT" -> params.gravity = Gravity.LEFT
                            "RIGHT" -> params.gravity = Gravity.RIGHT
                        }
                    }
                    item.content.header?.setText(header)
                    item.content.body?.setText(body)
                    item.content.footer?.setText(footer)
                }
            }
        }

        fun handleOnClickEvent(
            eventType: EventType? = EventType.Display_ElementSelected,
            templateId: String,
            token: String?,
            postback: String? = null,
            textInput: String? = null
        ) {
            try {
                when (eventType) {
                    EventType.Text_TextInput -> {
                        textInput?.let {
                            ClientManager.getClient().requestTextInput(it)
                        } ?: throw IllegalStateException("[setElementSelected] textInput is empty")
                    }
                    else /* "Display.ElementSelected" */ -> {
                        if (token == null) {
                            throw IllegalStateException("[setElementSelected] token is empty")
                        }
                        ClientManager.getClient().getDisplay()
                            ?.setElementSelected(templateId, token, postback, object :
                                DisplayInterface.OnElementSelectedCallback {
                                override fun onSuccess(dialogRequestId: String) {
                                    Log.d(
                                        TAG,
                                        "[setElementSelected::onSuccess] dialogRequestId: $dialogRequestId"
                                    )
                                }

                                override fun onError(
                                    dialogRequestId: String,
                                    errorType: DisplayInterface.ErrorType
                                ) {
                                    Log.d(
                                        TAG,
                                        "[setElementSelected::onError] dialogRequestId: $dialogRequestId / errorType: $errorType"
                                    )
                                }
                            }) ?: throw IllegalStateException("[setElementSelected] not initialized")
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e(
                    TAG,
                    "[setElementSelected] $e"
                )
            }
        }
    }
}
