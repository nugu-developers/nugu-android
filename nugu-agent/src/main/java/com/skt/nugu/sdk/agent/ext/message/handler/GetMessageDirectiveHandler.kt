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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.Contact
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.payload.GetMessagePayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class GetMessageDirectiveHandler (
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_GET_MESSAGE = "GetMessage"
        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val GET_MESSAGE = NamespaceAndName(MessageAgent.NAMESPACE, NAME_GET_MESSAGE)
    }

    interface Callback {
        fun onSuccess(messages: List<Contact>?)
        fun onFailure(errorCode: String)
    }

    interface Controller {
        fun getMessageList(payload: GetMessagePayload, callback: Callback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())

        val payload = MessageFactory.create(info.directive.payload, GetMessagePayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.getMessageList(payload, object : Callback {
                override fun onSuccess(messages: List<Contact>?) {
                    contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.sendMessage(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MessageAgent.NAMESPACE,
                                    "${NAME_GET_MESSAGE}${NAME_SUCCEEDED}",
                                    MessageAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    messages?.let {
                                        add("candidates", JsonArray().apply {
                                            it.forEach {
                                                add(it.toJson())
                                            }
                                        })
                                    }
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            )
                        }
                    })
                }

                override fun onFailure(errorCode: String) {
                    contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.sendMessage(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MessageAgent.NAMESPACE,
                                    "${NAME_GET_MESSAGE}${NAME_FAILED}",
                                    MessageAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("errorCode", errorCode)
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            )
                        }
                    })
                }
            })
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[GET_MESSAGE] = BlockingPolicy()

        return configuration
    }
}