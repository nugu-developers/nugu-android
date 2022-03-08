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

package com.skt.nugu.sdk.agent.ext.phonecall

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class Context(
    val state: State,
    val template: Template?,
    val recipient: Recipient?,
    val numberBlockable: Boolean?
) {
    enum class Intent {
        @SerializedName("CALL")
        CALL,
        @SerializedName("SEARCH")
        SEARCH,
        @SerializedName("HISTORY")
        HISTORY,
        @SerializedName("REDIAL")
        REDIAL,
        @SerializedName("MISSED")
        MISSED,
        @SerializedName("EXACT_ONE")
        EXACT_ONE,
        @SerializedName("SAVE_CONTACT")
        SAVE_CONTACT,
        @SerializedName("BLOCK")
        BLOCK,
        @SerializedName("NONE")
        NONE
    }

    data class Template(
        @SerializedName("intent")
        val intent: Intent?,
        @SerializedName("callType")
        val callType: CallType?,
        @SerializedName("recipientIntended")
        val recipientIntended: RecipientIntended?,
        @SerializedName("searchScene")
        val searchScene: String?,
        @SerializedName("candidates")
        val candidates: Array<Person>?
    ) {
        fun toJson(): JsonElement =
            GsonBuilder().registerTypeAdapter(Contact::class.java, Contact.gsonSerializer).create()
                .toJsonTree(this)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Template

            if (intent != other.intent) return false
            if (callType != other.callType) return false
            if (recipientIntended != other.recipientIntended) return false
            if (searchScene != other.searchScene) return false
            if (candidates != null) {
                if (other.candidates == null) return false
                if (!candidates.contentEquals(other.candidates)) return false
            } else if (other.candidates != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = intent?.hashCode() ?: 0
            result = 31 * result + (callType?.hashCode() ?: 0)
            result = 31 * result + (recipientIntended?.hashCode() ?: 0)
            result = 31 * result + (searchScene?.hashCode() ?: 0)
            result = 31 * result + (candidates?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Recipient(
        val name: String?,
        val token: String?,
        val isMobile: Boolean?,
        val isRecentMissed: Boolean?
    )
}