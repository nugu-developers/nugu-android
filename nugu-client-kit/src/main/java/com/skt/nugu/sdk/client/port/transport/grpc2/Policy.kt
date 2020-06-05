package com.skt.nugu.sdk.client.port.transport.grpc2

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
