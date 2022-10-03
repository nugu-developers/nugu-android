/**
 * Copyright (c) 2022 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.routine.handler

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class MoveDirectiveHandler(
    private val contextManager: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val controller: Controller,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_MOVE = "Move"

        val MOVE = NamespaceAndName(RoutineAgent.NAMESPACE, NAME_MOVE)
    }

    data class MoveDirective(
        val header: Header,
        val payload: Payload
    ) {
        data class Payload(
            @SerializedName("playServiceId")
            val playServiceId: String,
            @SerializedName("position")
            val position: Long
        )
    }

    interface Controller {
        fun move(directive: MoveDirective): Boolean
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, MoveDirective.Payload::class.java)

        if(payload == null) {
            info.result.setFailed("Invalid Payload.")
            return
        }

        val directive = MoveDirective(info.directive.header, payload)

        if (controller.move(directive)) {
            sendSuccessEvent(directive)
            info.result.setCompleted()
        } else {
            val errorMessage = "No executable action (Maybe only left uncountable action) or index out of bound."
            sendFailedEvent(directive, errorMessage)
            info.result.setFailed(errorMessage)
        }
    }

    private fun sendSuccessEvent(directive: MoveDirective) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(EventMessageRequest.Builder(
                    jsonContext,
                    RoutineAgent.NAMESPACE,
                    "MoveSucceeded",
                    RoutineAgent.VERSION.toString()
                ).payload(JsonObject().apply {
                    addProperty("playServiceId", directive.payload.playServiceId)
                }.toString()).referrerDialogRequestId(directive.header.dialogRequestId).build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun sendFailedEvent(directive: MoveDirective, errorMessage: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        RoutineAgent.NAMESPACE,
                        "MoveFailed",
                        RoutineAgent.VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                        addProperty("errorMessage", errorMessage)
                    }.toString())
                        .referrerDialogRequestId(directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // no-op
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[MOVE] = BlockingPolicy.sharedInstanceFactory.get()
    }
}