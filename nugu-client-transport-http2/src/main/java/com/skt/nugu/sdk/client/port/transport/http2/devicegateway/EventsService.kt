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
import com.skt.nugu.sdk.client.port.transport.http2.CanceledCall
import com.skt.nugu.sdk.client.port.transport.http2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.http2.devicegateway.ResponseHandler.Companion.handleResponse
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartRequestBody
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartRequestBody.Companion.toMultipartRequestBody
import com.skt.nugu.sdk.client.port.transport.http2.multipart.MultipartStreamingCalls
import com.skt.nugu.sdk.client.port.transport.http2.utils.MessageRequestConverter.toFilename
import com.skt.nugu.sdk.client.port.transport.http2.utils.MessageRequestConverter.toJson
import com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
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
        MultipartStreamingCalls.PendingRequestListener<ClientCall> {
        override fun execute(call: ClientCall) {
            sendEventMessage(call.messageRequest, call.callback)
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

    data class ClientCall(
        val messageRequest: EventMessageRequest,
        var callback: MessageSender.OnRequestCallback?
    )
    private fun handleRequestStreamingCall(call: ClientCall) : Boolean {
        val hasASR = call.messageRequest.namespace == "ASR"
        val hasRecognize = call.messageRequest.name == "Recognize"
        val isStart = hasASR && hasRecognize

        if(streamingCall.isExecuted()) {
            streamingCall.executePendingRequest(call)
            if(hasASR) streamingCall.stop()
            return false
        }
        if(isStart) {
            streamingCall.start()
        }
        return true
    }

    fun sendAttachmentMessage(messageRequest: AttachmentMessageRequest): MessageSender.Call {
        streamingCall.get()?.let { call ->
            val content = messageRequest.byteArray ?: ByteArray(0)
            call.getRequestBody().newBuilder().addFormDataPart(
                name = ATTACHMENT,
                filename = messageRequest.toFilename(),
                headers = Headers.Builder().add(MESSAGE_ID, messageRequest.messageId).build(),
                body = content.toRequestBody(APPLICATION_SPEEX.toMediaType())
            ).close(messageRequest.isEnd).build()
        } ?: return CanceledCall(messageRequest)

        if(messageRequest.isEnd) {
            streamingCall.stop()
        }
        return object : MessageSender.Call {
            override fun request(): MessageRequest {
                return messageRequest
            }

            override fun cancel() {
                streamingCall.cancel()
            }

            override fun isCanceled(): Boolean {
                return streamingCall.isCanceled()
            }
        }
    }

    fun sendEventMessage(request: EventMessageRequest, callback: MessageSender.OnRequestCallback?): MessageSender.Call {
        val returnCall = object : MessageSender.Call {
            var call : Call? = null

            override fun request(): MessageRequest {
                return request
            }

            override fun cancel() {
                if(streamingCall.isExecuted()) {
                    streamingCall.cancel()
                }
                call?.cancel()
            }

            override fun isCanceled(): Boolean {
                if(streamingCall.isExecuted()) {
                    return streamingCall.isCanceled()
                }
                return call?.isCanceled() ?: false
            }
        }

        if (handleRequestStreamingCall(ClientCall(request, callback))) {
            val message = request.toJson()
            val httpUrl = HttpUrl.Builder()
                .scheme(HTTPS_SCHEME)
                .port(policy.port)
                .host(policy.hostname)
                .addPathSegment("v2")
                .addPathSegment("events")
                .build()

            if(!streamingCall.isExecuted()) {
                returnCall.call = client.newCall(Request.Builder().url(httpUrl)
                    .post(message.toMultipartRequestBody(EVENT, true, ClientCall(request, callback)))
                    .build())
                returnCall.call?.enqueue(responseCallback)
            } else {
                val requestBuilder = Request.Builder().url(httpUrl)
                    .post(message.toMultipartRequestBody(EVENT, false, ClientCall(request, callback)))
                    .tag(responseCallback)
                    .build()
                streamingCall.set(client.newCall(requestBuilder)).enqueue(responseCallback)
            }
        }
        return returnCall
    }

    private val responseCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if(e.cause !is  IllegalStateException) {
                notifyOnError(call, e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        response.handleResponse(observer, object :ResponseHandler.OnDirectiveMessage{
                            override fun onResult(directives: List<DirectiveMessage>) {
                                call.request().body?.let { requestBody ->
                                    requestBody.dispatchCallback(directives)
                                }
                            }
                        })
                        response.close()
                    } catch (e: Throwable) {
                        Logger.d(TAG, "[responseCallback] : " + e.message.toString())
                    }
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> notifyOnError(call, Status.INTERNAL)
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> notifyOnError(call, Status.UNAUTHENTICATED)
                HttpURLConnection.HTTP_BAD_GATEWAY,
                HttpURLConnection.HTTP_UNAVAILABLE,
                HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> notifyOnError(call, Status.UNAVAILABLE)
                HttpURLConnection.HTTP_UNSUPPORTED_TYPE -> notifyOnError(call, Status.UNIMPLEMENTED)
                else -> {
                    notifyOnError(call, Status.UNKNOWN)
                }
            }
        }
    }

    private fun notifyOnError(call: Call, status: Status) {
        observer.onError(status)
        call.getRequestBody().dispatchCallback()?.onFailure(status)
    }
    private fun notifyOnError(call: Call, throwable: Throwable?) {
        if (!isShutdown.get()) {
            val status = Status.fromThrowable(throwable)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $throwable")
            observer.onError(status)

            call.getRequestBody().dispatchCallback()?.onFailure(status)
        }
    }

    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            streamingCall.cancel()
        }
    }
}

private fun RequestBody.dispatchCallback(directives: List<DirectiveMessage>) {
    val request = this as MultipartRequestBody
    directives.forEach {
        request.getClientCall()?.let { clientCall ->
            if (it.header.dialogRequestId == clientCall.messageRequest.dialogRequestId) {
                request.dispatchCallback()?.onSuccess()
            }
        }
    }
}
