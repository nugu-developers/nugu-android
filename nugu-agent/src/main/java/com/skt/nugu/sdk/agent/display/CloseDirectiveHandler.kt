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
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class CloseDirectiveHandler(
    private val controller: Controller,
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender
) : AbstractDirectiveHandler() {
    companion object {
        // supported at v1.1
        private const val TAG = "CloseDirectiveHandler"
        private const val NAME_CLOSE = "Close"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val CLOSE = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_CLOSE
        )
    }

    private data class ClosePayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    interface Controller {
        fun close(playServiceId: String, listener: OnCloseListener)

        interface OnCloseListener {
            fun onSuccess()
            fun onFailure()
        }
    }

    private val displayNamespaceAndName = NamespaceAndName("supportedInterfaces", DisplayAgent.NAMESPACE)

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val closePayload =
            MessageFactory.create(info.directive.payload, ClosePayload::class.java)

        if (closePayload == null) {
            Logger.w(TAG, "[executeHandleCloseDirective] (Close) invalid payload.")
            sendCloseFailed(info, "")
            setHandlingCompleted(info)
            return
        }

        controller.close(closePayload.playServiceId, object : Controller.OnCloseListener {
            override fun onSuccess() {
                sendCloseSucceeded(info, closePayload.playServiceId)
                setHandlingCompleted(info)
            }

            override fun onFailure() {
                sendCloseFailed(info, closePayload.playServiceId)
                setHandlingCompleted(info)
            }
        })
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[CLOSE] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }

    private fun sendCloseEvent(eventName: String, info: DirectiveInfo, playServiceId: String) {
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        DisplayAgent.NAMESPACE,
                        eventName,
                        DisplayAgent.VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                    }.toString())
                        .referrerDialogRequestId(info.directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, displayNamespaceAndName)
    }

    private fun sendCloseSucceeded(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent("$NAME_CLOSE$NAME_SUCCEEDED", info, playServiceId)
    }

    private fun sendCloseFailed(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent("$NAME_CLOSE$NAME_FAILED", info, playServiceId)
    }
}