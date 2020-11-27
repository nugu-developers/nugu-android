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
import com.skt.nugu.sdk.agent.ext.mediaplayer.event.EventCallback
import com.skt.nugu.sdk.agent.ext.mediaplayer.MediaPlayerAgent
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.Payload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class ResumeDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
): AbstractDirectiveHandler() {
    companion object {
        private const val NAME_RESUME = "Resume"
        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val RESUME = NamespaceAndName(MediaPlayerAgent.NAMESPACE, NAME_RESUME)
    }

    interface Controller {
        fun resume(header: Header, payload: Payload, callback: EventCallback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.resume(info.directive.header, payload, object:
                EventCallback {
                override fun onSuccess(message: String?) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_RESUME}${NAME_SUCCEEDED}",
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
                            ).enqueue( object : MessageSender.Callback {
                                override fun onFailure(request: MessageRequest, status: Status) {
                                }

                                override fun onSuccess(request: MessageRequest) {
                                }
                                override fun onResponseStart(request: MessageRequest) {
                                }
                            })
                        }
                    })
                }

                override fun onFailure(errorCode: String) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_RESUME}${NAME_FAILED}",
                                    MediaPlayerAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    addProperty("errorCode", errorCode)
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue( object : MessageSender.Callback {
                                override fun onFailure(request: MessageRequest, status: Status) {
                                }

                                override fun onSuccess(request: MessageRequest) {
                                }
                                override fun onResponseStart(request: MessageRequest) {
                                }
                            })
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

        configuration[RESUME] = BlockingPolicy(BlockingPolicy.MEDIUM_AUDIO)

        return configuration
    }
}