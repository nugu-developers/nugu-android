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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.h2

import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.utils.Logger
import com.squareup.okhttp.*
import devicegateway.grpc.PolicyResponse
import io.grpc.Status
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
    val option: Options,
    val client: OkHttpClient,
    private val healthCheckPolicy: PolicyResponse.HealthCheckPolicy,
    val observer: Observer
) {
    private val timeout: Long = healthCheckPolicy.healthCheckTimeout.toLong()
    private var intervalFuture: ScheduledFuture<*>? = null
    private val isShutdown = AtomicBoolean(false)

    companion object {
        private const val TAG = "PingService"
        private const val defaultInterval: Long = 1000 * 60L
        private const val defaultTimeout: Long = 1000 * 10L

        fun create(
            option: Options,
            client: OkHttpClient,
            healthCheckPolicy: PolicyResponse.HealthCheckPolicy,
            observer: Observer
        ): PingService {
            return PingService(option, client, healthCheckPolicy, observer)
        }
    }

    private val executorService: ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }


    interface Observer {
        fun onPingRequestAcknowledged()
        fun onError(status: Status)
    }

    private val pingTask = Runnable {
        try {
            //if (timeout > 0) timeout else defaultTimeout,
            if(sendPing()) {
                nextInterval()
            }
        } catch (e: Throwable) {
            if (!isShutdown.get()) {
                val status = Status.fromThrowable(e)
                Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
                observer.onError(status)
            }
        }
    }

    init {
        intervalFuture = executorService.schedule(pingTask, 0, TimeUnit.MILLISECONDS)
    }

    private fun nextInterval() {
        if (isShutdown.get()) {
            return
        }

        val retryDelay: Long = if (healthCheckPolicy.retryDelay == 0) {
            defaultInterval
        } else healthCheckPolicy.retryDelay.toLong()

        val ttlMax: Long = healthCheckPolicy.ttlMax.toLong()
        val beta = healthCheckPolicy.beta
        val internal = Math.max(
            ttlMax + (beta * Math.log(Random().nextDouble())).toLong(),
            retryDelay
        )
        intervalFuture = executorService.schedule(pingTask, internal, TimeUnit.MILLISECONDS)
    }

    fun sendPing(): Boolean {
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host(option.hostname)
            .addPathSegment("v1")
            .addPathSegment("ping")
            .build()
        val request = Request.Builder().url(httpUrl)
            .get()
            .build()

        val startTime = System.nanoTime()
        val response = client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request?, e: IOException?) {
                if (!isShutdown.get()) {
                    val status = Status.fromThrowable(e)
                    Logger.d(TAG, "[onError] ${status.code}, ${status.description}, $e")
                    observer.onError(status)
                }
            }

            override fun onResponse(response: Response?) {
                val duration = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime)
                val code = response?.code()
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        observer.onPingRequestAcknowledged()
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> observer.onError(Status.UNAUTHENTICATED)
                    in 500..599 -> observer.onError(Status.INTERNAL)
                    else -> observer.onError(Status.UNKNOWN)
                }
            }
        })
        return true
    }

    fun shutdown() {
        if(isShutdown.compareAndSet(false, true)) {
            intervalFuture?.cancel(true)
            executorService.shutdown()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}