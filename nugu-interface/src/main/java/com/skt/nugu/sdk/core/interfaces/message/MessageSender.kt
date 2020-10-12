/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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

import java.io.IOException

/**
 * An interface which send [MessageRequest]
 */
interface MessageSender {
    /**
     * The listener to be notified when occur event
     */
    interface OnSendMessageListener {
        /**
         * Called when pre send message
         * @param request the messageRequest to be sent
         */
        fun onPreSendMessage(request: MessageRequest)
        /**
         * Called when post send message
         * @param request the messageRequest to be sent
         * @param status the success or failure to send the [request]
         */
        fun onPostSendMessage(request: MessageRequest, status: Status)
    }

    /**
     * Prepares the request to be executed at some point in the future.
     */
    fun newCall(request: MessageRequest, headers: Map<String, String>? = null): Call

    /**
     * The callback to the result of sending the message
     */
    interface Callback {
        fun onFailure(request: MessageRequest, status: Status)
        fun onSuccess(request: MessageRequest)
        fun onResponseStart(request: MessageRequest)
    }

    /**
     * Add listener to notified when occur events
     * @param listener the listener will be added
     */
    fun addOnSendMessageListener(listener: OnSendMessageListener)

    /**
     * Remove the listener
     * @param listener the listener will be removed
     */
    fun removeOnSendMessageListener(listener: OnSendMessageListener)
}