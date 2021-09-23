/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.client.port.transport.DefaultTransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import org.junit.Assert.*

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class GrpcTransportFactoryTest : TestCase() {
    private val serverInfo = NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 44)
        .registry(host = "registry.sk.com", 443)
        .build()

    @Test
    fun testBuildTransportFactory() {
        val transportFactory = GrpcTransportFactory(serverInfo)
        val transport: Transport = transportFactory.createTransport(
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNotNull(transport)
    }

    @Test
    fun testDefaultTransportFactory() {
        val transportFactory = DefaultTransportFactory.buildTransportFactory()
        val transport: Transport = transportFactory.createTransport(
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNotNull(transport)
    }

    @Test
    fun testCallConnect() {
        val transport: Transport = mock()
        verify(transport, never()).connect()
        verify(transport, never()).disconnect()
    }

}