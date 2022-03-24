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
import java.util.HashMap
import java.util.concurrent.ExecutorService

internal abstract class ActionDirectiveHandler(
    private val contextGetter: ContextGetterInterface,
    private val messageSender: MessageSender,
    private val namespaceAndName: NamespaceAndName,
    private val executor: ExecutorService,
): AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ExtensionAgent::ActionDirectiveHandler"
        private const val NAME_ACTION = "Action"
        private const val NAME_ACTION_SUCCEEDED = "ActionSucceeded"
        private const val NAME_ACTION_FAILED = "ActionFailed"

        private val ACTION = NamespaceAndName(
            ExtensionAgent.NAMESPACE,
            NAME_ACTION
        )
    }

    internal data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("data")
        val data: JsonObject
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, Payload::class.java)
        if (payload == null) {
            Logger.d(TAG, "[handleDirective] invalid payload: ${info.directive.payload}")
            setHandlingFailed(
                info,
                "[handleDirective] invalid payload: ${info.directive.payload}"
            )
            return
        }

        val data = payload.data
        val playServiceId = payload.playServiceId

        executor.submit {
            val currentClient = getClient()
            if (currentClient != null) {
                val referrerDialogRequestId = info.directive.header.dialogRequestId
                if (currentClient.action(data.toString(), playServiceId, info.directive.header.dialogRequestId)) {
                    sendActionEvent(NAME_ACTION_SUCCEEDED, playServiceId, referrerDialogRequestId)
                } else {
                    sendActionEvent(NAME_ACTION_FAILED, playServiceId, referrerDialogRequestId)
                }
            } else {
                Logger.w(
                    TAG,
                    "[handleDirective] no current client. set client using setClient()."
                )
            }
        }
        setHandlingCompleted(info)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // no-op
    }

    private fun sendActionEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        Logger.d(TAG, "[sendEvent] name: $name, playServiceId: $playServiceId")
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext,
                    ExtensionAgent.NAMESPACE, name, ExtensionAgent.VERSION.toString())
                    .payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                    }.toString())
                    .referrerDialogRequestId(referrerDialogRequestId)
                    .build()

                messageSender.newCall(
                    request
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> by lazy {
        HashMap<NamespaceAndName, BlockingPolicy>().apply {
            this[ACTION] = BlockingPolicy.sharedInstanceFactory.get()
        }
    }

    abstract fun getClient(): ExtensionAgentInterface.Client?
}