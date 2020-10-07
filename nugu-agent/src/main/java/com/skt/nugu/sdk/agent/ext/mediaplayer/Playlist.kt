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

package com.skt.nugu.sdk.agent.ext.mediaplayer

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Playlist information.
 * If not supported, should be null.
 * When create event, exclude field not supported.
 */
data class Playlist(
    @SerializedName("type")
    val type : Type?,
    @SerializedName("name")
    val name: String,
    @SerializedName("number")
    val number: String,
    @SerializedName("length")
    val length: String,
    @SerializedName("currentSongOrder")
    val currentSongOrder: String
) {
    enum class Type {
        CHART,
        PLAYLIST,
        THEMELIST,
        DEFAULT
    }
    fun toJson(): JsonElement = Gson().toJsonTree(this)
}