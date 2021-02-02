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
package com.skt.nugu.sdk.client.port.transport.http2.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

object MessageRequestConverter {
    fun EventMessageRequest.toJson(): String {
        return with(this) {
            JsonObject().apply {
                this.add("context", JsonParser.parseString(context).asJsonObject)
                this.add("event", JsonObject().apply {
                    this.add("header", JsonObject().apply {
                        this.addProperty("dialogRequestId", dialogRequestId)
                        this.addProperty("messageId", messageId)
                        this.addProperty("name", name)
                        this.addProperty("namespace", namespace)
                        this.addProperty("version", version)
                        this.addProperty("referrerDialogRequestId", referrerDialogRequestId)
                    })
                    this.add("payload", JsonParser.parseString(payload).asJsonObject)
                })
            }
        }.toString()
    }

    fun MessageRequest.toStringMessage() : String {
        return when (this) {
            is AttachmentMessageRequest -> {
                StringBuilder().apply {
                    append(this.javaClass.simpleName)
                    append("(")
                    append("messageId=${messageId}")
                    append(",dialogRequestId=${dialogRequestId}")
                    append(",context=${context}")
                    append(",namespace=${namespace}")
                    append(",name=${name}")
                    append(",version=${version}")
                    append(",referrerDialogRequestId=${referrerDialogRequestId}")
                    append(",seq=${seq}")
                    append(",isEnd=${isEnd}")
                    append(",parentMessageId=${parentMessageId}")
                    append(",mediaType=${mediaType}")
                    append(",content size=${byteArray?.size}")
                    append(")")
                }.toString()
            }
            else -> this.toString()
        }
    }

    fun AttachmentMessageRequest.toFilename() : String {
        return "${this.seq};${if (this.isEnd) "end" else "continued"}"
    }
}