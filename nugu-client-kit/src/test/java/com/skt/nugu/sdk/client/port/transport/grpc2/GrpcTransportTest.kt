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

import com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway.DeviceGatewayTransport
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import org.junit.Assert.*

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.net.InetAddress

class GrpcTransportTest : TestCase() {
    private val serverInfo = NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 44)
        .registry(host = "registry.sk.com", 443)
        .build()

    @Test
    fun testCreate() {
        val transport = GrpcTransport.create(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNotNull(transport)
    }

    @Test
    fun testGetDelegatedServerInfo() {
        val transport = GrpcTransport(
            serverInfo = NuguServerInfo(delegate = object : NuguServerInfo.Delegate {
                override val serverInfo: NuguServerInfo
                    get() = run {
                        return NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 443)
                            .registry(host = "registry.sk.com", 443)
                            .build()
                    }
            }),
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        val serverInfo2 = transport.getDelegatedServerInfo()
        Assert.assertEquals(
            NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 443)
                .registry(host = "registry.sk.com", 443)
                .build(), serverInfo2
        )
    }

    @Test
    fun testIsConnected() {
        val transport = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertFalse(transport.isConnected())
    }

    @Test
    fun testCheckAuthorizationIfEmpty() {
        val transport = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = object : AuthDelegate {
                override fun getAuthorization(): String? {
                    return "Authorization"
                }
            },
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNotNull(transport.checkAuthorizationIfEmpty {})

        val transport2 = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = object : AuthDelegate {
                override fun getAuthorization(): String? {
                    return null
                }
            },
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNull(transport2.checkAuthorizationIfEmpty {})
    }

    @Test
    fun testTryConnectToDeviceGateway() {
        val transport = GrpcTransport(
            serverInfo = NuguServerInfo(delegate = object : NuguServerInfo.Delegate {
                override val serverInfo: NuguServerInfo
                    get() = run {
                        return NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 443)
                            .registry(host = "registry.sk.com", 443)
                            .build()
                    }
            }),
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = object : AuthDelegate {
                override fun getAuthorization(): String? {
                    return "Authorization"
                }
            },
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = { false }
        )
        Assert.assertTrue(
            transport.tryConnectToDeviceGateway(
                RegistryClient.DefaultPolicy(
                    serverInfo
                )
            )
        )
    }

    @Test
    fun testSetState() {
        val transport = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = mock(),
            isStartReceiveServerInitiatedDirective = mock()
        )

        transport.setState(
            TransportState.DetailedState.CONNECTING,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.CONNECTING)
        transport.setState(
            TransportState.DetailedState.CONNECTING_REGISTRY,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(
            transport.getDetailedState(),
            TransportState.DetailedState.CONNECTING_REGISTRY
        )
        transport.setState(
            TransportState.DetailedState.CONNECTING_DEVICEGATEWAY,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(
            transport.getDetailedState(),
            TransportState.DetailedState.CONNECTING_DEVICEGATEWAY
        )
        transport.setState(
            TransportState.DetailedState.CONNECTED,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.CONNECTED)
        Assert.assertTrue(transport.isConnected())

        transport.setState(
            TransportState.DetailedState.RECONNECTING,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.RECONNECTING)

        Assert.assertTrue(transport.isConnectedOrConnecting())

        Assert.assertFalse(
            transport.setState(
                TransportState.DetailedState.HANDOFF,
                ConnectionStatusListener.ChangedReason.NONE
            )
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.RECONNECTING)

        Assert.assertFalse(
            transport.setState(
                TransportState.DetailedState.IDLE,
                ConnectionStatusListener.ChangedReason.NONE
            )
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.RECONNECTING)

        transport.setState(
            TransportState.DetailedState.FAILED,
            ConnectionStatusListener.ChangedReason.NONE
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.FAILED)

        Assert.assertFalse(
            transport.setState(
                TransportState.DetailedState.DISCONNECTING,
                ConnectionStatusListener.ChangedReason.NONE
            )
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.FAILED)

        Assert.assertFalse(
            transport.setState(
                TransportState.DetailedState.DISCONNECTED,
                ConnectionStatusListener.ChangedReason.NONE
            )
        )
        Assert.assertEquals(transport.getDetailedState(), TransportState.DetailedState.FAILED)
    }

    @Test
    fun testHandoffConnection() {
        val transport = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = object : TransportListener {
                override fun onConnecting(
                    transport: Transport,
                    reason: ConnectionStatusListener.ChangedReason
                ) {
                    Assert.assertEquals(
                        reason,
                        ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED
                    )
                }

                override fun onConnected(transport: Transport) {
                    Assert.assertNull(transport)
                }

                override fun onDisconnected(
                    transport: Transport,
                    reason: ConnectionStatusListener.ChangedReason
                ) {
                    Assert.assertNull(transport)
                }
            },
            isStartReceiveServerInitiatedDirective = mock()
        )
        RegistryClient.cachedPolicy = RegistryClient.DefaultPolicy(serverInfo)
        transport.handoffConnection(
            protocol = "GRPC",
            hostname = "hostname",
            address = "address",
            port = 443,
            retryCountLimit = 3,
            connectionTimeout = 1000,
            "Normal"
        )
    }

    @Test
    fun testNewCall() {
        val transport = GrpcTransport(
            serverInfo = serverInfo,
            dnsLookup = object : DnsLookup {
                override fun lookup(hostname: String): List<InetAddress> {
                    return emptyList()
                }
            },
            callOptions = null,
            channelOptions = null,
            authDelegate = mock(),
            messageConsumer = mock(),
            transportObserver = object : TransportListener {
                override fun onConnecting(
                    transport: Transport,
                    reason: ConnectionStatusListener.ChangedReason
                ) {
                    Assert.assertEquals(
                        reason,
                        ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED
                    )
                }

                override fun onConnected(transport: Transport) {
                    Assert.assertNull(transport)
                }

                override fun onDisconnected(
                    transport: Transport,
                    reason: ConnectionStatusListener.ChangedReason
                ) {
                    Assert.assertNull(transport)
                }
            },
            isStartReceiveServerInitiatedDirective = mock()
        )

        val request: MessageRequest = EventMessageRequest.Builder(
            "context",
            "namespace",
            "name", "version"
        ).build()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = transport.newCall(transport, request, null, listener)
        Assert.assertEquals(call.request, request)
        Assert.assertEquals(call.transport, transport)
    }
}