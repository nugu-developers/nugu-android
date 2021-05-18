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

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger

/**
 * TransportFactory to create [GrpcTransport].
 * @param option the options for GrpcTransport
 * @param dns the DNS service used to lookup IP addresses for hostnames.
 */

class GrpcTransportFactory(
    private val address: String = DEFAULT_ADDRESS,
    private val dnsLookup: DnsLookup? = null
) : TransportFactory {
    companion object {
        const val TAG = "GrpcTransportFactory"
        const val DEFAULT_ADDRESS = "reg-http.sktnugu.com"
    }
    /**
     * Create a Transport.
     */
    override fun createTransport(
        authDelegate: AuthDelegate,
        messageConsumer: MessageConsumer,
        transportObserver: TransportListener
    ): Transport {
        return GrpcTransport.create(
            address,
            dnsLookup,
            authDelegate,
            messageConsumer,
            transportObserver
        )
    }
    override fun keepConnection(enabled: Boolean) : Boolean {
        Logger.w(TAG, "keepConnection not supported by this grpc version, try grpc v2 or h2")
        return false
    }

    override fun keepConnection(): Boolean {
        Logger.w(TAG, "keepConnection not supported by this grpc version, try grpc v2 or h2")
        return false
    }
}