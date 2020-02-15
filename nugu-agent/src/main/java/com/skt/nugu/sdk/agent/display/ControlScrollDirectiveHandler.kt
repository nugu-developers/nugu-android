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
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.util.getValidReferrerDialogRequestId
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class ControlScrollDirectiveHandler(
    private val controller: Controller,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ControlScrollDirectiveHandler"
        private const val NAME_CONTROL_SCROLL = "ControlScroll"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val CONTROL_SCROLL = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
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
        val direction: Direction
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

        val referrerDialogRequestId = info.directive.header.getValidReferrerDialogRequestId()
        if (controller.controlScroll(payload.playServiceId, payload.direction)) {
            sendControlScrollEvent(
                info.directive.payload,
                "${NAME_CONTROL_SCROLL}${NAME_SUCCEEDED}",
                referrerDialogRequestId
            )
        } else {
            sendControlScrollEvent(
                info.directive.payload,
                "${NAME_CONTROL_SCROLL}${NAME_FAILED}",
                referrerDialogRequestId
            )
        }
        setHandlingCompleted(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    private fun sendControlScrollEvent(
        payload: String,
        name: String,
        referrerDialogRequestId: String
    ) {
        contextGetter.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(
                        jsonContext,
                        namespaceAndName.namespace,
                        name,
                        DefaultDisplayAgent.VERSION
                    ).payload(payload)
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[CONTROL_SCROLL] = nonBlockingPolicy

        return configuration
    }
}