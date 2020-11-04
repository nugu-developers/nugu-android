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

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * toggle information.
 * If not supported, should be null.
 * When create event, exclude field not supported.
 */
data class Toggle(
    /**
     * repeat, null if not supported
     */
    @SerializedName("repeat")
    val repeat: Repeat?,
    /**
     * shuffle, null if not supported
     */
    @SerializedName("shuffle")
    val shuffle: Shuffle?
) {
    enum class Repeat {
        @SerializedName("ALL")
        ALL,
        @SerializedName("ONE")
        ONE,
        @SerializedName("NONE")
        NONE
    }
    enum class Shuffle {
        @SerializedName("ON")
        ON,
        @SerializedName("OFF")
        OFF
    }
    fun toJson(): JsonElement = JsonObject().apply {
        repeat?.let { repeat ->
            addProperty("repeat", repeat.name)
        }
        shuffle?.let { shuffle ->
            addProperty("shuffle", shuffle.name)
        }
    }
}