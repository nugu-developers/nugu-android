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

import com.skt.nugu.sdk.client.port.transport.grpc2.Grpc2Call
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import org.junit.Assert.*

import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.Executors

class EventsServiceTest : TestCase() {
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

    @Test
    fun testEventsService() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val service = EventsService(
            mock(),
            mock(),
            scheduler,
            null
        )
        Assert.assertFalse(service.isShutdown.get())
        service.shutdown()
        Assert.assertTrue(service.isShutdown.get())
    }

    @Test
    fun testSendEventMessage() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val service = EventsService(
            mock(),
            mock(),
            scheduler,
            null
        )
        val listener: MessageSender.OnSendMessageListener = mock()
        val headers = hashMapOf("Last-Asr-Event-Time" to "123")
        val request: MessageRequest = EventMessageRequest.Builder(
            "context",
            "namespace",
            "name", "version"
        ).build()
        val call = Grpc2Call(mockTransport, request, headers, listener)
        val responseObserver = service.ClientCallStreamObserver("streamId", call, false)
        val future = service.scheduleTimeout("streamId", call)
        val clientChannel = EventsService.ClientChannel(
            mock(), future, responseObserver
        )
        service.requestStreamMap["streamId"] = clientChannel
        Assert.assertEquals(service.obtainChannel("streamId"), clientChannel)
        Assert.assertEquals(service.requestStreamMap.size, 1)
        service.cancelScheduledTimeout("streamId")
        future?.let { Assert.assertTrue(it.isCancelled) }
        service.halfClose("streamId")
        service.shutdown()
        Assert.assertEquals(service.requestStreamMap.size, 0)
    }
}