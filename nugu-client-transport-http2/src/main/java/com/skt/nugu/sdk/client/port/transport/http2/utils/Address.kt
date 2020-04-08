package com.skt.nugu.sdk.client.port.transport.http2.utils

data class Address(
    val host: String,
    val port: Int
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Address

        if (!host.equals(other.host)) return false
        if (port != other.port) return false
        return true
    }

    override fun toString(): String {
        return "Address{${host}:${port}}"
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + port
        return result
    }
}