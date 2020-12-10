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
package com.skt.nugu.sdk.platform.android.ux.template.model

import com.google.gson.annotations.SerializedName


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
    X_LARGE
}

enum class TextAlign {
    @SerializedName("left")
    LEFT,

    @SerializedName("center")
    CENTER,

    @SerializedName("right")
    RIGHT
}

enum class Display {
    @SerializedName("block")
    BLOCK,

    @SerializedName("inline")
    INLINE,

    @SerializedName("none")
    NONE
}

enum class LyricsType {
    @SerializedName("NONE")
    NONE,

    @SerializedName("SYNC")
    SYNC,

    @SerializedName("NON_SYNC")
    NON_SYNC
}

enum class ToggleStatus {
    @SerializedName(value="on", alternate=["on", "ON"])
    ON,
    @SerializedName(value="off", alternate=["off", "OFF"])
    OFF
}

enum class Repeat {
    @SerializedName("ALL")
    ALL,
    @SerializedName("ONE")
    ONE,
    @SerializedName("NONE")
    NONE
}

enum class EventType {
    @SerializedName("Display.ElementSelected")
    Display_ElementSelected,
    @SerializedName("Text.TextInput")
    Text_TextInput
}

class Title(
    @SerializedName("logo") val logo: Image,
    @SerializedName("iconUrl") val iconUrl: String?,
    @SerializedName("text") val text: Text,
    @SerializedName("subtext") val subtext: Text?,
    @SerializedName("subicon") val subicon: Image?
)

class Text(
    @SerializedName("text") val text: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("style") val style: Style?
)

class Style(
    @SerializedName("text-align") val align: TextAlign?,
    @SerializedName("opacity") val opacity: Float?,
    @SerializedName("display") val display: Display?,
    @SerializedName("margin") val margin: Long?
)

class Image(
    @SerializedName("contentDescription") val contentDescription: String?,
    @SerializedName("sources") val sources: List<Source>
)

class Button(
    @SerializedName("style") val style: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("token") val token: String,
    @SerializedName("postback") val postback: String?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class ToggleButton(
    @SerializedName("style") val style: String?,
    @SerializedName("status") val status: ToggleStatus?,
    @SerializedName("token") val token: String
)

class ToggleStyle(
    @SerializedName("text") val text: ToggleStyleText?,
    @SerializedName("image") val image: ToggleStyleImage?
)

class ToggleStyleText(
    @SerializedName("on") val on: Text?,
    @SerializedName("off") val off: Text?
)

class ToggleStyleImage(
    @SerializedName("on") val on: Image?,
    @SerializedName("off") val off: Image?
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

class AudioPlayerUpdate(
    @SerializedName("template") val player: AudioPlayer
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
    @SerializedName("backgroundColor") val backgroundColor: String?,
    @SerializedName("badgeMessage") val badgeMessage: String?,
    @SerializedName("badgeImageUrl") val badgeImageUrl: String?,
    @SerializedName("lyrics") val lyrics: Lyrics?,
    @SerializedName("settings") val settings: Settings?
)

data class Settings(
    @SerializedName("favorite") val favorite: Boolean?,
    @SerializedName("repeat") val repeat: Repeat?,
    @SerializedName("shuffle") val shuffle: Boolean?
)

data class Lyrics(
    @SerializedName("title") val title: String?,
    @SerializedName("lyricsType") val lyricsType: LyricsType?,
    @SerializedName("lyricsInfoList") val lyricsInfoList: List<LyricsInfo>?

)
data class LyricsInfo(
    @SerializedName("time") val time: Int?,
    @SerializedName("text") val text: String?
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
    @SerializedName("footer") val footer: Text?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class TextList2(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("toggleStyle") val toggleStyle: ToggleStyle?,
    @SerializedName("listItems") val listItems: List<TextList2Item>
)

class TextList3(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<TextList3Item>
)

class TextList2Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("toggle") val toggle: ToggleButton?,
    @SerializedName("footer") val footer: Text?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class TextList3Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: List<Text>?,
    @SerializedName("button") val button: Button?,
    @SerializedName("footer") val footer: Text?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class ImageList3(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<ImageList3Item>
)

class ImageList3Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("icon") val icon: Image?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class ImageList2(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<ImageList2Item>
)

class ImageList2Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("footer") val footer: Text?,
    @SerializedName("icon") val icon: Image?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class ImageList1(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("badgeNumber") val badgeNumber: Boolean?,
    @SerializedName("listItems") val listItems: List<ImageList1Item>
)

class ImageList1Item(
    @SerializedName("token") val token: String,
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("footer") val footer: Text?,
    @SerializedName("eventType") val eventType: EventType?,
    @SerializedName("textInput") val textInput: String?
)

class Weather1(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("content") val content: Weather1Content
)

class Weather1Item(
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("temperature") val temperature: Temperature?,
    @SerializedName("footer") val footer: Text?
)

class Temperature(
    @SerializedName("current") val current: Text?,
    @SerializedName("max") val max: Text?,
    @SerializedName("min") val min: Text?
)

class Weather1Content(
    @SerializedName("image") val image: Image?,
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("temperature") val temperature: Temperature?,
    @SerializedName("listItems") val listItems: List<Weather1Item>
)

class Weather5(
    @SerializedName("title") val title: Title,
    @SerializedName("background") val background: Background?,
    @SerializedName("content") val content: Weather5Content
)

class Weather5Content(
    @SerializedName("header") val header: Text?,
    @SerializedName("body") val body: Text?,
    @SerializedName("footer") val footer: Text?,
    @SerializedName("progress") val progress: Float?,
    @SerializedName("progressColor") val progressColor: String?,
    @SerializedName("max") val max: Text?,
    @SerializedName("min") val min: Text?,
    @SerializedName("icon") val image: Image?
)