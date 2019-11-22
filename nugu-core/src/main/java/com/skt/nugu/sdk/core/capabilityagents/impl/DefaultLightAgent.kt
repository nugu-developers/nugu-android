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
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.light.AbstractLightAgent
import com.skt.nugu.sdk.core.interfaces.capability.light.LightAgentFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.light.Light
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.HashMap
import java.util.concurrent.Executors

object DefaultLightAgent {
    private const val TAG = "LightAgent"

    val FACTORY = object : LightAgentFactory {
        override fun create(
            messageSender: MessageSender,
            contextManager: ContextManagerInterface,
            light: Light
        ): AbstractLightAgent = Impl(
            messageSender,
            contextManager,
            light
        )
    }

    internal data class EmptyPayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    internal data class ChangeLightPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("brightness")
        val brightness: Long,
        @SerializedName("mode")
        val mode: String
    )

    internal data class FlickerPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("onTimeInMilliseconds")
        val onTimeInMilliseconds: Long,
        @SerializedName("offTimeInMilliseconds")
        val offTimeInMilliseconds: Long,
        @SerializedName("repeatCount")
        val repeatCount: Long,
        @SerializedName("mode")
        val mode: String
    )

    internal class Impl(
        messageSender: MessageSender,
        contextManager: ContextManagerInterface,
        light: Light
    ) : AbstractLightAgent(messageSender, contextManager, light) {
        companion object {
            const val NAME_TURN_ON_LIGHT = "TurnOnLight"
            const val NAME_TURN_OFF_LIGHT = "TurnOffLight"
            const val NAME_CHANGE_LIGHT = "ChangeLight"
            const val NAME_FLICKER = "Flicker"

            val TURN_ON_LIGHT = NamespaceAndName(
                NAMESPACE,
                NAME_TURN_ON_LIGHT
            )
            val TURN_OFF_LIGHT = NamespaceAndName(
                NAMESPACE,
                NAME_TURN_OFF_LIGHT
            )
            val CHANGE_LIGHT = NamespaceAndName(
                NAMESPACE,
                NAME_CHANGE_LIGHT
            )
            val FLICKER = NamespaceAndName(
                NAMESPACE,
                NAME_FLICKER
            )

            private const val KEY_STATE = "state"
            const val KEY_MODE = "mode"
            const val KEY_BRIGHTNESS = "brightness"
            const val KEY_MAX_BRIGHTNESS = "maxBrightness"
            const val KEY_MIN_BRIGHTNESS = "minBrightness"
            const val KEY_MAX_FLICKER_COUNT = "maxFlickerCount"

            const val KEY_ON_TIME_IN_MILLISECONDS = "onTimeInMilliseconds"
            const val KEY_OFF_TIME_IN_MILLISECONDS = "offTimeInMilliseconds"
            const val KEY_REPEAT_COUNT = "repeatCount"

            private const val NAME_TURN_ON_LIGHT_SUCCEEDED = "TurnOnLightSucceeded"
            private const val NAME_TURN_ON_LIGHT_FAILED = "TurnOnLightFailed"

            private const val NAME_TURN_OFF_LIGHT_SUCCEEDED = "TurnOffLightSucceeded"
            private const val NAME_TURN_OFF_LIGHT_FAILED = "TurnOffLightFailed"

            private const val NAME_CHANGE_LIGHT_SUCCEEDED = "ChangeLightSucceeded"
            private const val NAME_CHANGE_LIGHT_FAILED = "ChangeLightFailed"

            private const val NAME_FLICKER_SUCCEEDED = "FlickerSucceeded"
            private const val NAME_FLICKER_FAILED = "FlickerFailed"
        }

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)
        private val executor = Executors.newSingleThreadExecutor()

        init {
            contextManager.setStateProvider(namespaceAndName, this)
        }

        override fun preHandleDirective(info: DirectiveInfo) {
            // no-op
        }

        override fun handleDirective(info: DirectiveInfo) {
            executor.submit {
                when (info.directive.getName()) {
                    NAME_TURN_ON_LIGHT -> executeHandleChangeLight(info, true)
                    NAME_CHANGE_LIGHT -> executeHandleChangeLight(info, false)
                    NAME_TURN_OFF_LIGHT -> executeHandleTurnOffLight(info)
                    NAME_FLICKER -> executeHandleFlicker(info)
                }
            }
        }

        private fun executeHandleTurnOffLight(info: DirectiveInfo) {
            val payload = MessageFactory.create(info.directive.payload, EmptyPayload::class.java)
            if (payload == null) {
                Logger.w(
                    TAG,
                    "[executeHandleTurnOffLight] invalid payload: ${info.directive.payload}"
                )
                executeSetHandlingFailed(
                    info,
                    "[executeHandleTurnOffLight] invalid payload: ${info.directive.payload}"
                )
                return
            }

            if (light.turnOffLight()) {
                sendEvent(NAME_TURN_OFF_LIGHT_SUCCEEDED, payload.playServiceId)
                executeSetHandlingCompleted(info)
            } else {
                sendEvent(NAME_TURN_OFF_LIGHT_FAILED, payload.playServiceId)
                executeSetHandlingFailed(info, "executeTurnOffLight failed")
            }
        }

        private fun executeHandleChangeLight(
            info: DirectiveInfo,
            turnOn: Boolean
        ) {
            with(info.directive) {
                val minBrightness = light.getMinBrightness()
                val maxBrightness = light.getMaxBrightness()
                val payload = MessageFactory.create(payload, ChangeLightPayload::class.java)
                if (payload == null) {
                    Logger.w(
                        TAG,
                        "[executeHandleChangeLight] invalid payload: ${this.payload}"
                    )
                    executeSetHandlingFailed(
                        info,
                        "[executeHandleChangeLight] invalid payload: ${this.payload}"
                    )
                    return
                }

                val brightness = payload.brightness

                if (minBrightness != null && maxBrightness != null) {
                    if (brightness !in minBrightness..maxBrightness) {
                        Logger.w(
                            TAG,
                            "[executeHandleChangeLight] out of range brightness: $brightness"
                        )
                        executeSetHandlingFailed(
                            info,
                            "[executeHandleChangeLight] out of range brightness: $brightness"
                        )
                        return
                    }
                }

                val strMode = payload.mode

                val mode = Light.Mode.valueOf(strMode)
                if (mode == null) {
                    Logger.w(TAG, "[executeHandleChangeLight] wrong mode : $strMode")
                    executeSetHandlingFailed(
                        info,
                        "[executeHandleChangeLight] wrong mode : $strMode"
                    )
                    return
                }

                Logger.d(
                    TAG,
                    "[executeHandleChangeLight] mode: $mode, brightness: $brightness, turnOn: $turnOn"
                )

                val result = if (turnOn) {
                    light.turnOnLight(mode, brightness)
                } else {
                    light.changeLight(mode, brightness)
                }

                val playServiceId = payload.playServiceId

                if (result) {
                    if (turnOn) {
                        sendEvent(NAME_TURN_ON_LIGHT_SUCCEEDED, playServiceId)
                    } else {
                        sendEvent(NAME_CHANGE_LIGHT_SUCCEEDED, playServiceId)
                    }
                    executeSetHandlingCompleted(info)
                } else {
                    if (turnOn) {
                        sendEvent(NAME_TURN_ON_LIGHT_FAILED, playServiceId)
                    } else {
                        sendEvent(NAME_CHANGE_LIGHT_FAILED, playServiceId)
                    }
                    executeSetHandlingFailed(info, "executeChangeLight failed")
                }
            }
        }

        private fun executeHandleFlicker(info: DirectiveInfo) {
            with(info.directive) {
                val payload = MessageFactory.create(payload, FlickerPayload::class.java)
                if (payload == null) {
                    Logger.w(
                        TAG,
                        "[executeHandleChangeLight] invalid payload: ${this.payload}"
                    )
                    executeSetHandlingFailed(
                        info,
                        "[executeHandleChangeLight] invalid payload: ${this.payload}"
                    )
                    return
                }

                val strMode = payload.mode
                val mode = Light.Mode.valueOf(strMode)
                if (mode == null) {
                    Logger.w(TAG, "[executeHandleFlicker] wrong mode : $strMode")
                    executeSetHandlingFailed(
                        info,
                        "[executeHandleFlicker] wrong mode : $strMode"
                    )
                    return
                }

                Logger.d(
                    TAG,
                    "[executeHandleFlicker] payload: $payload"
                )

                if (light.flicker(
                        payload.onTimeInMilliseconds,
                        payload.offTimeInMilliseconds,
                        payload.repeatCount,
                        mode
                    )
                ) {
                    sendEvent(NAME_FLICKER_SUCCEEDED, payload.playServiceId)
                    executeSetHandlingCompleted(info)
                } else {
                    sendEvent(NAME_FLICKER_FAILED, payload.playServiceId)
                    executeSetHandlingFailed(info, "executeFlicker failed")
                }
            }
        }

        private fun executeSetHandlingCompleted(info: DirectiveInfo) {
            info.result.setCompleted()
            removeDirective(info)
        }

        private fun executeSetHandlingFailed(info: DirectiveInfo, msg: String) {
            info.result.setFailed(msg)
            removeDirective(info)
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

            configuration[TURN_ON_LIGHT] = nonBlockingPolicy
            configuration[TURN_OFF_LIGHT] = nonBlockingPolicy
            configuration[CHANGE_LIGHT] = nonBlockingPolicy
            configuration[FLICKER] = nonBlockingPolicy

            return configuration
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            contextSetter.setState(
                namespaceAndName,
                buildContext(),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }

        private fun buildContext() = JsonObject().apply {
            addProperty("version", VERSION)
            light.getLightSettings()?.let {
                addProperty(KEY_STATE, if (it.isOn) "ON" else "OFF")
                addProperty(KEY_MODE, it.mode.name)
                addProperty(KEY_BRIGHTNESS, it.brightness)
            }
            light.getMaxBrightness()?.let {
                addProperty(KEY_MAX_BRIGHTNESS, it)
            }
            light.getMinBrightness()?.let {
                addProperty(KEY_MIN_BRIGHTNESS, it)
            }
            add("supportedModes", JsonArray().apply {
                light.getSupportedModes().forEach {
                    add(it.name)
                }
            })
            light.getMaxFlickerCount()?.let {
                addProperty(KEY_MAX_FLICKER_COUNT, it)
            }
        }.toString()

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }

        private fun sendEvent(name: String, playServiceId: String) {
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    val messageRequest =
                        EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION)
                            .payload(JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                            }.toString()).build()
                    messageSender.sendMessage(messageRequest)
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                }
            })
        }
    }
}