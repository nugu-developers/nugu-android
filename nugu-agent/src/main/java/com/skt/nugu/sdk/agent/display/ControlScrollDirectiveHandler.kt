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
package com.skt.nugu.sdk.agent.display

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.common.InteractionControl
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class ControlScrollDirectiveHandler(
    private val controller: Controller,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val namespaceAndName: NamespaceAndName,
    private val interactionControlManager: InteractionControlManagerInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ControlScrollDirectiveHandler"
        private const val NAME_CONTROL_SCROLL = "ControlScroll"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val CONTROL_SCROLL = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CONTROL_SCROLL
        )
    }

    interface Controller {
        fun controlScroll(playServiceId: String, direction: Direction): Boolean
    }

    private data class ControlScrollPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("direction")
        val direction: Direction,
        @SerializedName("interactionControl")
        val interactionControl: InteractionControl?
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, ControlScrollPayload::class.java)
        if (payload == null) {
            Logger.w(TAG, "[handleDirective] controlScroll - invalid payload")
            setHandlingFailed(info, "[handleDirective] controlScroll - invalid payload")
            return
        }

        val interactionControl = if(payload.interactionControl != null) {
            object : com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl {
                override fun getMode(): InteractionControlMode = when(payload.interactionControl.mode) {
                    InteractionControl.Mode.MULTI_TURN -> InteractionControlMode.MULTI_TURN
                    InteractionControl.Mode.NONE -> InteractionControlMode.NONE
                }
            }
        } else {
            null
        }

        interactionControl?.let {
            interactionControlManager.start(it)
        }

        val eventCallback = object: MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                interactionControl?.let {
                    interactionControlManager.finish(it)
                }
            }

            override fun onSuccess(request: MessageRequest) {
                interactionControl?.let {
                    interactionControlManager.finish(it)
                }
            }

            override fun onResponseStart(request: MessageRequest) {
            }

        }
        val referrerDialogRequestId = info.directive.header.dialogRequestId
        if (controller.controlScroll(payload.playServiceId, payload.direction)) {
            sendControlScrollEvent(
                info.directive.payload,
                "${NAME_CONTROL_SCROLL}${NAME_SUCCEEDED}",
                referrerDialogRequestId,
                eventCallback
            )
        } else {
            sendControlScrollEvent(
                info.directive.payload,
                "${NAME_CONTROL_SCROLL}${NAME_FAILED}",
                referrerDialogRequestId,
                eventCallback
            )
        }
        setHandlingCompleted(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun sendControlScrollEvent(
        payload: String,
        name: String,
        referrerDialogRequestId: String,
        callback: MessageSender.Callback?
    ) {
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        namespaceAndName.name,
                        name,
                        DisplayAgent.VERSION.toString()
                    ).payload(payload)
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                ).enqueue(callback)
            }
        }, namespaceAndName)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[CONTROL_SCROLL] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }
}