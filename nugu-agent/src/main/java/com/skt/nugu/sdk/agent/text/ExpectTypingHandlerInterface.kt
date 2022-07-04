/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.text

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.message.Header

interface ExpectTypingHandlerInterface {
    interface Controller {
        fun expectTyping(directive: Directive)
    }

    data class Directive(
        val header: Header,
        val payload: Payload
    )

    data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("domainTypes")
        val domainTypes: Array<String>?,
        @SerializedName("asrContext")
        val asrContext: JsonObject?,
    ) {
        companion object {
            fun getDialogAttribute(payload: Payload) = with(payload) {
                DialogAttribute(
                    this.playServiceId,
                    this.domainTypes,
                    this.asrContext?.toString()
                )
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Payload

            if (playServiceId != other.playServiceId) return false
            if (domainTypes != null) {
                if (other.domainTypes == null) return false
                if (!domainTypes.contentEquals(other.domainTypes)) return false
            } else if (other.domainTypes != null) return false
            if (asrContext != other.asrContext) return false

            return true
        }

        override fun hashCode(): Int {
            var result = playServiceId.hashCode()
            result = 31 * result + (domainTypes?.contentHashCode() ?: 0)
            result = 31 * result + (asrContext?.hashCode() ?: 0)
            return result
        }
    }
}