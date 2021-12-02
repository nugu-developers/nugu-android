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
import com.skt.nugu.sdk.agent.microphone.Microphone
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

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
        private val VERSION = Version(1, 0)

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

    internal data class StateContext(private val settings: Microphone.Settings?) :
        BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            addProperty("micStatus", if (settings?.onOff == true) "ON" else "OFF")
        }.toString()
    }

    init {
        defaultMicrophone?.addListener(this)
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun onSettingsChanged(settings: Microphone.Settings) {
        provideState(contextManager, namespaceAndName, ContextType.FULL, ContextSetterInterface.FORCE_SET_TOKEN)
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

        val status = payload.status.uppercase(Locale.getDefault())

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
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
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
                ).enqueue(null)
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
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[SET_MIC] = BlockingPolicy.sharedInstanceFactory.get()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        executor.submit {
            Logger.d(
                TAG,
                "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
            )
            contextSetter.setState(
                namespaceAndName,
                if (contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(
                    defaultMicrophone?.getSettings()
                ), StateRefreshPolicy.ALWAYS, contextType, stateRequestToken
            )
        }
    }
}