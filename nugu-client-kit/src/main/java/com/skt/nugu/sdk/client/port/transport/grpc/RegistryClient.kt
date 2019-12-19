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

import com.google.gson.*
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import devicegateway.grpc.PolicyResponse
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import com.squareup.okhttp.HttpUrl
import devicegateway.grpc.Charge
import devicegateway.grpc.Protocol
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.Executors

/**
 *  Implementation of registry
 **/
internal class RegistryClient(private var address: String) : Transport {
    companion object {
        private const val TAG = "RegistryClient"
        var cachedPolicy: PolicyResponse? = null
        const val GRPC_PROTOCOL = "H2_GRPC"
        const val HTTPS_SCHEME = "https"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val isShutdown = AtomicBoolean(false)

    interface Observer {
        fun onCompleted(policy: PolicyResponse?)
        fun onError(reason: ChangedReason)
    }

    fun getPolicy(token: String?, observer: Observer) {
        if (isShutdown.get()) {
            Logger.w(TAG, "[getPolicy] already shutdown")
            return
        }
        executor.submit {
            val client = OkHttpClient()
            client.connectTimeout
            val httpUrl = HttpUrl.Builder()
                .scheme(HTTPS_SCHEME)
                .host(address)
                .addPathSegment("v1")
                .addPathSegment("policies")
                .addQueryParameter("protocol", GRPC_PROTOCOL)
                .build()

            val request = Request.Builder().url(httpUrl)
                .header("Accept", "application/json")
                .header("Authorization", token ?: "")
                .build()
            try {
                val response = client.newCall(request).execute()
                val code = response.code()
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val jsonObject = JsonParser().parse(response.body().string()).asJsonObject
                        if (!(jsonObject.has("healthCheckPolicy") && jsonObject.has("serverPolicies"))) {
                            observer.onError(ChangedReason.FAILURE_PROTOCOL_ERROR)
                            return@submit
                        }

                        val policyBuilder = PolicyResponse.newBuilder()
                        jsonObject.get("healthCheckPolicy").apply {
                            policyBuilder.setHealthCheckPolicy(
                                PolicyResponse.HealthCheckPolicy.newBuilder()
                                    .setBeta(asJsonObject.get("beta").asFloat)
                                    .setAccumulationTime(asJsonObject.get("accumulationTime").asInt)
                                    .setHealthCheckTimeout(asJsonObject.get("healthCheckTimeout").asInt)
                                    .setRetryCountLimit(asJsonObject.get("retryCountLimit").asInt)
                                    .setRetryDelay(asJsonObject.get("retryDelay").asInt)
                                    .setTtl(asJsonObject.get("ttl").asInt)
                                    .setTtlMax(asJsonObject.get("ttlMax").asInt)
                            )
                        }
                        jsonObject.get("serverPolicies").asJsonArray.forEach {
                            policyBuilder.addServerPolicy(
                                PolicyResponse.ServerPolicy.newBuilder()
                                    .setProtocolValue(
                                        Protocol.valueOf(it.asJsonObject.get("protocol").asString.toUpperCase()).ordinal
                                    )
                                    .setChargeValue(
                                        Charge.valueOf(it.asJsonObject.get("charge").asString.toUpperCase()).ordinal
                                    )
                                    .setPort(it.asJsonObject.get("port").asInt)
                                    .setHostName(it.asJsonObject.get("hostname").asString)
                                    .setAddress(it.asJsonObject.get("address").asString)
                                    .setRetryCountLimit(it.asJsonObject.get("retryCountLimit").asInt)
                                    .setConnectionTimeout(it.asJsonObject.get("connectionTimeout").asInt)
                            )
                        }
                        notifyPolicy(policyBuilder.build(), observer)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        observer.onError(ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        cachedPolicy?.let {
                            notifyPolicy(it, observer)
                        } ?: run {
                            when (code) {
                                in 400..499 -> observer.onError(ChangedReason.INTERNAL_ERROR)
                                in 500..599 -> observer.onError(ChangedReason.SERVER_INTERNAL_ERROR)
                                else -> observer.onError(ChangedReason.UNRECOVERABLE_ERROR)
                            }
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                observer.onError(ChangedReason.DNS_TIMEDOUT)
            } catch (e: IOException) {
                observer.onError(ChangedReason.CONNECTION_TIMEDOUT)
            }
        }
    }

    private fun notifyPolicy(policy: PolicyResponse?, observer: Observer) {
        observer.onCompleted(policy)
        // cache setting
        cachedPolicy = policy
    }

    override fun connect(): Boolean {
        throw NotImplementedError()
    }

    override fun isConnected(): Boolean {
        throw NotImplementedError()
    }

    override fun send(request: MessageRequest): Boolean {
        throw NotImplementedError()
    }

    override fun isConnectedOrConnecting(): Boolean {
        throw NotImplementedError()
    }

    override fun disconnect() {
        // nothing to do
    }
    override fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            executor.shutdown()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}