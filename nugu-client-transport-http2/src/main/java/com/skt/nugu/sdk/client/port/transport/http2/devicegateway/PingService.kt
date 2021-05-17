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

import com.skt.nugu.sdk.client.port.transport.http2.HealthCheckPolicy
import com.skt.nugu.sdk.client.port.transport.http2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.http2.Status
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.*
import java.io.IOException
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is designed to manage healthcheck of DeviceGateway
 */
internal class PingService(
    val policy: ServerPolicy,
    val client: OkHttpClient,
    private val healthCheckPolicy: HealthCheckPolicy,
    val observer: DeviceGatewayTransport
) {
    private var intervalFuture: ScheduledFuture<*>? = null
    private val isShutdown = AtomicBoolean(false)

    companion object {
        private const val TAG = "PingService"
        private const val HTTPS_SCHEME = "https"
        private const val defaultInterval: Long = 1000 * 60L
        private const val defaultTimeout: Long = 1000 * 10L

        fun create(
            policy: ServerPolicy,
            client: OkHttpClient,
            healthCheckPolicy: HealthCheckPolicy,
            observer: DeviceGatewayTransport
        ): PingService {
            return PingService(
                policy,
                client,
                healthCheckPolicy,
                observer
            )
        }
    }

    private val executorService: ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }

    init {
        nextInterval(0)
    }

    private fun newDelayMillis() : Long {
        val retryDelay: Long = if (healthCheckPolicy.retryDelay == 0) {
            defaultInterval
        } else healthCheckPolicy.retryDelay.toLong()

        val ttlMax: Long = healthCheckPolicy.ttlMax.toLong()
        val beta = healthCheckPolicy.beta
        return Math.max(
            ttlMax + (beta * Math.log(Random().nextDouble())).toLong(),
            retryDelay
        )
    }

    private fun executePingRequest() : Boolean{
        try {
            if (sendPing()) {
                return true
            }
        } catch (e: Throwable) {
            val status = Status.fromThrowable(e)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
            notifyOnError(status)
        }
        return false
    }

    private fun nextInterval(delay : Long) {
        if (isShutdown.get()) {
            return
        }
        intervalFuture = executorService.schedule({
            if(executePingRequest()) {
                nextInterval(newDelayMillis())
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    fun sendPing(): Boolean {
        val httpUrl = HttpUrl.Builder()
            .scheme(HTTPS_SCHEME)
            .port(policy.port)
            .host(policy.hostname)
            .addPathSegment("v2")
            .addPathSegment("ping")
            .build()

        val request = Request.Builder().url(httpUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val status = Status.fromThrowable(e)
                Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $e")
                notifyOnError(status)
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        if (!isShutdown.get()) {
                            observer.onPingRequestAcknowledged()
                        }
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> notifyOnError(Status.INTERNAL)
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    HttpURLConnection.HTTP_FORBIDDEN -> notifyOnError(Status.UNAUTHENTICATED)
                    HttpURLConnection.HTTP_BAD_GATEWAY,
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> notifyOnError(Status.UNAVAILABLE)
                    else -> notifyOnError(Status.UNKNOWN)
                }
            }

        })
        return true
    }

    private fun notifyOnError(status: Status) {
        if (!isShutdown.get()) {
            observer.onError(status)
        }
    }

    fun isStop() = isShutdown.get()
    fun shutdown() {
        Logger.w(TAG, "[shutdown]")
        if (isShutdown.compareAndSet(false, true)) {
            intervalFuture?.cancel(true)
            executorService.shutdown()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }

    fun newPing() {
        intervalFuture?.cancel(true)
        nextInterval(0)
    }
}