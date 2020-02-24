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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.h2

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.utils.MultipartParser
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.Logger
import com.squareup.okhttp.*
import io.grpc.Status
import okio.Buffer
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import okio.BufferedSource

class DownStreamService(
    val option: Options,
    val client: OkHttpClient,
    private val observer: Observer
) {
    interface Observer {
        fun onReceiveDirectives(json: String)
        fun onReceiveAttachment(json: String)
        fun onError(status: Status)
    }

    private val isShutdown = AtomicBoolean(false)
    data class ExtractFilename(val seq: String?, val isEnd: Boolean)

    companion object {
        val TAG = "DownStreamService"

        const val CONTENT_TYPE = "Content-Type"
        const val CONTENT_LENGTH = "Content-Length"
        const val DIALOG_REQUEST_ID = "Dialog-Request-Id"
        const val MESSAGE_ID = "Message-Id"
        const val PARENT_MESSAGE_ID = "Parent-Message-Id"
        const val REFERRER_DIALOG_REQUEST_ID = "Referrer-Dialog-Request-Id"
        const val NAMESPACE = "Namespace"
        const val VERSION = "Version"
        const val FILENAME = "Filename"
        const val NAME = "Name"

        const val APPLICATION_JSON = "application/json"
        const val APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8"
        const val APPLICATION_OPUS = "audio/opus"


        fun create(
            option: Options,
            client: OkHttpClient,
            observer: Observer
        ) = DownStreamService(option, client, observer).apply {
            startDownStream()
        }
    }

    private fun startDownStream() {
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(option.hostname)
            .addPathSegment("v1")
            .addPathSegment("directives")
            .build()

        val request = Request.Builder().url(httpUrl).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request?, e: IOException?) {
                notifyOnError(e)
            }

            override fun onResponse(response: Response?) {
                when (response?.code()) {
                    HttpURLConnection.HTTP_OK -> {
                        try {
                            if (!parseResponseBody(response.body()?.source())) {
                                Logger.e(TAG, "[onResponse] return false")
                                observer.onError(Status.UNKNOWN)
                            }
                        } catch (e: Throwable) {
                            notifyOnError(e)
                        }
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                    else -> {
                        observer.onError(Status.UNKNOWN)
                    }
                }
            }
        })
    }

    private fun notifyOnError(throwable : Throwable?) {
        if (!isShutdown.get()) {
            val status = Status.fromThrowable(throwable)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $throwable")
            observer.onError(status)
        }
    }

    private fun parseResponseBody(source : BufferedSource?) : Boolean {
        return MultipartParser(source)
            .parsePart(object : MultipartParser.Listener {
            override fun onResult(
                headers: Map<String, String>,
                body: Buffer
            ) {
                when (headers[CONTENT_TYPE]) {
                    APPLICATION_OPUS -> {
                        val attachment = parseAttachment(headers, body)
                        observer.onReceiveAttachment(attachment.toString())
                    }
                    APPLICATION_JSON_UTF8,
                    APPLICATION_JSON -> {
                        parseDirectives(headers, body) { directives ->
                            observer.onReceiveDirectives(directives.toString())
                        }
                    }
                    else -> {
                        throw Exception("unknown content type (${headers[CONTENT_TYPE]})")
                    }
                }
            }
        })
    }

    private fun parseDirectives(headers: Map<String, String>, body: Buffer, predicate: (JsonObject) -> Unit) {
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
            predicate(directives)
        }
    }

    private fun parseAttachment(headers: Map<String, String>, body: Buffer) : JsonObject {
        val splitFilename = extractfilenames(headers[FILENAME])

        val contentLength = headers[CONTENT_LENGTH]?.toLong() ?: body.size()
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
            this.addProperty("referrerDialogRequestId", headers[REFERRER_DIALOG_REQUEST_ID] ?: "")
            this.addProperty("version", headers[VERSION])
        }

        return JsonObject().apply {
            this.add("attachment", JsonObject().apply {
                this.add("header", header)
                this.add("content", content)
                this.addProperty("isEnd", splitFilename.isEnd)
                this.addProperty("seq", splitFilename.seq)
                this.addProperty("parentMessageId", headers[PARENT_MESSAGE_ID])
                this.addProperty("mediaType", headers[CONTENT_TYPE])
            })
        }
    }

    private fun extractfilenames(name : String?) : ExtractFilename{
        val filenames = name?.split(";".toRegex())
        val seq = filenames?.get(0)
        val isEnd = filenames?.get(1).equals("end") ?: true
        return ExtractFilename(seq, isEnd)
    }

    fun shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}