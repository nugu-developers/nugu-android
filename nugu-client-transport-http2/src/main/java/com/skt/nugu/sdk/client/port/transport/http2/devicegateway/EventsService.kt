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

import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.APPLICATION_SPEEX
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.ATTACHMENT
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.EVENT
import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.MESSAGE_ID
import com.skt.nugu.sdk.client.port.transport.http2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.http2.Status
import com.skt.nugu.sdk.client.port.transport.http2.devicegateway.ResponseHandler.Companion.handleResponse
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartRequestBody
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartRequestBody.Companion.toMultipartRequestBody
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartStreamingCalls
import com.skt.nugu.sdk.client.port.transport.http2.utils.MessageRequestConverter.toFilename
import com.skt.nugu.sdk.client.port.transport.http2.utils.MessageRequestConverter.toJson
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

class EventsService(
    val policy: ServerPolicy,
    val client: OkHttpClient,
    private val observer: DeviceGatewayTransport
) {
    private val isShutdown = AtomicBoolean(false)
    private val streamingCall = MultipartStreamingCalls(object :
        MultipartStreamingCalls.PendingRequestListener<EventMessageRequest> {
        override fun execute(request: EventMessageRequest) {
            sendEventMessage(request)
        }
    })

    companion object {
        private const val TAG = "EventsService"
        private const val HTTPS_SCHEME = "https"

        fun create(
            policy: ServerPolicy,
            client: OkHttpClient,
            observer: DeviceGatewayTransport
        ): EventsService {
            return EventsService(
                policy,
                client,
                observer
            )
        }

        fun Call.closeQuietly() = this.getRequestBody().cancel()
        fun Call.getRequestBody() = this.request().body as MultipartRequestBody
    }

    private fun handleRequestStreamingCall(messageRequest: EventMessageRequest) : Boolean {
        val hasASR = messageRequest.namespace == "ASR"
        val hasRecognize = messageRequest.name == "Recognize"
        val isStart = hasASR && hasRecognize

        if(streamingCall.isExecuted()) {
            streamingCall.executePendingRequest(messageRequest)
            if(hasASR) streamingCall.stop()
            return false
        }
        if(isStart) {
            streamingCall.start()
        }
        return true
    }

    fun sendAttachmentMessage(messageRequest: AttachmentMessageRequest): Boolean {
        streamingCall.get()?.let { call ->
            val content = messageRequest.byteArray ?: ByteArray(0)
            call.getRequestBody().newBuilder().addFormDataPart(
                name = ATTACHMENT,
                filename = messageRequest.toFilename(),
                headers = Headers.Builder().add(MESSAGE_ID, messageRequest.messageId).build(),
                body = content.toRequestBody(APPLICATION_SPEEX.toMediaType())
            ).close(messageRequest.isEnd).build()
        } ?: return false

        if(messageRequest.isEnd) {
            streamingCall.stop()
        }
        return true
    }

    fun sendEventMessage(messageRequest: EventMessageRequest): Boolean {
        if(!handleRequestStreamingCall(messageRequest)) {
            return true
        }
        val message = messageRequest.toJson()
        val httpUrl = HttpUrl.Builder()
            .scheme(HTTPS_SCHEME)
            .port(policy.port)
            .host(policy.hostname)
            .addPathSegment("v2")
            .addPathSegment("events")
            .build()

        if(!streamingCall.isExecuted()) {
            client.newCall(Request.Builder().url(httpUrl)
                .post(message.toMultipartRequestBody(EVENT, true))
                .build()).enqueue(responseCallback)
        } else {
            val request = Request.Builder().url(httpUrl)
                .post(message.toMultipartRequestBody(EVENT, false))
                .tag(responseCallback)
                .build()
            streamingCall.set(client.newCall(request)).enqueue(responseCallback)
        }
        return true
    }

    private val responseCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if(e.cause !is  IllegalStateException) {
                notifyOnError(e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        response.handleResponse(observer)
                        response.close()
                    } catch (e: Throwable) {
                        Logger.d(TAG, "[responseCallback] : " + e.message.toString())
                    }
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> observer.onError(Status.INTERNAL)
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                HttpURLConnection.HTTP_BAD_GATEWAY,
                HttpURLConnection.HTTP_UNAVAILABLE,
                HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> observer.onError(Status.UNAVAILABLE)
                HttpURLConnection.HTTP_UNSUPPORTED_TYPE -> observer.onError(Status.UNIMPLEMENTED)
                else -> {
                    observer.onError(Status.UNKNOWN)
                }
            }
        }
    }

    private fun notifyOnError(throwable: Throwable?) {
        if (!isShutdown.get()) {
            val status = Status.fromThrowable(throwable)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $throwable")
            observer.onError(status)
        }
    }

    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            streamingCall.cancel()
        }
    }
}