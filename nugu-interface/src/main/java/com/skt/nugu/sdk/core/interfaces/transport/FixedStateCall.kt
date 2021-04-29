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
package com.skt.nugu.sdk.core.interfaces.transport

import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status

/**
 * This class returns a predefined status
 */
class FixedStateCall(
    val status: Status,
    val request: MessageRequest,
    listener: MessageSender.OnSendMessageListener
) : Call {
    private var listener: MessageSender.OnSendMessageListener? = listener
    private var isOnPreSendMessageCalled = false
    override fun request() = request
    override fun headers(): Map<String, String>? {
        throw NotImplementedError()
    }
    override fun isCanceled() = false

    override fun cancel() {
        // no op
    }

    override fun execute(): Status {
        return status
    }

    override fun enqueue(callback: MessageSender.Callback?): Boolean {
        isOnPreSendMessageCalled = true
        listener?.onPreSendMessage(request())
        callback?.onFailure(request(), status)
        onComplete(status)
        return false
    }

    override fun noAck(): Call {
        // no op
        return this
    }

    override fun onStart() {
    }

    override fun onComplete(status: Status) {
        if(!isOnPreSendMessageCalled) {
            isOnPreSendMessageCalled = true
            listener?.onPreSendMessage(request())
        }
        listener?.onPostSendMessage(request(), status)
        listener = null
    }
    override fun isCompleted() = false

    override fun callTimeout(millis: Long): Call {
        // no op
        return this
    }
    override fun callTimeout() = 0L

    override fun reschedule() {
    }
}