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

package com.skt.nugu.sdk.agent.routine

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class Action(
    @SerializedName("type")
    val type: Type,
    @SerializedName("text")
    val text: String?,
    @SerializedName("data")
    val data: JsonObject?,
    @SerializedName("playServiceId")
    val playServiceId: String?,
    @SerializedName("token")
    val token: String?,
    @SerializedName("postDelayInMilliseconds")
    val postDelayInMilliseconds: Long?
) {
    enum class Type {
        @SerializedName("TEXT")
        TEXT,
        @SerializedName("DATA")
        DATA
    }

    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("type", type.name)
        text?.let {
            addProperty("text", it)
        }
        data?.let {
            add("data", it)
        }
        playServiceId?.let {
            addProperty("playServiceId", it)
        }
        token?.let {
            addProperty("token", it)
        }
        postDelayInMilliseconds?.let {
            addProperty("postDelayInMilliseconds", it)
        }
    }
}