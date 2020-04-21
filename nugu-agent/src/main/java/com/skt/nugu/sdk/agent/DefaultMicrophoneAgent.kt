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
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.agent.microphone.Microphone
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.concurrent.Executors

class DefaultMicrophoneAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val defaultMicrophone: Microphone?
) : AbstractCapabilityAgent(NAMESPACE), Microphone.OnSettingChangeListener {
    internal data class SetMicPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("status")
        val status: String
    )

    companion object {
        private const val TAG = "MicrophoneAgent"

        const val NAMESPACE = "Mic"
        private val VERSION = Version(1,0)

        const val NAME_SET_MIC = "SetMic"
        private const val NAME_SET_MIC_SUCCEEDED = "SetMicSucceeded"
        private const val NAME_SET_MIC_FAILED = "SetMicFailed"

        val SET_MIC = NamespaceAndName(
            NAMESPACE,
            NAME_SET_MIC
        )

        const val KEY_STATUS = "status"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var lastUpdatedMicSettings: Microphone.Settings? = null


    init {
        defaultMicrophone?.addListener(this)
        contextManager.setStateProvider(namespaceAndName, this, buildCompactContext().toString())
        contextManager.setState(namespaceAndName, buildContext(defaultMicrophone?.getSettings()), StateRefreshPolicy.ALWAYS, 0)
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
            val referrerDialogRequestId = info.directive.header.dialogRequestId

            if (executeHandleSetMic(status)) {
                sendEvent(
                    NAME_SET_MIC_SUCCEEDED,
                    payload.playServiceId,
                    payload.status,
                    referrerDialogRequestId
                )
            } else {
                sendEvent(
                    NAME_SET_MIC_FAILED,
                    payload.playServiceId,
                    payload.status,
                    referrerDialogRequestId
                )
            }
        }
    }

    private fun sendEvent(
        eventName: String,
        playServiceId: String,
        micStatus: String,
        referrerDialogRequestId: String
    ) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        eventName,
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                        addProperty("micStatus", micStatus)
                    }.toString())
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
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
        executor.submit {
            Logger.d(TAG, "[provideState]")
            val micSettings = defaultMicrophone?.getSettings()

            val context = if(lastUpdatedMicSettings == micSettings || micSettings == null) {
                null
            } else {
                buildContext(micSettings)
            }

            contextSetter.setState(namespaceAndName, context, StateRefreshPolicy.ALWAYS, stateRequestToken)
        }
    }

    private fun buildCompactContext() = JsonObject().apply {
        addProperty("version", VERSION.toString())
    }

    private fun buildContext(micSettings: Microphone.Settings?) = buildCompactContext().apply {
        addProperty("micStatus", if(micSettings?.onOff == true) "ON" else "OFF")
    }.toString()

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }
}