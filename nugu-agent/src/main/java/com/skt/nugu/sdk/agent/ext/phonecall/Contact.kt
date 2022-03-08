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

import com.google.gson.JsonObject
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName

data class Contact(
    @SerializedName("label")
    val label: Label?,
    @SerializedName("number")
    val number: String?,
    @SerializedName("isBlocked")
    val isBlocked: Boolean?
) {
    companion object {
        val gsonSerializer by lazy {
            JsonSerializer<Contact> { src, _, _ ->
                JsonObject().apply {
                    src?.let {
                        it.label?.let { label ->
                            addProperty("label", label.name)
                        }
                        it.number?.let { number ->
                            addProperty("number", number)
                        }
                        it.isBlocked?.let { isBlocked ->
                            addProperty("isBlocked", if (isBlocked) "TRUE" else "FALSE")
                        }
                    }
                }
            }
        }
    }

    enum class Label {
        MOBILE,
        COMPANY,
        HOME,
        USER_DEFINED
    }
}