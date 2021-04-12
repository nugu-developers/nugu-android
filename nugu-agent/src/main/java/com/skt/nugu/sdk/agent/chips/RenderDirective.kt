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

package com.skt.nugu.sdk.agent.chips

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.message.Header

data class RenderDirective(
    val header: Header,
    val payload: Payload
) {
    data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("target")
        val target: Target,
        @SerializedName("chips")
        val chips: Array<Chip>
    ) {
        enum class Target {
            DM, LISTEN, SPEAKING
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Payload

            if (playServiceId != other.playServiceId) return false
            if (target != other.target) return false
            if (!chips.contentEquals(other.chips)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = playServiceId.hashCode()
            result = 31 * result + target.hashCode()
            result = 31 * result + chips.contentHashCode()
            return result
        }
    }
}