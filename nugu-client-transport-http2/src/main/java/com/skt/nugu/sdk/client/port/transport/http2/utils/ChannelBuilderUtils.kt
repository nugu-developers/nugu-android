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
package com.skt.nugu.sdk.client.port.transport.http2.utils

import com.skt.nugu.sdk.client.port.transport.http2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.http2.interceptors.ForwardInterceptor
import com.skt.nugu.sdk.client.port.transport.http2.interceptors.SecurityInterceptor
import com.skt.nugu.sdk.client.port.transport.http2.interceptors.UserAgentInterceptor
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UserAgent
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Create the gRPC channel and initialization.
 **/
class ChannelBuilderUtils {
    companion object {
        private const val TAG = "ChannelBuilderUtils"
        private const val defaultTimeout: Long = 1000 * 10L
        /** configures the gRPC channel. */
        fun createChannelBuilderWith(
            policy: ServerPolicy,
            authDelegate: AuthDelegate
        ): OkHttpClient {
            val timeout = if(policy.connectionTimeout > 1000) policy.connectionTimeout.toLong() else defaultTimeout
            return OkHttpClient().newBuilder().protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(0L, TimeUnit.MINUTES)
                .readTimeout(0L, TimeUnit.MINUTES)
                .addInterceptor(
                    SecurityInterceptor(
                        authDelegate
                    )
                )
                .addInterceptor(
                    UserAgentInterceptor(
                        UserAgent.toString()
                    )
                )
                .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addNetworkInterceptor(ForwardInterceptor())
                .build()
        }


        /** Shuts down the gRPC channel */
        fun shutdown(executor: ExecutorService, client: OkHttpClient) {
            try {
                val finishLatch = CountDownLatch(1)
                executor.submit {
                    client.dispatcher.executorService.shutdown();
                    client.connectionPool.evictAll() // Close any persistent connections.
                    finishLatch.countDown()
                }
                finishLatch.await(1, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                Logger.d(TAG, "disconnect" + e.cause.toString())
            }
        }
    }
}