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

package com.skt.nugu.sdk.agent.ext.mediaplayer.handler

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.mediaplayer.EventCallback
import com.skt.nugu.sdk.agent.ext.mediaplayer.MediaPlayerAgent
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.MovePayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class MoveDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
): AbstractDirectiveHandler() {
    companion object {
        private const val NAME_MOVE = "Move"
        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val MOVE = NamespaceAndName(MediaPlayerAgent.NAMESPACE, NAME_MOVE)
    }

    interface Controller {
        fun move(payload: MovePayload, callback: EventCallback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, MovePayload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.move(payload, object: EventCallback {
                override fun onSuccess(message: String?) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.sendMessage(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_MOVE}${NAME_SUCCEEDED}",
                                    MediaPlayerAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    message?.let {
                                        addProperty("message", message)
                                    }
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            )
                        }
                    })
                }

                override fun onFailure(reason: String) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.sendMessage(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_MOVE}${NAME_FAILED}",
                                    MediaPlayerAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    addProperty("reason", reason)
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
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[MOVE] = BlockingPolicy()

        return configuration
    }
}