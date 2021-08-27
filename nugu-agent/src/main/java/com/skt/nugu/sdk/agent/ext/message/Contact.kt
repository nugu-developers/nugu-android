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

package com.skt.nugu.sdk.agent.ext.message

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class Contact(
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: Type,
    @SerializedName("number")
    val number: String?,
    @SerializedName("label")
    val label: Label?,
    @SerializedName("profileImgUrl")
    val profileImgUrl: String?,
    @SerializedName("message")
    val message: Message?,
    @SerializedName("time")
    val time: String?,
    @SerializedName("numInMessageHistory")
    val numInMessageHistory: String?,
    @SerializedName("token")
    val token: String?,
    @SerializedName("score")
    val score: String?
) {
    data class Message(
        @SerializedName("text")
        val text: String,
        @SerializedName("type")
        val type: Type?
    ) {
        enum class Type {
            @SerializedName("SMS")
            SMS,
            @SerializedName("MMS")
            MMS
        }
    }

    enum class Label {
        @SerializedName("MOBILE")
        MOBILE,
        @SerializedName("COMPANY")
        COMPANY,
        @SerializedName("HOME")
        HOME,
        @SerializedName("USER_DEFINED")
        USER_DEFINED
    }

    enum class Type {
        @SerializedName("CONTACT")
        CONTACT,
        @SerializedName("EXCHANGE")
        EXCHANGE,
        @SerializedName("T114")
        T114,
        @SerializedName("NONE")
        NONE,
        @SerializedName("EMERGENCY")
        EMERGENCY
    }

    fun toJson(): JsonElement = Gson().toJsonTree(this)
}