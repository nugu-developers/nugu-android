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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class Person(
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: Type,
    @SerializedName("profileImgUrl")
    val profileImgUrl: String?,
    @SerializedName("category")
    val category: String?,
    @SerializedName("address")
    val address: Address?,
    @SerializedName("businessHours")
    val businessHours: BusinessHour?,
    @SerializedName("history")
    val history: History?,
    @SerializedName("numInCallHistory")
    val numInCallHistory: String,
    @SerializedName("token")
    val token: String?,
    @SerializedName("score")
    val score: String?,
    @SerializedName("contacts")
    val contacts: Array<Contact>?,
    @SerializedName("poiId")
    val poiId: String?
) {
    enum class Type {
        @SerializedName("CONTACT")
        CONTACT,
        @SerializedName("EXCHANGE")
        EXCHANGE,
        @SerializedName("T114")
        T114,
        @SerializedName("NONE")
        NONE
    }

    data class Address(
        @SerializedName("road")
        val road: String?,
        @SerializedName("jibun")
        val jibun: String?
    )

    data class BusinessHour(
        @SerializedName("open")
        val open: String?,
        @SerializedName("close")
        val close: String?,
        @SerializedName("info")
        val info: String?
    )

    data class History(
        @SerializedName("time")
        val time: String?,
        @SerializedName("type")
        val type: Type?,
        @SerializedName("callType")
        val callType: CallType?
    ) {
        enum class Type {
            @SerializedName("OUT")
            OUT,
            @SerializedName("OUT_CANCELED")
            OUT_CANCELED,
            @SerializedName("IN")
            IN,
            @SerializedName("REJECTED")
            REJECTED,
            @SerializedName("MISSED")
            MISSED,
            @SerializedName("BLOCKED")
            BLOCKED,
        }

        enum class CallType {
            @SerializedName("NORMAL")
            NORMAL,
            @SerializedName("VIDEO")
            VIDEO,
            @SerializedName("CALLAR")
            CALLAR,
            @SerializedName("GROUP")
            GROUP,
            @SerializedName("VOICE_MESSAGE")
            VOICE_MESSAGE,
        }
    }

    fun toJson(): JsonElement = Gson().toJsonTree(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Person

        if (name != other.name) return false
        if (type != other.type) return false
        if (profileImgUrl != other.profileImgUrl) return false
        if (category != other.category) return false
        if (address != other.address) return false
        if (businessHours != other.businessHours) return false
        if (history != other.history) return false
        if (numInCallHistory != other.numInCallHistory) return false
        if (token != other.token) return false
        if (score != other.score) return false
        if (contacts != null) {
            if (other.contacts == null) return false
            if (!contacts.contentEquals(other.contacts)) return false
        } else if (other.contacts != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (profileImgUrl?.hashCode() ?: 0)
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (businessHours?.hashCode() ?: 0)
        result = 31 * result + (history?.hashCode() ?: 0)
        result = 31 * result + numInCallHistory.hashCode()
        result = 31 * result + (token?.hashCode() ?: 0)
        result = 31 * result + (score?.hashCode() ?: 0)
        result = 31 * result + (contacts?.contentHashCode() ?: 0)
        return result
    }
}