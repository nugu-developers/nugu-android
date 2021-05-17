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

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup

/**
 * HTTP2TransportFactory to create [HTTP2Transport].
 * @param option the options for Transport
 */
class HTTP2TransportFactory(
    private var serverInfo: NuguServerInfo,
    private val dnsLookup: DnsLookup? = null
) : TransportFactory {

    override fun createTransport(
        authDelegate: AuthDelegate,
        messageConsumer: MessageConsumer,
        transportObserver: TransportListener,
        isStartReceiveServerInitiatedDirective: () -> Boolean
    ): Transport {
        return HTTP2Transport.create(
            serverInfo,
            dnsLookup,
            authDelegate,
            messageConsumer,
            transportObserver,
            isStartReceiveServerInitiatedDirective
        )
    }
}