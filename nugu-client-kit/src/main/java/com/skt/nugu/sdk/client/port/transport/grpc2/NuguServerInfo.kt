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
package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.client.port.transport.grpc2.utils.Address
import com.skt.nugu.sdk.core.utils.Logger
import java.net.URL

data class NuguServerInfo(
    val registry: Address,
    val deviceGW: Address
) {
    constructor(delegate: Delegate) : this( defaultRegistry, defaultServer) {
        this.delegate = delegate
    }
    private var delegate: Delegate? = null
    fun delegate(): Delegate? = delegate

    companion object {
        const val TAG = "NuguServerInfo"
        const val DEFAULT_DEVICE_GATEWAY_REGISTRY_HOST = "reg-http.sktnugu.com"
        const val DEFAULT_DEVICE_GATEWAY_SERVER_HOST = "dggrpc.sktnugu.com"
        const val HTTPS_PORT = 443
        const val HTTP_PORT = 80
        val defaultRegistry = Address(
            DEFAULT_DEVICE_GATEWAY_REGISTRY_HOST,
            HTTPS_PORT
        )
        val defaultServer = Address(
            DEFAULT_DEVICE_GATEWAY_SERVER_HOST,
            HTTPS_PORT
        )
    }

    override fun toString(): String {
        val changed = !(registry == Address(
            DEFAULT_DEVICE_GATEWAY_REGISTRY_HOST,
            HTTPS_PORT
        ) && deviceGW == Address(
            DEFAULT_DEVICE_GATEWAY_SERVER_HOST,
            HTTPS_PORT
        ))

        val builder = StringBuilder("NuguServerInfo { protocol: GRPC")
            .append(", server: ")
        if (changed) {
            builder.append("registry(").append("host: ").append(registry.host)
                .append(", port: ").append(registry.port).append(")")
            builder.append("deviceGW(").append("host: ").append(deviceGW.host)
                .append(", port: ").append(deviceGW.port).append(")")
        } else {
            builder.append("PRD")
        }
        return builder.append(" }").toString()
    }

    fun checkServerSettings() {
        Logger.d(TAG, toString())

        if (registry != Address(DEFAULT_DEVICE_GATEWAY_REGISTRY_HOST, HTTPS_PORT)) {
            Logger.w(TAG, "Registry host or port has been changed. ($registry)")
        }
        if (deviceGW != Address(DEFAULT_DEVICE_GATEWAY_SERVER_HOST, HTTPS_PORT)) {
            Logger.w(TAG, "DeviceGW host or port has been changed. ($deviceGW)")
        }
    }

    class Builder {
        private var registry: Address =
            Address(
                DEFAULT_DEVICE_GATEWAY_REGISTRY_HOST,
                HTTPS_PORT
            )
        private var deviceGW: Address =
            Address(
                DEFAULT_DEVICE_GATEWAY_SERVER_HOST,
                HTTPS_PORT
            )
        private var keepConnection = false

        fun registry(host: String, port: Int = HTTPS_PORT): Builder {
            registry =
                Address(
                    host,
                    port
                )
            return this
        }

        fun registry(urlStr: String?): Builder {
            try {
                val url = URL(urlStr)
                val host = url.host.toString()
                var port = url.port
                if (port == -1) {
                    port = if (url.protocol == "https")
                        HTTPS_PORT else HTTP_PORT
                }
                registry =
                    Address(
                        host,
                        port
                    )
            } catch ( e: Throwable) {
                Logger.e(TAG, "[registry] Invalid URL=${urlStr}, exception:$e")
                registry = Address("", -1)
            }
            return this
        }
        fun deviceGW(host: String, port: Int = HTTPS_PORT): Builder {
            deviceGW =
                Address(
                    host,
                    port
                )
            return this
        }

        fun deviceGW(urlStr: String?): Builder {
            try {
                val url = URL(urlStr)
                val host = url.host.toString()
                var port = url.port
                if (port == -1) {
                    port = if (url.protocol == "https")
                        HTTPS_PORT else HTTP_PORT
                }
                deviceGW =
                    Address(
                        host,
                        port
                    )
            } catch ( e: Throwable) {
                Logger.e(TAG, "[deviceGW] Invalid URL=${urlStr}, exception:$e")
                deviceGW = Address("", -1)
            }
            return this
        }

        fun build() =
            NuguServerInfo(
                registry,
                deviceGW
            )
    }

    interface Delegate {
        val serverInfo: NuguServerInfo
    }
}
