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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.screen.Screen
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class DefaultScreenAgent(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val screen: Screen
) : AbstractCapabilityAgent(NAMESPACE) {
    companion object {
        private const val TAG = "ScreenAgent"
        const val NAMESPACE = "Screen"
        private val VERSION = Version(1,0)

        private const val NAME_TURN_ON = "TurnOn"
        private const val NAME_TURN_OFF = "TurnOff"
        private const val NAME_SET_BRIGHTNESS = "SetBrightness"

        private val TURN_ON = NamespaceAndName(NAMESPACE, NAME_TURN_ON)
        private val TURN_OFF = NamespaceAndName(NAMESPACE, NAME_TURN_OFF)
        private val SET_BRIGHTNESS = NamespaceAndName(NAMESPACE, NAME_SET_BRIGHTNESS)

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"
    }

    data class TurnOnPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("brightness")
        val brightness: Long
    )

    data class TurnOffPayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    data class SetBrightnessPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("brightness")
        val brightness: Long
    )

    private val executor = Executors.newSingleThreadExecutor()

    private var lastUpdatedSettings: Screen.Settings? = null

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
            val referrerDialogRequestId = info.directive.header.dialogRequestId
            when (info.directive.getNamespaceAndName()) {
                TURN_ON -> {
                    val payload =
                        MessageFactory.create(info.directive.payload, TurnOnPayload::class.java)
                    if (payload == null) {
                        Logger.w(TAG, "[handleDirective] turnOn: invalid payload")
                        setHandlingFailed(info, "[handleDirective] turnOn: invalid payload")
                        return@submit
                    }

                    if (screen.turnOn(payload.brightness)) {
                        sendEvent("$NAME_TURN_ON$NAME_SUCCEEDED", payload.playServiceId, referrerDialogRequestId)
                    } else {
                        sendEvent("$NAME_TURN_ON$NAME_FAILED", payload.playServiceId, referrerDialogRequestId)
                    }
                }
                TURN_OFF -> {
                    val payload =
                        MessageFactory.create(info.directive.payload, TurnOffPayload::class.java)
                    if (payload == null) {
                        Logger.w(TAG, "[handleDirective] turnOff: invalid payload")
                        setHandlingFailed(info, "[handleDirective] turnOff: invalid payload")
                        return@submit
                    }
                    if (screen.turnOff()) {
                        sendEvent("$NAME_TURN_OFF$NAME_SUCCEEDED", payload.playServiceId, referrerDialogRequestId)
                    } else {
                        sendEvent("$NAME_TURN_OFF$NAME_FAILED", payload.playServiceId, referrerDialogRequestId)
                    }
                }
                SET_BRIGHTNESS -> {
                    val payload = MessageFactory.create(
                        info.directive.payload,
                        SetBrightnessPayload::class.java
                    )
                    if (payload == null) {
                        Logger.w(TAG, "[handleDirective] setBrightness: invalid payload")
                        setHandlingFailed(info, "[handleDirective] setBrightness: invalid payload")
                        return@submit
                    }
                    if (screen.setBrightness(payload.brightness)) {
                        sendEvent("$NAME_SET_BRIGHTNESS$NAME_SUCCEEDED", payload.playServiceId, referrerDialogRequestId)
                    } else {
                        sendEvent("$NAME_SET_BRIGHTNESS$NAME_FAILED", payload.playServiceId, referrerDialogRequestId)
                    }
                }
            }

            setHandlingCompleted(info)
        }
    }

    private fun sendEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
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

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val nonBlockingPolicy = BlockingPolicy.sharedInstanceFactory.get()
        this[TURN_ON] = nonBlockingPolicy
        this[TURN_OFF] = nonBlockingPolicy
        this[SET_BRIGHTNESS] = nonBlockingPolicy
    }

    internal data class StateContext(val settings: Screen.Settings): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            with(settings) {
                addProperty("state", if(isOn) "ON" else "OFF")
                addProperty("brightness", brightness)
            }
        }.toString()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        contextSetter.setState(
            namespaceAndName,
            if(contextType == ContextType.COMPACT) StateContext.CompactContextState else StateContext(screen.getSettings()),
            StateRefreshPolicy.ALWAYS,
            contextType,
            stateRequestToken
        )
    }
}