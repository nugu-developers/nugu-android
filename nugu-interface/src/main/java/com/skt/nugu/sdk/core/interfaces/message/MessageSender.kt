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

/**
 * An interface which send [MessageRequest]
 */
interface MessageSender {
    /**
     * The listener to be notified when occur event
     */
    interface OnSendMessageListener {
        /**
         * Called when post send message
         * @param messageRequest the messageRequest to be sent
         * @param result the success or failure to send the [messageRequest]
         */
        fun onPostSendMessage(messageRequest: MessageRequest, result: Boolean)
    }

    /**
     * Send message
     * @param messageRequest the messageRequest to be sent
     */
    fun sendMessage(messageRequest: MessageRequest): Boolean


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