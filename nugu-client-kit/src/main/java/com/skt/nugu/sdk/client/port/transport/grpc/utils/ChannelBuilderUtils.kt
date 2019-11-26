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

import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.HeaderClientInterceptor
import com.skt.nugu.sdk.core.utils.SdkVersion
import io.grpc.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class ChannelBuilderUtils {
    companion object {
        fun createChannelBuilderWith(
            options: Options,
            authorization: String
        ): ManagedChannelBuilder<*> {
            val builder = ManagedChannelBuilder
                .forAddress(options.address, options.port)
                .userAgent(userAgent())

            if (!options.hostname.isBlank()) {
                builder.overrideAuthority(options.hostname)
            }
            if (options.debug) {
                // adb shell setprop log.tag.io.grpc.ChannelLogger DEBUG
                builder.maxTraceEvents(100)
                val logger = java.util.logging.Logger.getLogger(ChannelLogger::class.java.name)
                logger.level = Level.ALL
            }
            return builder.intercept(
                HeaderClientInterceptor(
                    authorization
                )
            )
        }

        private fun userAgent(): String {
            return "OpenSDK/" + SdkVersion.currentVersion
        }

        fun shutdown(channel : ManagedChannel?) {
            var isTerminated = false
            channel?.apply {
                try {
                    if (isTerminated()) {
                        isTerminated = true
                        return@apply
                    }
                    isTerminated = shutdown().awaitTermination(10, TimeUnit.MILLISECONDS)
                } catch (e: Throwable) {
                    // nothing to do
                } finally {
                    if(!isTerminated) {
                        shutdownNow()
                    }
                }
            }
        }
    }
}