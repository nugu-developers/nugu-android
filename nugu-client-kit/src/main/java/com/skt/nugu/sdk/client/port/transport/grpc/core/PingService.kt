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
package com.skt.nugu.sdk.client.port.transport.grpc.core

import com.skt.nugu.sdk.client.port.transport.grpc.Channels
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import java.util.concurrent.*


/**
 * This class is designed to manage healthcheck of DeviceGateway
 */
internal class PingService(val observer: GrpcServiceListener) :
    GrpcServiceInterface {
    companion object {
        private const val TAG = "GrpcTransport"
        private const val pingInterval: Long = 1000 * 30
        private const val pingTimeout: Long = 1000 * 10
    }

    private var blockingStub: VoiceServiceGrpc.VoiceServiceBlockingStub? = null
    @Volatile
    private var isShutdown = true
    private var executorService: ScheduledThreadPoolExecutor? = null
    private var intervalFuture: ScheduledFuture<*>? = null
    private var channel: Channels? = null

    /**
     * Execute a request to the DeviceGateway.
     */
    private fun onExecute(): Boolean {
        try {
            if(blockingStub == null) {
                return false
            }
            //Provide interval period for ping.
            //It does not occur when {@code #onExecute#onNext#onError} is called.
            var timeout = channel?.getBackoff()!!.healthCheckTimeout
            if (timeout <= 0) {
                timeout =
                    pingTimeout
            }
            // request grpc
            blockingStub!!.apply {
                val request = PingRequest.newBuilder().build()
                withDeadlineAfter(timeout, TimeUnit.MILLISECONDS).ping(request)
            }
        } catch (e: Throwable) {
            Logger.d(TAG, "[PingService] throwable ${e.message}")
            return false
        }
        return true
    }

    /**
     * Initializes, Creates a new async stub and timeout for the PingService.
     */
    override fun connect(channel: Channels) {
        if (!isShutdown) {
            Logger.d(TAG, "[PingService] duplicate created")
            return
        }
        this.channel = channel
        this.isShutdown = false

        executorService = ScheduledThreadPoolExecutor(1)
        executorService?.removeOnCancelPolicy = true
        this.blockingStub = VoiceServiceGrpc.newBlockingStub(channel.getChannel())
        var timeout = channel.getBackoff().retryDelay
        if (timeout <= 0) {
            timeout = pingInterval
        }
        this.intervalFuture?.cancel(true)
        this.intervalFuture = executorService?.scheduleWithFixedDelay({
            val success = onExecute()
            if (!isShutdown) {
                observer.onPingRequestAcknowledged(success)
            }
        }, 0, timeout, TimeUnit.MILLISECONDS)
    }


    /**
     * Explicitly clean up resources.
     */
    override fun shutdown() {
        Logger.d(TAG, "[PingService] shutdown")
        if (!isShutdown) {
            this.isShutdown = true
            this.intervalFuture?.cancel(true)
            this.executorService?.shutdown()
            this.blockingStub = null
        }
    }

}