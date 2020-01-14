/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.microphone.Microphone
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.concurrent.Executors

class DefaultMicrophoneAgent(
    messageSender: MessageSender,
    contextManager: ContextManagerInterface,
    defaultMicrophone: Microphone?
) : AbstractMicrophoneAgent(messageSender, contextManager, defaultMicrophone) {
    internal data class SetMicPayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("status")
        val status: String
    )

    companion object {
        private const val TAG = "MicrophoneAgent"

        const val NAME_SET_MIC = "SetMic"
        private const val NAME_SET_MIC_SUCCEEDED = "SetMicSucceeded"
        private const val NAME_SET_MIC_FAILED = "SetMicFailed"

        val SET_MIC = NamespaceAndName(
            NAMESPACE,
            NAME_SET_MIC
        )

        const val KEY_STATUS = "status"
    }

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)
    private val executor = Executors.newSingleThreadExecutor()

    init {
        defaultMicrophone?.addListener(this)
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun onSettingsChanged(settings: Microphone.Settings) {
        provideState(contextManager, namespaceAndName, 0)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getNamespaceAndName()) {
            SET_MIC -> handleSetMic(info)
        }
    }

    private fun handleSetMic(info: DirectiveInfo) {
        val directive = info.directive
        val payload = MessageFactory.create(directive.payload, SetMicPayload::class.java)
        if (payload == null) {
            Logger.e(TAG, "[handleSetMic] invalid payload: ${directive.payload}")
            setHandlingFailed(info, "[handleSetMic] invalid payload: ${directive.payload}")
            return
        }

        val status = payload.status.toUpperCase()

        if (status != "ON" && status != "OFF") {
            Logger.e(TAG, "[handleSetMic] invalid status: $status")
            setHandlingFailed(info, "[handleSetMic] invalid status: $status")
            return
        }

        setHandlingCompleted(info)
        executor.submit {
            if (executeHandleSetMic(status)) {
                sendSetMicSucceededEvent()
            } else {
                sendSetMicFailedEvent()
            }
        }
    }

    private fun sendSetMicSucceededEvent() {
        sendEvent(NAME_SET_MIC_SUCCEEDED)
    }

    private fun sendSetMicFailedEvent() {
        sendEvent(NAME_SET_MIC_FAILED)
    }

    private fun sendEvent(eventName: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        eventName,
                        VERSION
                    ).build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {

            }
        })
    }

    private fun executeHandleSetMic(status: String): Boolean {
        return when (status) {
            "ON" -> defaultMicrophone?.on() ?: false
            "OFF" -> defaultMicrophone?.off() ?: false
            else -> false
        }
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
        removeDirective(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[SET_MIC] = nonBlockingPolicy

        return configuration
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState]")
        val micStatus = if (defaultMicrophone == null) {
            "OFF"
        } else {
            if (defaultMicrophone.getSettings().onOff) {
                "ON"
            } else {
                "OFF"
            }
        }

        contextSetter.setState(namespaceAndName, JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("micStatus", micStatus)
        }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }
}