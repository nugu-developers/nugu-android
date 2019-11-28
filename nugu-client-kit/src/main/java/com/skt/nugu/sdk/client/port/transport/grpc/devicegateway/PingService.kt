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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PingRequest
import devicegateway.grpc.PolicyResponse
import devicegateway.grpc.VoiceServiceGrpc
import io.grpc.Status
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * This class is designed to manage healthcheck of DeviceGateway
 */
internal class PingService(
    blockingStub: VoiceServiceGrpc.VoiceServiceBlockingStub,
    healthCheckPolicy: PolicyResponse.HealthCheckPolicy,
    observer: Observer
) {
    private val timeout: Long = healthCheckPolicy.healthCheckTimeout.toLong()
    private val pingInterval: Long =  healthCheckPolicy.retryDelay.toLong()
    private var intervalFuture: ScheduledFuture<*>? = null
    private var isShutdown = false

    companion object {
        private const val TAG = "PingService"
        private const val defaultInterval: Long = 1000 * 30L
        private const val defaultTimeout: Long = 1000 * 10L
    }

    private val executorService: ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }


    interface Observer {
        fun onPingRequestAcknowledged()
        fun onError(code: Status.Code)
    }

    init {
        intervalFuture = executorService.scheduleWithFixedDelay({
            try {
                val response = blockingStub.withDeadlineAfter(
                    if (timeout > 0) timeout else defaultTimeout,
                    TimeUnit.MILLISECONDS
                ).ping(PingRequest.newBuilder().build())
                if (!isShutdown) {
                    observer.onPingRequestAcknowledged()
                }
            } catch (th: Throwable) {
                shutdown()
                val status = Status.fromThrowable(th)
                observer.onError(status.code)
            }
        }, 0, if (pingInterval > 0) pingInterval else defaultInterval, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        if (isShutdown) {
            Logger.w(TAG, "[shutdown] already shutdown")
            return
        }

        isShutdown = true
        intervalFuture?.cancel(true)
        intervalFuture = null
        executorService.shutdown()
    }
}