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
package com.skt.nugu.sdk.agent.payload

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class PlayStackControl(
    @SerializedName("type")
    val type: Type?,
    @SerializedName("playServiceId")
    private val playServiceId: String?
) {
    enum class Type {
        NONE,
        PUSH
    }

    fun getPushPlayServiceId(): String? = playServiceId

    fun toJsonObject(): JsonObject = Gson().toJsonTree(this).asJsonObject
}