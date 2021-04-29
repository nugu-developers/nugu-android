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

import com.google.gson.Gson
import com.google.gson.JsonParser
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
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartParser
import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.Response
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.charset.Charset
import com.skt.nugu.sdk.core.interfaces.message.Call as MessageCall


class ResponseHandler {
    data class ExtractFilename(val seq: Int, val isEnd: Boolean)

    companion object {
        private val gson = Gson()

        private const val TAG = "ResponseHandler"

        fun Response.handleResponse(call: MessageCall?, observer: DeviceGatewayTransport): Boolean {
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
                        call?.onStart()

                        when (headers[CONTENT_TYPE]) {
                            APPLICATION_OPUS -> {
                                val attachment =
                                    handleAttachment(
                                        headers,
                                        body
                                    )
                                if(call?.isCanceled() != true) {
                                    observer.onReceiveAttachment(attachment)
                                }
                            }
                            APPLICATION_JSON_UTF8,
                            APPLICATION_JSON -> {
                                val directives = handleDirectives(
                                    body
                                )
                                if(call?.isCanceled() != true && call?.isCompleted() != true  ) {
                                    observer.onReceiveDirectives(directives)
                                }
                            }
                            else -> {
                                throw Exception("unknown content type (${headers[CONTENT_TYPE]})")
                            }
                        }
                    }
                }).start()
        }

        private fun handleDirectives(body: Buffer) : List<DirectiveMessage> {
            val directives = ArrayList<DirectiveMessage>()
            val json = JsonParser.parseString(body.readString(Charset.defaultCharset())).asJsonObject
            json.getAsJsonArray("directives").forEach {
                val header = gson.fromJson(it.asJsonObject["header"], Header::class.java)
                directives.add(DirectiveMessage(header, it.asJsonObject["payload"].toString()))
            }
            return directives
        }

        private fun handleAttachment(headers: Map<String, String>, body: Buffer): AttachmentMessage {
            val splitFilename = extractFilenames(headers[FILENAME])
            val defaultValue = ""
            val contentLength = headers[CONTENT_LENGTH]?.toLong() ?: body.size

            val header = Header(
                dialogRequestId = headers.getOrDefault(DIALOG_REQUEST_ID,defaultValue),
                messageId = headers.getOrDefault(MESSAGE_ID, defaultValue),
                name = headers.getOrDefault(NAME,defaultValue),
                namespace = headers.getOrDefault(NAMESPACE,defaultValue),
                version = headers.getOrDefault(VERSION, defaultValue),
                referrerDialogRequestId = headers.getOrDefault(REFERRER_DIALOG_REQUEST_ID,defaultValue)
            )

            return AttachmentMessage(
                content = ByteBuffer.wrap(body.readByteArray(contentLength)),
                header = header,
                isEnd = splitFilename.isEnd,
                seq = splitFilename.seq,
                parentMessageId = headers.getOrDefault(PARENT_MESSAGE_ID, defaultValue),
                mediaType = headers.getOrDefault(CONTENT_TYPE, defaultValue))
        }

        private fun extractFilenames(name: String?): ExtractFilename {
            val filenames = name.toString().split(";".toRegex())
            if (filenames.size == 2) {
                val seq = filenames[0]
                val isEnd = (filenames[1] == "end")
                return ExtractFilename(seq.toInt(), isEnd)
            }
            return ExtractFilename(0, true)
        }
    }

}