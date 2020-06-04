package com.skt.nugu.sdk.client.port.transport.http2

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

class CanceledCall(val request: MessageRequest) : MessageSender.Call {
    private var canceled: Boolean = true
    override fun request() = request
    override fun isCanceled() = true
    override fun cancel() {}
}