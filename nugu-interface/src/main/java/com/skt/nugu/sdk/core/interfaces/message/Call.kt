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
package com.skt.nugu.sdk.core.interfaces.message

interface Call {
    fun request(): MessageRequest
    fun headers(): Map<String, String>?
    fun isCanceled(): Boolean
    fun cancel()
    fun execute(): Status
    fun enqueue(callback: MessageSender.Callback?): Boolean
    fun noAck(): Call
    fun onStart()
    fun onComplete(status: Status)
    fun isCompleted(): Boolean
    fun callTimeout(millis: Long): Call
    fun callTimeout(): Long
    fun reschedule()
}