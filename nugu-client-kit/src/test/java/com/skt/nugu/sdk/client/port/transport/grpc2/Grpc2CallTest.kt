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

import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Preferences
import junit.framework.TestCase
import org.junit.Assert

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock

class Grpc2CallTest : TestCase() {
    val transport: Transport = object : Transport {
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
    fun testEnqueueReturnTrue() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        Assert.assertTrue(call.enqueue(mock()))
    }

    @Test
    fun testEnqueueOnResponseStart() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.noAck()
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Assert.assertTrue(status.toString(), status.isOk())
            }

            override fun onSuccess(request: MessageRequest) {
            }

            override fun onResponseStart(request: MessageRequest) {
                Assert.assertTrue(originRequest == request)
            }
        })
    }

    @Test
    fun testEnqueueOnSuccess() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.noAck()
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Assert.assertTrue(status.toString(), status.isOk())
            }

            override fun onSuccess(request: MessageRequest) {
                Assert.assertTrue(originRequest == request)
            }

            override fun onResponseStart(request: MessageRequest) {
            }
        })
    }

    @Test
    fun testEnqueueAlreadyExecuted() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.noAck()
        call.enqueue(mock())
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Assert.assertTrue(status.toString(), status.code == Status.Code.FAILED_PRECONDITION)
            }

            override fun onSuccess(request: MessageRequest) {
                Assert.assertFalse(originRequest == request)
            }

            override fun onResponseStart(request: MessageRequest) {
            }
        })
    }

    @Test
    fun testEnqueueCancel() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Assert.assertTrue(status.toString(), status.code == Status.Code.CANCELLED)
            }

            override fun onSuccess(request: MessageRequest) {
                Assert.assertTrue(originRequest == request)
            }

            override fun onResponseStart(request: MessageRequest) {
            }
        })
        call.cancel()
        Assert.assertTrue(call.isCanceled())
    }

    @Test
    fun testEnqueueDeadlineExceeded() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.callTimeout(1)
        Assert.assertEquals(1, call.callTimeout())
        Assert.assertEquals(Status.DEADLINE_EXCEEDED, call.execute())
    }

    @Test
    fun testEnqueueOnPostSendMessage() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener =
            object : MessageSender.OnSendMessageListener {
                override fun onPreSendMessage(request: MessageRequest) {
                    Assert.assertTrue(originRequest == request)
                }

                override fun onPostSendMessage(request: MessageRequest, status: Status) {
                    Assert.assertTrue(originRequest == request)
                }
            }
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.callTimeout(1)
        Assert.assertEquals(1, call.callTimeout())
        call.execute()
    }

    @Test
    fun testGetRequest() {
        val call = Grpc2Call(transport, mock(), null, mock())
        Assert.assertNotNull(call.request())
    }

    @Test
    fun testGetHeaders() {
        val call = Grpc2Call(transport, mock(), hashMapOf("Last-Asr-Event-Time" to "123"), mock())
        Assert.assertNotNull(call.headers())
    }

    @Test
    fun testEnqueueOnStart() {
        val call = Grpc2Call(transport, mock(), null, mock())
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
            }

            override fun onSuccess(request: MessageRequest) {
            }

            override fun onResponseStart(request: MessageRequest) {
                Assert.assertNotNull(request)
            }
        })
        call.onStart()
    }

    @Test
    fun testEnqueueOnComplete() {
        val call = Grpc2Call(transport, mock(), null, mock())
        call.enqueue(mock())
        call.onComplete(Status.OK)
        Assert.assertTrue(call.isCompleted())
    }
}