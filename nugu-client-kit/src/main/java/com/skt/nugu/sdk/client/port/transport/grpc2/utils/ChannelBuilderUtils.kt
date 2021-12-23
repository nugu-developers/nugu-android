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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.google.common.annotations.VisibleForTesting
import com.skt.nugu.sdk.client.port.transport.grpc2.HeaderClientInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc2.ServerPolicy
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.transport.ChannelOptions
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UserAgent
import io.grpc.*
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Create the gRPC channel and initialization.
 **/
class ChannelBuilderUtils {
    companion object {
        private const val TAG = "ChannelBuilderUtils"

        /** configures the gRPC channel. */
        fun createChannelBuilderWith(
            policy: ServerPolicy,
            channelOptions: ChannelOptions?,
            authDelegate: AuthDelegate,
            delegate: HeaderClientInterceptor.Delegate,
            isStartReceiveServerInitiatedDirective: () -> Boolean
        ): ManagedChannelBuilder<*> {
            val channelBuilder = OkHttpChannelBuilder
                .forAddress(policy.hostname, policy.port)
                .userAgent(userAgent())

            channelOptions?.let {
                // Do not use idleTimeout in ServerInitiatedDirective.
                if (!isStartReceiveServerInitiatedDirective()) {
                    channelBuilder.idleTimeout(it.idleTimeout.value, it.idleTimeout.unit)
                }
            }
            Logger.i(TAG,  "userAgent=${userAgent()}")
            return channelBuilder.intercept(
                HeaderClientInterceptor(
                    authDelegate,
                    delegate
                )
            )
        }

        @VisibleForTesting
        fun userAgent(): String {
            return UserAgent.toString()
        }

        /** Shuts down the gRPC channel */
        fun shutdown(channel: ManagedChannel?) {
            channel?.apply {
                try {
                    if (!shutdown().awaitTermination(1, TimeUnit.SECONDS)) {
                        if (!shutdownNow().awaitTermination(1, TimeUnit.SECONDS)) {
                            Logger.d(TAG,  "Unable to gracefully shutdown the gRPC ManagedChannel. Will attempt an immediate shutdown.")
                        }
                    }
                } catch (e: InterruptedException) {
                    // (Re-)Cancel if current thread also interrupted
                    shutdownNow()
                    // Preserve interrupt status
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}