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
package com.skt.nugu.sdk.client.port.transport.grpc.utils

import com.skt.nugu.sdk.client.port.transport.grpc.HeaderClientInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc.ServerPolicy
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UserAgent
import io.grpc.*
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
            authorization: String?
        ): ManagedChannelBuilder<*> {
            val channelBuilder = ManagedChannelBuilder
                .forAddress(policy.hostname, policy.port)
                .userAgent(userAgent())
            return channelBuilder.intercept(
                HeaderClientInterceptor(
                    authorization.toString()
                )
            )
        }

        private fun userAgent(): String {
            return UserAgent.toString()
        }

        /** Shuts down the gRPC channel */
        fun shutdown(channel: ManagedChannel?) {
            channel?.apply {
                try {
                    if (!shutdown().awaitTermination(1, TimeUnit.SECONDS)) {
                        Logger.d(TAG,  "Unable to gracefully shutdown the gRPC ManagedChannel. Will attempt an immediate shutdown.")
                        shutdownNow()
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