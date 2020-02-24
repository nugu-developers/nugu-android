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
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.squareup.okhttp.*
import io.grpc.Status
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UpStreamService(
    val option: Options,
    val client: OkHttpClient,
    private val observer: Observer
) {
    interface Observer {
        fun onError(status: Status)
    }

    private val isShutdown = AtomicBoolean(false)

    companion object {
        val TAG = "UpStreamService"

        fun create(
            option: Options,
            client: OkHttpClient,
            observer: Observer
        ): UpStreamService {
            return UpStreamService(option, client, observer)
        }
    }

    fun sendAttachmentMessage(messageRequest: AttachmentMessageRequest): Boolean {
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(option.hostname)
            .addPathSegment("v1")
            .addPathSegment("event-attachment")
            .addQueryParameter("dialogRequestId", messageRequest.dialogRequestId)
            .addQueryParameter("messageId", messageRequest.messageId)
            .addQueryParameter("name", messageRequest.name)
            .addQueryParameter("namespace", messageRequest.namespace)
            .addQueryParameter("version", messageRequest.version)
            .addQueryParameter("referrerDialogRequestId", messageRequest.referrerDialogRequestId)
            .addQueryParameter("seq", messageRequest.seq.toString())
            .addQueryParameter("isEnd", messageRequest.isEnd.toString())
            .build()

        val body = RequestBody.create(
            MediaType.parse("application/octet-stream"),
            if (messageRequest.byteArray != null) messageRequest.byteArray
            else ByteArray(0)
        )

        val request = Request.Builder().url(httpUrl)
            .post(body)
            .tag(System.nanoTime())
            .build()

        try {
            val response = client.newCall(request).enqueue(object : Callback {
                override fun onFailure(request: Request?, e: IOException?) {
                    notifyOnError(e)
                }

                override fun onResponse(response: Response?) {
                    val duration =
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - request.tag() as Long)
                    val code = response?.code()
                    when (code) {
                        HttpURLConnection.HTTP_OK -> {
                            response.body()?.let {
                                val result = it.string()
                                Logger.d(DownStreamService.TAG, result)
                            }
                        }
                        HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                        else -> {
                            observer.onError(Status.UNKNOWN)
                        }
                    }
                }
            })
        } catch (ex: IOException) {
            Logger.d(DownStreamService.TAG, ex.toString())
        }
        return true
    }

    fun sendEventMessage(messageRequest: EventMessageRequest): Boolean {
        val value = with(messageRequest) {
            JsonObject().apply {
                this.add("context", JsonParser().parse(context).asJsonObject)
                this.add("event", JsonObject().apply {
                    this.add("header", JsonObject().apply {
                        this.addProperty("dialogRequestId", dialogRequestId)
                        this.addProperty("messageId", messageId)
                        this.addProperty("name", name)
                        this.addProperty("namespace", namespace)
                        this.addProperty("version", version)
                        this.addProperty("referrerDialogRequestId", referrerDialogRequestId)
                    })
                    this.add("payload", JsonParser().parse(payload).asJsonObject)
                })
            }.toString()
        }

        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(option.hostname)
            .addPathSegment("v1")
            .addPathSegment("event")
            .build()
        val body = RequestBody.create(MediaType.parse(DownStreamService.APPLICATION_JSON), value)
        val request = Request.Builder().url(httpUrl)
            .post(body)
            .tag(System.nanoTime())
            .build()

        try {
            val response = client.newCall(request).enqueue(object : Callback {
                override fun onFailure(request: Request?, e: IOException?) {
                    notifyOnError(e)
                }

                override fun onResponse(response: Response?) {
                    val duration =
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - request.tag() as Long)
                    val code = response?.code()
                    when (code) {
                        HttpURLConnection.HTTP_OK -> {
                        }
                        HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                        else -> {
                            observer.onError(Status.UNKNOWN)
                        }
                    }
                }
            })
        } catch (ex: IOException) {
            Logger.d(DownStreamService.TAG, ex.toString())
            return false
        }
        return true
    }

    fun sendCrashReport(messageRequest: CrashReportMessageRequest): Boolean {
        val value = with(messageRequest) {
            JsonObject().apply {
                this.add("crashs", JsonArray().apply {
                    add(JsonObject().apply {
                        this.addProperty("level", messageRequest.level.name)
                        this.addProperty("message", messageRequest.message)
                    })
                })
            }.toString()
        }

        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(option.hostname)
            .addPathSegment("v1")
            .addPathSegment("crash")
            .build()
        val body = RequestBody.create(MediaType.parse(DownStreamService.APPLICATION_JSON), value)
        val request = Request.Builder().url(httpUrl)
            .post(body)
            .tag(System.nanoTime())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request?, e: IOException?) {
                notifyOnError(e)
            }

            override fun onResponse(response: Response?) {
                val duration =
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - request.tag() as Long)
                val code = response?.code()
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                    else -> {
                        observer.onError(Status.UNKNOWN)
                    }
                }
            }
        })

        return true
    }

    private fun notifyOnError(throwable : Throwable?) {
        if (!isShutdown.get()) {
            val status = Status.fromThrowable(throwable)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $throwable")
            observer.onError(status)
        }
    }

    fun shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}