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

package com.skt.nugu.sdk.agent.ext.mediaplayer.payload

import com.google.gson.annotations.SerializedName

data class PlayPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    /**
     * the unique string to identify
     */
    @SerializedName("token")
    val token: String,
    @SerializedName("category")
    val category: Category,
    @SerializedName("theme")
    val theme: String?,
    @SerializedName("genre")
    val genre: String?,
    @SerializedName("artist")
    val artist: String?,
    @SerializedName("album")
    val album: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("etc")
    val etc: Array<String>?
) {
    enum class Category {
        NONE,
        RECOMMEND,
        POPULAR,
        NEW,
        CHART,
        RECENT_PLAYED,
        FAVORITE,
        LIKE,
        PLAYLIST
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayPayload

        if (playServiceId != other.playServiceId) return false
        if (token != other.token) return false
        if (category != other.category) return false
        if (theme != other.theme) return false
        if (genre != other.genre) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (title != other.title) return false
        if (etc != null) {
            if (other.etc == null) return false
            if (!etc.contentEquals(other.etc)) return false
        } else if (other.etc != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + (theme?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (etc?.contentHashCode() ?: 0)
        return result
    }
}