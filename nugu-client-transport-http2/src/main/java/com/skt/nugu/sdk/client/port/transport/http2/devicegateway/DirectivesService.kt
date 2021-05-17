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

import com.skt.nugu.sdk.client.port.transport.http2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.http2.Status
import com.skt.nugu.sdk.client.port.transport.http2.devicegateway.ResponseHandler.Companion.handleResponse
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.*
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

class DirectivesService(
    val policy: ServerPolicy,
    val client: OkHttpClient,
    private val observer: DeviceGatewayTransport
) {
    private val isShutdown = AtomicBoolean(false)
    private var call : Call? = null

        companion object {
        private const val TAG = "DirectivesService"
        private const val HTTPS_SCHEME = "https"

        fun create(
            policy: ServerPolicy,
            client: OkHttpClient,
            observer: DeviceGatewayTransport
        ) = DirectivesService(
            policy,
            client,
            observer
        ).apply {
            startDownStream()
        }
    }

    private fun startDownStream() {
        val httpUrl = HttpUrl.Builder()
            .scheme(HTTPS_SCHEME)
            .port(policy.port)
            .host(policy.hostname)
            .addPathSegment("v2")
            .addPathSegment("directives")
            .build()

        val request = Request.Builder().url(httpUrl).tag(responseCallback).build()
        call = client.newCall(request).apply {
            enqueue(responseCallback)
        }
    }

    private val responseCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            notifyOnError(e)
        }

        override fun onResponse(call: Call, response: Response) {
            when (response.code) {
                HttpURLConnection.HTTP_OK -> {
                    try {
                        response.handleResponse(null, observer)
                    } catch (e: Throwable) {
                        notifyOnError(e)
                    }
                }
                HttpURLConnection.HTTP_BAD_REQUEST -> observer.onError(Status.INTERNAL)
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                HttpURLConnection.HTTP_BAD_GATEWAY,
                HttpURLConnection.HTTP_UNAVAILABLE,
                HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> observer.onError(Status.UNAVAILABLE)
                else -> observer.onError(Status.UNKNOWN)
            }
        }
    }

    private fun notifyOnError(throwable : Throwable?) {
        if (!isShutdown.get()) {
            val status = Status.fromThrowable(throwable)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $throwable")
            observer.onError(status)
        }
    }

    fun shutdown() {
        Logger.w(TAG, "[shutdown]")
        if (isShutdown.compareAndSet(false, true)) {
            call?.cancel()
        }
    }
}