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
package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener

/**
 * TransportFactory to create [GrpcTransport].
 * @param option the options for GrpcTransport
 */

class GrpcTransportFactory(
    private val serverInfo: NuguServerInfo = NuguServerInfo.Default(),
    private val dnsLookup: DnsLookup? = null
) : TransportFactory {

    /**
     * Create a Transport.
     */
    override fun createTransport(
        authDelegate: AuthDelegate,
        messageConsumer: MessageConsumer,
        transportObserver: TransportListener
    ): Transport {
        return GrpcTransport.create(
            serverInfo,
            dnsLookup,
            authDelegate,
            messageConsumer,
            transportObserver
        )
    }

    override fun keepConnection(enabled: Boolean) : Boolean{
        if(serverInfo.keepConnection != enabled) {
            serverInfo.keepConnection = enabled
            return true
        }
        return false /* unchanged */
    }
}