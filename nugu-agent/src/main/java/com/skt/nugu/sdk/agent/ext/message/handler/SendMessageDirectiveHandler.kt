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

package com.skt.nugu.sdk.agent.ext.message.handler

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.EventCallback
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.payload.SendMessagePayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class SendMessageDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_SEND_MESSAGE = "SendMessage"
        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val SEND_MESSAGE = NamespaceAndName(MessageAgent.NAMESPACE, NAME_SEND_MESSAGE)
    }

    interface Controller {
        fun sendMessage(payload: SendMessagePayload, callback: EventCallback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, SendMessagePayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.sendMessage(payload, object : EventCallback {
                override fun onSuccess() {
                    contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MessageAgent.NAMESPACE,
                                    "${NAME_SEND_MESSAGE}${NAME_SUCCEEDED}",
                                    MessageAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    add("recipient", payload.recipient.toJson())
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue(null)
                        }
                    })
                }

                override fun onFailure(errorCode: String) {
                    contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MessageAgent.NAMESPACE,
                                    "${NAME_SEND_MESSAGE}${NAME_FAILED}",
                                    MessageAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    add("recipient", payload.recipient.toJson())
                                    addProperty("errorCode", errorCode)
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue(null)
                        }
                    })
                }
            })
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[SEND_MESSAGE] = BlockingPolicy.sharedInstanceFactory.get()
    }
}