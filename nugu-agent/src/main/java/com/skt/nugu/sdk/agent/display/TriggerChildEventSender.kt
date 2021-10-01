/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration

class TriggerChildEventSender (
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender
) {
    companion object {
        private const val EVENT_NAME_TRIGGER_CHILD = "TriggerChild"
    }

    fun triggerChild(playServiceId: String,
                     parentToken: String,
                     data: JsonObject,
                     referrerDialogRequestId: String?,
                     callback: DisplayAgentInterface.OnTriggerChildCallback?): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        DisplayAgent.NAMESPACE,
                        EVENT_NAME_TRIGGER_CHILD,
                        DisplayAgent.VERSION.toString()
                    ).dialogRequestId(dialogRequestId).payload(
                        JsonObject().apply {
                            addProperty("playServiceId", playServiceId)
                            addProperty("parentToken", parentToken)
                            add("data", data)
                        }.toString()
                    ).also { builder->
                        referrerDialogRequestId?.let {
                            builder.referrerDialogRequestId(referrerDialogRequestId)
                        }
                    }.build()
                ).enqueue( object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                        if(status.isTimeout()) {
                            callback?.onError(
                                dialogRequestId,
                                DisplayInterface.ErrorType.RESPONSE_TIMEOUT
                            )
                        } else {
                            callback?.onError(
                                dialogRequestId,
                                DisplayInterface.ErrorType.REQUEST_FAIL
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
        })

        return dialogRequestId
    }
}