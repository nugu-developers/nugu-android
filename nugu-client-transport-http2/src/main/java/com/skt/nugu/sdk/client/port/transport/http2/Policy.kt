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

package com.skt.nugu.sdk.client.port.transport.http2

import com.google.gson.annotations.SerializedName

data class Policy(
    @SerializedName("serverPolicies")
    val serverPolicy: List<ServerPolicy>,
    @SerializedName("healthCheckPolicy")
    val healthCheckPolicy: HealthCheckPolicy
)

data class ServerPolicy(
    @SerializedName("protocol")
    val protocol: String,
    @SerializedName("hostname")
    val hostname: String,
    @SerializedName("port")
    val port: Int,
    @SerializedName("retryCountLimit")
    val retryCountLimit: Int,
    @SerializedName("connectionTimeout")
    val connectionTimeout: Int,
    @SerializedName("charge")
    val charge : String
)

data class HealthCheckPolicy(
    @SerializedName("ttl")
    val ttl: Int,
    @SerializedName("ttlMax")
    val ttlMax: Int,
    @SerializedName("beta")
    val beta: Float,
    @SerializedName("retryCountLimit")
    val retryCountLimit: Int,
    @SerializedName("retryDelay")
    val retryDelay: Int,
    @SerializedName("healthCheckTimeout")
    val healthCheckTimeout: Int,
    @SerializedName("accumulationTime")
    val accumulationTime: Int
)
