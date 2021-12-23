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
package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.display.DisplayAgent
import com.skt.nugu.sdk.agent.display.ElementSelectedEventHandler
import com.skt.nugu.sdk.client.port.transport.DefaultTransportFactory
import com.skt.nugu.sdk.client.port.transport.grpc2.*
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toAttachmentMessage
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toDirectives
import com.skt.nugu.sdk.core.interfaces.transport.ChannelOptions
import com.skt.nugu.sdk.core.interfaces.transport.IdleTimeout
import io.grpc.Status
import org.junit.Assert.*
import devicegateway.grpc.AttachmentMessage
import devicegateway.grpc.DirectiveMessage

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

class DeviceGatewayClientTest : TestCase() {
    private val serverInfo = NuguServerInfo.Builder().deviceGW(host = "deviceGW.sk.com", 44)
        .registry(host = "registry.sk.com", 443)
        .build()
    private val mockTransport: Transport = object : Transport {
        override fun connect() = true
        override fun disconnect() = Unit
        override fun isConnected() = true
        override fun isConnectedOrConnecting() = true
        override fun send(call: Call) = true
        override fun shutdown() = Unit
        override fun newCall(
            activeTransport: Transport?,
            request: MessageRequest,
            headers: Map<String, String>?,
            listener: MessageSender.OnSendMessageListener
        ): Call {
            TODO("Not yet implemented")
        }
    }

    inner class MockDeviceGateway(val onSuccess: (DirectiveMessage) -> Unit) :
        DeviceGatewayTransport {
        override fun onReceiveDirectives(directiveMessage: DirectiveMessage) {
            onSuccess.invoke(directiveMessage)
        }

        override fun onReceiveAttachment(attachmentMessage: AttachmentMessage) = Unit
        override fun onError(status: Status, who: String) = Unit
        override fun onPingRequestAcknowledged() = Unit
        override fun send(call: Call): Boolean {
            onReceiveDirectives(mock())
            onReceiveAttachment(mock())
            return true
        }

        override fun connect(): Boolean = true
        override fun disconnect() = Unit
        override fun shutdown() = Unit
        override fun startDirectivesService() = Unit
        override fun stopDirectivesService() = Unit
    }

    @Test
    fun testDeviceGatewayClient() {
        val client = DeviceGatewayClient(
            policy = RegistryClient.DefaultPolicy(serverInfo),
            messageConsumer = mock(),
            transportObserver = mock(),
            authDelegate = mock(),
            callOptions = null,
            channelOptions = null,
            isStartReceiveServerInitiatedDirective = mock()
        )
        Assert.assertNotNull(client)
        Assert.assertFalse(client.isConnected())
        client.handleOnConnected()
        Assert.assertTrue(client.isConnected())
        client.shutdown()
        Assert.assertFalse(client.isConnected())
    }

    @Test
    fun testConnect() {
        val client = DeviceGatewayClient(
            policy = RegistryClient.DefaultPolicy(serverInfo),
            messageConsumer = mock(),
            transportObserver = mock(),
            authDelegate = mock(),
            callOptions = null,
            channelOptions = null,
            isStartReceiveServerInitiatedDirective = { false }
        )
        Assert.assertFalse(client.isConnected())
        client.connect()
        Assert.assertTrue(client.isConnected())
        client.disconnect()
        Assert.assertFalse(client.isConnected())
        client.connect()
        Assert.assertTrue(client.isConnected())
        client.disconnect()
        Assert.assertFalse(client.isConnected())
        client.startDirectivesService()
        client.onPingRequestAcknowledged()
        Assert.assertTrue(client.isConnected())
        client.stopDirectivesService()
        Assert.assertTrue(client.isConnected())
    }

    @Test
    fun testSend() {
        val client = DeviceGatewayClient(
            policy = RegistryClient.DefaultPolicy(serverInfo),
            messageConsumer = mock(),
            transportObserver = mock(),
            authDelegate = mock(),
            callOptions = null,
            channelOptions = null,
            isStartReceiveServerInitiatedDirective = { false }
        )
        client.connect()
        val request: MessageRequest = EventMessageRequest.Builder(
            "context",
            "namespace",
            "name", "version"
        ).build()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(mockTransport, request, null, listener)
        Assert.assertTrue(client.send(call))
    }

    @Test
    fun testGetHeaders() {
        val client = DeviceGatewayClient(
            policy = RegistryClient.DefaultPolicy(serverInfo),
            messageConsumer = mock(),
            transportObserver = mock(),
            authDelegate = mock(),
            callOptions = null,
            channelOptions = null,
            isStartReceiveServerInitiatedDirective = { false }
        )
        client.connect()
        val request: MessageRequest = EventMessageRequest.Builder(
            "context",
            "namespace",
            "name", "version"
        ).build()
        val listener: MessageSender.OnSendMessageListener = mock()
        val headers = hashMapOf("Last-Asr-Event-Time" to "123")
        val call = Grpc2Call(mockTransport, request, headers, listener)
        Assert.assertTrue(client.send(call))
        Assert.assertEquals(client.getHeaders(), headers)
        client.shutdown()
        Assert.assertNotEquals(client.getHeaders(), headers)
    }

    @Test
    fun testTransportObserver() {
        val deviceGatewayObserver = object : DeviceGatewayTransport.TransportObserver {
            override fun onConnected() {
                Assert.assertTrue("connect", true)
            }

            override fun onError(reason: ConnectionStatusListener.ChangedReason) {
                Assert.assertFalse(true)
            }

            override fun onReconnecting(reason: ConnectionStatusListener.ChangedReason) {
                Assert.assertTrue("reconnecting", true)
            }
        }
        val client: DeviceGatewayTransport = DeviceGatewayClient(
            policy = RegistryClient.DefaultPolicy(serverInfo),
            messageConsumer = mock(),
            transportObserver = deviceGatewayObserver,
            authDelegate = mock(),
            callOptions = null,
            channelOptions = null,
            isStartReceiveServerInitiatedDirective = { false }
        )
        client.connect()
        client.startDirectivesService()
    }

    @Test
    fun testDeviceGatewayTransport() {
        val mock : DeviceGatewayTransport = mock()
        verify(mock, never()).connect()
        verify(mock, never()).startDirectivesService()
    }
}