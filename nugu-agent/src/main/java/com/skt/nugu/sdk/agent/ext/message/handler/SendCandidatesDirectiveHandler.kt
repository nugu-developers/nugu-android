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
import com.skt.nugu.sdk.agent.ext.message.Context
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.BaseContextState
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class SendCandidatesDirectiveHandler(
    private val controller: AgentController,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface,
    private val interactionControlManager: InteractionControlManagerInterface,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_SEND_CANDIDATES = "SendCandidates"
        private const val NAME_CANDIDATES_LISTED = "CandidatesListed"

        private val SEND_CANDIDATES = NamespaceAndName(MessageAgent.NAMESPACE, NAME_SEND_CANDIDATES)
    }

    interface Callback {
        fun onSuccess(context: Context)
        fun onFailure()
    }

    interface Controller {
        fun sendCandidates(payload: SendCandidatesPayload, callback: Callback)
    }

    interface AgentCallback {
        fun onSuccess(context: MessageAgent.StateContext)
        fun onFailure()
    }

    interface AgentController {
        fun sendCandidates(payload: SendCandidatesPayload, callback: AgentCallback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, SendCandidatesPayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()

            val interactionControl = if(payload.interactionControl != null) {
                object : InteractionControl {
                    override fun getMode(): InteractionControlMode = when(payload.interactionControl.mode) {
                        com.skt.nugu.sdk.agent.common.InteractionControl.Mode.MULTI_TURN -> InteractionControlMode.MULTI_TURN
                        com.skt.nugu.sdk.agent.common.InteractionControl.Mode.NONE -> InteractionControlMode.NONE
                    }
                }
            } else {
                null
            }

            interactionControl?.let {
                interactionControlManager.start(it)
            }

            controller.sendCandidates(payload, object : AgentCallback {
                override fun onSuccess(context: MessageAgent.StateContext) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            if(!messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MessageAgent.NAMESPACE,
                                    NAME_CANDIDATES_LISTED,
                                    MessageAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    payload.interactionControl?.let {
                                        add("interactionControl", it.toJsonObject())
                                    }
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue(object : MessageSender.Callback{
                                override fun onFailure(request: MessageRequest, status: Status) {
                                    interactionControl?.let {
                                        interactionControlManager.finish(it)
                                    }
                                }

                                override fun onSuccess(request: MessageRequest) {
                                }

                                override fun onResponseStart(request: MessageRequest) {
                                    interactionControl?.let {
                                        interactionControlManager.finish(it)
                                    }
                                }
                            })) {
                                interactionControl?.let {
                                    interactionControlManager.finish(it)
                                }
                            }
                        }
                    }, null, HashMap<NamespaceAndName, BaseContextState>().apply {
                        put(namespaceAndName, context)
                    })
                }

                override fun onFailure() {
                    // can't send without context
                    interactionControl?.let {
                        interactionControlManager.finish(it)
                    }
                }
            })
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[SEND_CANDIDATES] = BlockingPolicy.sharedInstanceFactory.get()
    }
}