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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.ext.phonecall.Context
import com.skt.nugu.sdk.agent.ext.phonecall.Person

data class SendCandidatesPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    @SerializedName("intent")
    val intent: Context.Intent,
    @SerializedName("recipient")
    val recipient: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("label")
    val label: String?,
    @SerializedName("callType")
    val callType: Context.CallType?,
    @SerializedName("candidates")
    val candidates: Array<Person>?
) {
    fun toJson(): JsonElement = Gson().toJsonTree(this)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendCandidatesPayload

        if (playServiceId != other.playServiceId) return false
        if (intent != other.intent) return false
        if (recipient != other.recipient) return false
        if (name != other.name) return false
        if (label != other.label) return false
        if (callType != other.callType) return false
        if (candidates != null) {
            if (other.candidates == null) return false
            if (!candidates.contentEquals(other.candidates)) return false
        } else if (other.candidates != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + intent.hashCode()
        result = 31 * result + (recipient?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + (callType?.hashCode() ?: 0)
        result = 31 * result + (candidates?.contentHashCode() ?: 0)
        return result
    }
}