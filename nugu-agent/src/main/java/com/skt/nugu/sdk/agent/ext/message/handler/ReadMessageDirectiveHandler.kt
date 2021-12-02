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
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.payload.ReadMessageDirective
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class ReadMessageDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface,
    private val namespaceAndName: NamespaceAndName
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ReadMessageDirectiveHandler"

        const val NAME_READ_MESSAGE = "ReadMessage"

        private const val NAME_FINISHED = "Finished"

        private val READ_MESSAGE = NamespaceAndName(MessageAgent.NAMESPACE, NAME_READ_MESSAGE)
    }

    interface Callback {
        fun onError(description: String)
        fun onStop(cancelAssociation: Boolean)
        fun onFinish()
    }

    interface Controller {
        fun prepare(directive: ReadMessageDirective, reader: Attachment.Reader, callback: Callback)
        fun start(messageId: String)
        fun cancel(messageId: String)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, ReadMessageDirective.Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload")
            return
        }

        val reader = info.directive.getAttachmentReader()
        if(reader == null) {
            info.result.setFailed("Cannot create reader")
            return
        }

        controller.prepare(ReadMessageDirective(info.directive.header, payload), reader, object: Callback {
            override fun onError(description: String) {
                Logger.d(TAG, "[onError] description: $description")
                info.result.setFailed(description)
            }

            override fun onStop(cancelAssociation: Boolean) {
                Logger.d(TAG, "[onStop] cancelAssociation: $cancelAssociation")
                info.result.setFailed("playback stopped", if(cancelAssociation){
                    DirectiveHandlerResult.POLICY_CANCEL_ALL
                } else{
                    DirectiveHandlerResult.POLICY_CANCEL_NONE
                })
            }

            override fun onFinish() {
                Logger.d(TAG, "[onFinish]")
                info.result.setCompleted()
                contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                    override fun onContext(jsonContext: String) {
                        messageSender.newCall(
                            EventMessageRequest.Builder(
                                jsonContext,
                                MessageAgent.NAMESPACE,
                                "${NAME_READ_MESSAGE}${NAME_FINISHED}",
                                MessageAgent.VERSION.toString()
                            ).payload(JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("token", payload.token)
                            }.toString())
                                .referrerDialogRequestId(info.directive.getDialogRequestId())
                                .build()
                        ).enqueue(null)
                    }
                }, namespaceAndName)
            }
        })
    }

    override fun handleDirective(info: DirectiveInfo) {
        controller.start(info.directive.getMessageId())
    }

    override fun cancelDirective(info: DirectiveInfo) {
        controller.cancel(info.directive.getMessageId())
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[READ_MESSAGE] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }
}