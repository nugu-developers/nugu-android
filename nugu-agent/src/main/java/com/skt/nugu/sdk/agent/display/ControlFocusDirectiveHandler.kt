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
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class ControlFocusDirectiveHandler(
    private val controller: Controller,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ControlFocusDirectiveHandler"
        private const val NAME_CONTROL_FOCUS = "ControlFocus"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val CONTROL_FOCUS = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CONTROL_FOCUS
        )
    }

    interface Controller {
        fun controlFocus(playServiceId: String, direction: Direction): Boolean
    }

    private data class ControlFocusPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("direction")
        val direction: Direction
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, ControlFocusPayload::class.java)
        if (payload == null) {
            Logger.w(TAG, "[handleDirective] controlFocus - invalid payload")
            setHandlingFailed(info, "[handleDirective] controlFocus - invalid payload")
            return
        }

        val referrerDialogRequestId = info.directive.header.dialogRequestId
        if (controller.controlFocus(payload.playServiceId, payload.direction)) {
            sendControlFocusEvent(
                info.directive.payload,
                "$NAME_CONTROL_FOCUS$NAME_SUCCEEDED",
                referrerDialogRequestId
            )
        } else {
            sendControlFocusEvent(
                info.directive.payload,
                "$NAME_CONTROL_FOCUS$NAME_FAILED",
                referrerDialogRequestId
            )
        }
        setHandlingCompleted(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    private fun sendControlFocusEvent(
        payload: String,
        name: String,
        referrerDialogRequestId: String
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
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[CONTROL_FOCUS] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }
}