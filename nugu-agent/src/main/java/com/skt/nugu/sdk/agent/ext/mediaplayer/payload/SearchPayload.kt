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
import com.skt.nugu.sdk.agent.ext.mediaplayer.Category
import com.skt.nugu.sdk.agent.ext.mediaplayer.Song

data class SearchPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    /**
     * the unique string to identify
     */
    @SerializedName("token")
    val token: String,
    @SerializedName("asrText")
    val asrText: String?,
    @SerializedName("song")
    val song: Song?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayPayload

        if (playServiceId != other.playServiceId) return false
        if (token != other.token) return false
        if (asrText != other.asrText) return false
        if (song != other.song) return false
        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + (asrText?.hashCode() ?: 0)
        result = 31 * result + (song?.hashCode() ?: 0)
        return result
    }
}