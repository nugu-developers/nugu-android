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
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration

class ElementSelectedEventHandler(
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val inputProcessorManager: InputProcessorManagerInterface
): InputProcessor {
    companion object {
        private const val EVENT_NAME_ELEMENT_SELECTED = "ElementSelected"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"
    }
    private val eventCallbacks = HashMap<String, DisplayInterface.OnElementSelectedCallback>()

    fun setElementSelected(playServiceId: String, token: String, callback: DisplayInterface.OnElementSelectedCallback?): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()

        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                if (messageSender.sendMessage(
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
                    )
                ) {
                    callback?.let {
                        eventCallbacks.put(dialogRequestId, callback)
                    }
                    onSendEventFinished(dialogRequestId)
                } else {
                    callback?.onError(dialogRequestId, DisplayInterface.ErrorType.REQUEST_FAIL)
                }
            }
        })

        return dialogRequestId
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        eventCallbacks.remove(dialogRequestId)?.onSuccess(dialogRequestId)
        return true
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        eventCallbacks.remove(dialogRequestId)
            ?.onError(dialogRequestId, DisplayInterface.ErrorType.RESPONSE_TIMEOUT)
    }
}