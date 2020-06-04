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

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration

class ElementSelectedEventHandler(
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender
) {
    companion object {
        private const val EVENT_NAME_ELEMENT_SELECTED = "ElementSelected"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"
    }

    fun setElementSelected(playServiceId: String, token: String, callback: DisplayInterface.OnElementSelectedCallback?): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.sendMessage(
                        EventMessageRequest.Builder(
                            jsonContext,
                            DefaultDisplayAgent.NAMESPACE,
                            EVENT_NAME_ELEMENT_SELECTED,
                            DefaultDisplayAgent.VERSION.toString()
                        ).dialogRequestId(dialogRequestId).payload(
                            JsonObject().apply {
                                addProperty(KEY_TOKEN, token)
                                addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                            }.toString()
                        ).build()
                    , object : MessageSender.OnRequestCallback {
                        override fun onSuccess() {
                            callback?.onSuccess(dialogRequestId)
                        }

                        override fun onFailure(status: Status) {
                            callback?.onError(dialogRequestId, when (status.error) {
                                Status.StatusError.TIMEOUT -> DisplayInterface.ErrorType.RESPONSE_TIMEOUT
                                else -> DisplayInterface.ErrorType.REQUEST_FAIL
                            })
                        }
                    })
            }
        })

        return dialogRequestId
    }

}