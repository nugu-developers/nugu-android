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
package com.skt.nugu.sdk.agent.asr

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class AsrRecognizeEventPayload(
    private val codec: String,
    private val sessionId: String? = null,
    private val playServiceId: String? = null,
    private val property: String?, // TODO: Client에서 관여할 property가 아닌 것 같음.
    private val domainTypes: Array<String>? = null,
    private val language: String? = null,
    private val endpointing: String,
    private val encoding: String? = null,
    private val wakeup: PayloadWakeup? = null
) {
    companion object {
        const val CODEC_SPEEX = "SPEEX"
        const val CODEC_PCM = "PCM"

        const val LANGUAGE_KOR = "KOR"
        const val LANGUAGE_ENG = "ENG"
        const val LANGUAGE_JPN = "JPN"
        const val LANGUAGE_CHN = "CHN"

        const val ENDPOINTING_CLIENT = "CLIENT"
        const val ENDPOINTING_SERVER = "SERVER"

        const val ENCODING_PARTIAL = "PARTIAL"
        const val ENCODING_COMPLETE = "COMPLETE"
    }

    fun toJsonString(): String = JsonObject().apply {
        addProperty("codec", codec)
        sessionId?.let {
            addProperty("sessionId", sessionId)
        }
        playServiceId?.let {
            addProperty("playServiceId", playServiceId)
        }
        property?.let {
            addProperty("property", property)
        }
        domainTypes?.let {
            add("domainTypes", JsonArray().apply {
                domainTypes.forEach {
                    add(it)
                }
            })
        }
        language?.let {
            addProperty("language", language)
        }
        addProperty("endpointing", endpointing)
        encoding?.let {
            addProperty("encoding", encoding)
        }

        wakeup?.let {
            add("wakeup", JsonObject().apply {
               addProperty("word", it.word)
                add("boundary", JsonObject().apply {
                    addProperty("start", it.boundary.startSamplePosition)
                    addProperty("end", it.boundary.endSamplePosition)
                    addProperty("detection", it.boundary.detectSamplePosition)
                })
            })
        }
    }.toString()
}