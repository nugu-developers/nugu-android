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
package com.skt.nugu.sdk.client.port.transport.http2.devicegateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartParser
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.APPLICATION_JSON
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.APPLICATION_JSON_UTF8
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.APPLICATION_OPUS
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.CONTENT_LENGTH
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.CONTENT_TYPE
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.DIALOG_REQUEST_ID
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.FILENAME
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.MESSAGE_ID
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.MULTIPART_RELATED
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.NAME
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.NAMESPACE
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.PARENT_MESSAGE_ID
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.REFERRER_DIALOG_REQUEST_ID
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.VERSION

class ResponseHandler {
    data class ExtractFilename(val seq: String, val isEnd: Boolean)

    companion object {
        private const val TAG = "ResponseHandler"

        fun Response.handleResponse(observer: DeviceGatewayTransport): Boolean {
            val headers = this.headers
            val responseBody = this.body!!

            val contentType = headers[CONTENT_TYPE]
            if (contentType?.contains(MULTIPART_RELATED, true) == false) {
                Logger.d(TAG, "Unexpected header: Content-Type, ($contentType)")
                return false
            }
            if (responseBody.source().exhausted()) {
                return false
            }
            return MultipartParser(
                responseBody.source(),
                object :
                    MultipartParser.Listener {
                    override fun onResult(
                        headers: Map<String, String>,
                        body: Buffer
                    ) {
                        when (headers[CONTENT_TYPE]) {
                            APPLICATION_OPUS -> {
                                val attachment =
                                    handleAttachment(
                                        headers,
                                        body
                                    )
                                observer.onReceiveAttachment(attachment.toString())
                            }
                            APPLICATION_JSON_UTF8,
                            APPLICATION_JSON -> {
                                handleDirectives(
                                    body
                                ) { directives ->
                                    observer.onReceiveDirectives(directives.toString())
                                }
                            }
                            else -> {
                                throw Exception("unknown content type (${headers[CONTENT_TYPE]})")
                            }
                        }
                    }
                }).start()
        }

        private fun handleDirectives(body: Buffer, block: (JsonObject) -> Unit) {
            val json = JsonParser().parse(body.readString(Charset.defaultCharset())).asJsonObject
            json.getAsJsonArray("directives").forEach {
                val header = it.asJsonObject["header"]
                val directives = JsonObject().apply {
                    this.add("directives", JsonArray().apply {
                        add(JsonObject().apply {
                            this.add("header", header)
                            this.addProperty("payload", it.asJsonObject["payload"].toString())
                        })
                    })
                }
                block(directives)
            }
        }

        private fun handleAttachment(headers: Map<String, String>, body: Buffer): JsonObject {
            val splitFilename = extractFilenames(headers[FILENAME])
            val contentLength = headers[CONTENT_LENGTH]?.toLong() ?: body.size
            val content = JsonObject().apply {
                this.add("bytes", JsonArray().apply {
                    body.readByteArray(contentLength).forEach {
                        add(it)
                    }
                })
            }

            val header = JsonObject().apply {
                this.addProperty("dialogRequestId", headers[DIALOG_REQUEST_ID])
                this.addProperty("messageId", headers[MESSAGE_ID])
                this.addProperty("name", headers[NAME])
                this.addProperty("namespace", headers[NAMESPACE])
                this.addProperty(
                    "referrerDialogRequestId",
                    headers[REFERRER_DIALOG_REQUEST_ID] ?: ""
                )
                this.addProperty("version", headers[VERSION])
            }

            return JsonObject().apply {
                this.add("attachment", JsonObject().apply {
                    this.add("header", header)
                    this.add("content", content)
                    splitFilename?.let {
                        this.addProperty("isEnd", splitFilename.isEnd)
                        this.addProperty("seq", splitFilename.seq)
                    }
                    this.addProperty("parentMessageId", headers[PARENT_MESSAGE_ID])
                    this.addProperty("mediaType", headers[CONTENT_TYPE])
                })
            }
        }

        private fun extractFilenames(name: String?): ExtractFilename? {
            val filenames = name.toString().split(";".toRegex())
            if (filenames.size == 2) {
                val seq = filenames[0]
                val isEnd = (filenames[1] == "end")
                return ExtractFilename(seq, isEnd)
            }
            return null
        }
    }

}