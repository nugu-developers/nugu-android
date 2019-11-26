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
package com.skt.nugu.sdk.client.port.transport.grpc

import com.skt.nugu.sdk.client.port.transport.grpc.utils.BackOff
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PolicyRequest
import devicegateway.grpc.PolicyResponse
import devicegateway.grpc.RegistryGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class RegistryClient {
    companion object {
        private const val TAG = "RegistryClient"
        fun newClient() = RegistryClient()
    }
    private var backoff : BackOff = BackOff.DEFAULT()
    private var channel: ManagedChannel? = null

    var policy: PolicyResponse? = null
    @Volatile
    var isShutdown = false

    interface Observer {
        fun onCompleted()
        fun onError(code: Status.Code)
    }

    fun getPolicy(channel: ManagedChannel, observer: Observer) {
        this.channel = channel

        RegistryGrpc.newStub(channel).getPolicy(
            PolicyRequest.newBuilder().build(),
            object : StreamObserver<PolicyResponse> {
                override fun onNext(value: PolicyResponse?) {
                    Logger.d(TAG, "[onNext] $value")
                    policy = value
                }

                override fun onError(t: Throwable?) {
                    val status = Status.fromThrowable(t)
                    awaitRetry(status.code)
                }

                override fun onCompleted() {
                    if (policy == null) {
                        awaitRetry(Status.Code.NOT_FOUND)
                    } else {
                        if(!isShutdown) {
                            shutdown()
                            observer.onCompleted()
                        }
                    }
                }

                private fun awaitRetry(code: Status.Code) = backoff.awaitRetry(code, object : BackOff.Observer {
                    override fun onError(reason: String) {
                        Logger.w(TAG, "[awaitRetry] Error : $reason")

                        if(!isShutdown) {
                            shutdown()
                            observer.onError(code)
                        }
                    }

                    override fun onRetry(retriesAttempted: Int) {
                        getPolicy(channel, observer)
                    }
                })

            })
    }

    fun shutdown() {
        if(isShutdown) {
            return
        }
        isShutdown = true

        backoff.reset()

        this.channel?.shutdown()
        this.channel = null
    }
}