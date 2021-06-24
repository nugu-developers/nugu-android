package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import junit.framework.TestCase
import org.junit.Assert

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock

class Grpc2CallTest : TestCase() {
    private val transport: Transport = object : Transport {
        override fun connect() = true
        override fun disconnect() = Unit
        override fun isConnected() = true
        override fun isConnectedOrConnecting() = true
        override fun isConnectSilently(): Boolean = false
        override fun setConnectSilently(connectSilently: Boolean) = Unit
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
    fun test_enqueue_return_true() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        Assert.assertTrue(call.enqueue(mock()))
    }

    @Test
    fun test_enqueue_onResponseStart() {
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
    fun test_enqueue_onSuccess() {
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
    fun test_enqueue_already_executed() {
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
    fun test_enqueue_cancel() {
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
    fun test_enqueue_deadline_exceeded() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener = mock()
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.callTimeout(1)
        Assert.assertEquals(Status.DEADLINE_EXCEEDED, call.execute())
    }

    @Test
    fun test_enqueue_onPostSendMessage() {
        val originRequest: MessageRequest = mock()
        val listener: MessageSender.OnSendMessageListener =
            object : MessageSender.OnSendMessageListener {
                override fun onPreSendMessage(request: MessageRequest) {
                    Assert.assertTrue(originRequest == request)
                }

                override fun onPostSendMessage(request: MessageRequest, status: Status) {
                }
            }
        val call = Grpc2Call(transport, originRequest, null, listener)
        call.callTimeout(1)
        call.execute()
    }
}