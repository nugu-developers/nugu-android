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

package com.skt.nugu.sdk.agent.ext.phonecall.handler

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.phonecall.PhoneCallAgent
import com.skt.nugu.sdk.agent.ext.phonecall.payload.MakeCallPayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class MakeCallDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_MAKE_CALL = "MakeCall"

        private const val NAME_FAILED = "Failed"

        private val MAKE_CALL = NamespaceAndName(PhoneCallAgent.NAMESPACE, NAME_MAKE_CALL)
    }

    interface Callback {
        fun onSuccess()
        fun onFailure(errorCode: ErrorCode)
    }

    enum class ErrorCode {
        NO_SYSTEM_PERMISSION,
        CALL_TYPE_NOT_SUPPORTED
    }

    interface Controller {
        fun makeCall(payload: MakeCallPayload, callback: Callback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())

        val payload =
            MessageFactory.create(info.directive.payload, MakeCallPayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.makeCall(payload, object : Callback {
                override fun onSuccess() {
                    // no-op
                }

                override fun onFailure(errorCode: ErrorCode) {
                    contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.sendMessage(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    PhoneCallAgent.NAMESPACE,
                                    "$NAME_MAKE_CALL$NAME_FAILED",
                                    PhoneCallAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("errorCode", errorCode.name)
                                    addProperty("callType", payload.callType.name)
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            )
                        }
                    }, namespaceAndName)
                }
            })
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configurations = HashMap<NamespaceAndName, BlockingPolicy>()

        configurations[MAKE_CALL] = BlockingPolicy()

        return configurations
    }
}