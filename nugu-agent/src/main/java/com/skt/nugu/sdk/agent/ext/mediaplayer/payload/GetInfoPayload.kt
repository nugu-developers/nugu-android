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

data class GetInfoPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    /**
     * the unique string to identify
     */
    @SerializedName("token")
    val token: String,
    @SerializedName("infos")
    val infos: Array<InfoItem>
) {
    enum class InfoItem(val value: String) {
        TITLE("title"),
        ARTIST("artist"),
        ALBUM("album"),
        ISSUE_DATE("issueDate"),
        PLAY_TIME("playTime"),
        PLAYLIST_NAME("playlistName")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetInfoPayload

        if (playServiceId != other.playServiceId) return false
        if (token != other.token) return false
        if (!infos.contentEquals(other.infos)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + infos.contentHashCode()
        return result
    }
}