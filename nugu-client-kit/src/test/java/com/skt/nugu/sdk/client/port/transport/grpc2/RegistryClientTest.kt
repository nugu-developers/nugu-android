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

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup
import org.junit.Assert.*

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.net.InetAddress

class RegistryClientTest : TestCase() {
    @Test
    fun testRegistryClient() {
        val client = RegistryClient(object : DnsLookup {
            override fun lookup(hostname: String): List<InetAddress> {
                return emptyList()
            }
        })
        Assert.assertNotNull(client)
    }

    @Test
    fun testDefaultPolicy() {
        val policy = RegistryClient.DefaultPolicy(
            NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 443)
                .registry(host = "registry.sk.com", 443)
                .build()
        )
        Assert.assertNotNull(policy)
    }

    @Test
    fun testNotifyPolicy() {
        val policy1 = RegistryClient.DefaultPolicy(
            NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 443)
                .registry(host = "registry.sk.com", 443)
                .build()
        )
        val client = RegistryClient(object : DnsLookup {
            override fun lookup(hostname: String): List<InetAddress> {
                return emptyList()
            }
        })
        client.notifyPolicy(policy1, object : RegistryClient.Observer {
            override fun onCompleted(policy2: Policy?) {
                Assert.assertEquals(policy1, policy2)
            }

            override fun onError(reason: ConnectionStatusListener.ChangedReason) {
            }
        })
    }

    @Test
    fun testShutdown() {
        val client = RegistryClient(object : DnsLookup {
            override fun lookup(hostname: String): List<InetAddress> {
                return emptyList()
            }
        })
        Assert.assertFalse(client.isShutdown.get())
        client.shutdown()
        Assert.assertTrue(client.isShutdown.get())
    }
}