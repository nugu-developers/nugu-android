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
import com.skt.nugu.sdk.agent.screen.AbstractScreenAgent
import com.skt.nugu.sdk.agent.screen.Screen
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class DefaultScreenAgent(
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    screen: Screen
) : AbstractScreenAgent(contextManager, messageSender, screen) {
    companion object {
        private const val TAG = "ScreenAgent"
        private const val NAMESPACE = "Screen"
        private const val VERSION = "1,0"

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

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    private val executor = Executors.newSingleThreadExecutor()

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        executor.submit {
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
                        sendEvent("$NAME_TURN_ON$NAME_SUCCEEDED", payload.playServiceId)
                    } else {
                        sendEvent("$NAME_TURN_ON$NAME_FAILED", payload.playServiceId)
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
                        sendEvent("$NAME_TURN_OFF$NAME_SUCCEEDED", payload.playServiceId)
                    } else {
                        sendEvent("$NAME_TURN_OFF$NAME_FAILED", payload.playServiceId)
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
                        sendEvent("$NAME_SET_BRIGHTNESS$NAME_SUCCEEDED", payload.playServiceId)
                    } else {
                        sendEvent("$NAME_SET_BRIGHTNESS$NAME_FAILED", payload.playServiceId)
                    }
                }
            }

            setHandlingCompleted(info)
        }
    }

    private fun sendEvent(name: String, playServiceId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                val request = EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION)
                    .payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                    }.toString()).build()

                messageSender.sendMessage(request)
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
        removeDirective(info.directive.getMessageId())
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[TURN_ON] = nonBlockingPolicy
        configuration[TURN_OFF] = nonBlockingPolicy
        configuration[SET_BRIGHTNESS] = nonBlockingPolicy

        return configuration
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(namespaceAndName, JsonObject().apply {
            addProperty("version", VERSION)
            with(screen.getSettings()) {
                addProperty("state", isOn)
                addProperty("brightness", brightness)
            }
        }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
    }
}