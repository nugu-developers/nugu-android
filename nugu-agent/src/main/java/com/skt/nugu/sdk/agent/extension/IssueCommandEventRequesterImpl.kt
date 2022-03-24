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

package com.skt.nugu.sdk.agent.extension

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration

internal class IssueCommandEventRequesterImpl(
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val namespaceAndName: NamespaceAndName
): IssueCommandEventRequester {
    companion object {
        private const val TAG = "ExtensionAgent::IssueCommandEventRequester"

        private const val NAME_COMMAND_ISSUED = "CommandIssued"

        const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"
        const val PAYLOAD_DATA = "data"
    }

    override fun issueCommand(playServiceId: String,
                              data: String,
                              callback: ExtensionAgentInterface.OnCommandIssuedCallback?): String {
        Logger.d(TAG, "[issueCommand] playServiceId: $playServiceId, data: $data, callback: $callback")
        val jsonData = try {
            JsonParser.parseString(data).asJsonObject
        } catch(th: Throwable) {
            throw IllegalArgumentException(th)
        }

        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext,
                    ExtensionAgent.NAMESPACE,
                    NAME_COMMAND_ISSUED, ExtensionAgent.VERSION.toString())
                    .dialogRequestId(dialogRequestId)
                    .payload(JsonObject().apply {
                        addProperty(PAYLOAD_PLAY_SERVICE_ID, playServiceId)
                        add(PAYLOAD_DATA, jsonData)
                    }.toString()).build()

                messageSender.newCall(
                    request
                ).enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {

                        if(status.isTimeout()) {
                            callback?.onError(
                                dialogRequestId,
                                ExtensionAgentInterface.ErrorType.RESPONSE_TIMEOUT
                            )
                        } else {
                            callback?.onError(
                                dialogRequestId,
                                ExtensionAgentInterface.ErrorType.REQUEST_FAIL
                            )
                        }
                    }
                    override fun onSuccess(request: MessageRequest) {
                    }
                    override fun onResponseStart(request: MessageRequest) {
                        callback?.onSuccess(dialogRequestId)
                    }
                })
            }
        }, namespaceAndName)

        return dialogRequestId
    }
}