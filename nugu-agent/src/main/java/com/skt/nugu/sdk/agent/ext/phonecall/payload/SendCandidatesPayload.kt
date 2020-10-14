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

package com.skt.nugu.sdk.agent.ext.phonecall.payload

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.common.InteractionControl
import com.skt.nugu.sdk.agent.ext.phonecall.CallType
import com.skt.nugu.sdk.agent.ext.phonecall.Context
import com.skt.nugu.sdk.agent.ext.phonecall.Person
import com.skt.nugu.sdk.agent.ext.phonecall.RecipientIntended
import java.lang.reflect.Type

data class SendCandidatesPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    @SerializedName("intent")
    val intent: Context.Intent,
    @SerializedName("recipientIntended")
    val recipientIntended: RecipientIntended?,
    @SerializedName("callType")
    val callType: CallType?,
    @SerializedName("searchTargetList")
    val searchTargetList: Array<SearchTarget>?,
    @SerializedName("searchScene")
    val searchScene: String?,
    @SerializedName("candidates")
    val candidates: Array<Person>?,
    @SerializedName("interactionControl")
    val interactionControl: InteractionControl?
) {
    companion object {
        private val GSON: Gson = GsonBuilder().registerTypeAdapter(
            Array<SearchTarget>::class.java,
            Deserializer()
        ).create()

        fun fromJson(json: String): SendCandidatesPayload? = GSON.fromJson(json, SendCandidatesPayload::class.java)
    }

    @Deprecated("deprecated at v1.2")
    enum class SearchTarget{
        @SerializedName("CONTACT")
        CONTACT,
        @SerializedName("EXCHANGE")
        EXCHANGE,
        @SerializedName("T114")
        T114
    }

    class Deserializer: JsonDeserializer<Array<SearchTarget>> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Array<SearchTarget> {
            val list = ArrayList<SearchTarget>()

            val jsonArray = json?.asJsonArray ?: return list.toTypedArray()

            jsonArray.forEach {
                try {
                    list.add(SearchTarget.valueOf(it.asString))
                } catch (e: Exception) {
                    // ignore
                }
            }

            return list.toTypedArray()
        }
    }

    fun toJson(): JsonElement = GSON.toJsonTree(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendCandidatesPayload

        if (playServiceId != other.playServiceId) return false
        if (intent != other.intent) return false
        if (recipientIntended != other.recipientIntended) return false
        if (callType != other.callType) return false
        if (searchTargetList != null) {
            if (other.searchTargetList == null) return false
            if (!searchTargetList.contentEquals(other.searchTargetList)) return false
        } else if (other.searchTargetList != null) return false
        if (searchScene != other.searchScene) return false
        if (candidates != null) {
            if (other.candidates == null) return false
            if (!candidates.contentEquals(other.candidates)) return false
        } else if (other.candidates != null) return false
        if (interactionControl != other.interactionControl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + intent.hashCode()
        result = 31 * result + (recipientIntended?.hashCode() ?: 0)
        result = 31 * result + (callType?.hashCode() ?: 0)
        result = 31 * result + (searchTargetList?.contentHashCode() ?: 0)
        result = 31 * result + (searchScene?.hashCode() ?: 0)
        result = 31 * result + (candidates?.contentHashCode() ?: 0)
        result = 31 * result + (interactionControl?.hashCode() ?: 0)
        return result
    }
}