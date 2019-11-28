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
import devicegateway.grpc.PolicyRequest
import devicegateway.grpc.RegistryGrpc
import io.grpc.Status
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class RegistryService(private val observer: GrpcServiceListener) :
    GrpcServiceInterface {
    companion object {
        private const val TAG = "RegistryService"
    }
    @Volatile
    private var isShutdown = true
    private var blockingStub : RegistryGrpc.RegistryBlockingStub? = null

    private var executor = Executors.newSingleThreadExecutor()
    private var channel: Channels? = null
    
    /**
     * Execute a request to the device-gateway-registry.
     */
    private fun onExecute(): Boolean {
        try {
            if(blockingStub == null || channel == null) {
                return false
            }
            val timeout = channel!!.getOptions().connectionTimeout.toLong()
            // request grpc
            blockingStub!!.apply {
                val request = PolicyRequest.newBuilder().build()
                val response = withDeadlineAfter(timeout, TimeUnit.MILLISECONDS).getPolicy(request)
                if(!isShutdown) {
                    observer.onRegistryConnected(response)
                }
            }
        } catch (e: Throwable) {
            val status = Status.fromThrowable(e)
            when(status.code) {
                Status.Code.UNAUTHENTICATED -> {
                    observer.onUnAuthenticated()
                    return true
                }
                else -> {}
            }
            Logger.d(TAG, "[RegistryService] throwable ${e.message}")
            return false
        }
        return true
    }

    /**
     * Initializes, Creates a new async stub and timeout for the PingService.
     */
    override fun connect(channel : Channels) {
        Logger.d(TAG, "[RegistryService] connect")
        if(!isShutdown) {
            return
        }
        this.channel = channel
        isShutdown = false

        // The underlying channel of the stub.
        this.blockingStub = RegistryGrpc.newBlockingStub(channel.getChannel())

        if(executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }

        executor.submit {
            if(!onExecute()) {
                if(!isShutdown) {
                    observer.onConnectTimeout()
                }
            }
        }
    }

    /**
     * Explicitly clean up resources.
     */
    override fun shutdown() {
        Logger.d(TAG, "[RegistryService] shutdown")
        isShutdown = true
        executor.shutdown()
        this.blockingStub = null
    }

}