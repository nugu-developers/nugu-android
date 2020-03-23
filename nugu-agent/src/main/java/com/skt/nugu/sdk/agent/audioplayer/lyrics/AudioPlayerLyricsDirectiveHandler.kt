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
package com.skt.nugu.sdk.agent.audioplayer.lyrics

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class AudioPlayerLyricsDirectiveHandler(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val visibilityController: VisibilityController,
    private val pageController: PagingController
): AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "AudioPlayerLyricsDirectiveHandler"

        private const val NAMESPACE =
            DefaultAudioPlayerAgent.NAMESPACE
        private val VERSION =
            DefaultAudioPlayerAgent.VERSION

        // v1.1
        private const val NAME_SHOW_LYRICS = "ShowLyrics"
        private const val NAME_HIDE_LYRICS = "HideLyrics"
        private const val NAME_CONTROL_LYRICS_PAGE = "ControlLyricsPage"

        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val SHOW_LYRICS =
            NamespaceAndName(
                NAMESPACE,
                NAME_SHOW_LYRICS
            )

        private val HIDE_LYRICS =
            NamespaceAndName(
                NAMESPACE,
                NAME_HIDE_LYRICS
            )

        private val CONTROL_LYRICS_PAGE =
            NamespaceAndName(
                NAMESPACE,
                NAME_CONTROL_LYRICS_PAGE
            )
    }

    interface PagingController {
        fun controlPage(playServiceId: String, direction: Direction): Boolean
    }

    interface VisibilityController {
        fun show(playServiceId: String): Boolean
        fun hide(playServiceId: String): Boolean
    }

    private data class VisibilityLyricsPayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    private data class PageControlLyricsPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("direction")
        val direction: Direction
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val referrerDialogRequestId = info.directive.getDialogRequestId()
        when(info.directive.getNamespaceAndName()) {
            SHOW_LYRICS,
            HIDE_LYRICS -> {
                val payload = MessageFactory.create(info.directive.payload, VisibilityLyricsPayload::class.java)
                if(payload == null) {
                    Logger.d(TAG, "[handleDirective] invalid payload")
                    setHandlingFailed(info, "[handleDirective] invalid payload")
                    return
                }

                val playServiceId = payload.playServiceId

                if(SHOW_LYRICS == info.directive.getNamespaceAndName()) {
                    if (visibilityController.show(playServiceId)) {
                        sendVisibilityEvent("$NAME_SHOW_LYRICS$NAME_SUCCEEDED", playServiceId, referrerDialogRequestId)
                    } else {
                        sendVisibilityEvent("$NAME_SHOW_LYRICS$NAME_FAILED", playServiceId, referrerDialogRequestId)
                    }
                } else {
                    if (visibilityController.hide(playServiceId)) {
                        sendVisibilityEvent("$NAME_HIDE_LYRICS$NAME_SUCCEEDED", playServiceId, referrerDialogRequestId)
                    } else {
                        sendVisibilityEvent("$NAME_HIDE_LYRICS$NAME_FAILED", playServiceId, referrerDialogRequestId)
                    }
                }
            }
            CONTROL_LYRICS_PAGE -> {
                val payload = MessageFactory.create(info.directive.payload, PageControlLyricsPayload::class.java)
                if(payload == null) {
                    Logger.d(TAG, "[handleDirective] invalid payload")
                    setHandlingFailed(info, "[handleDirective] invalid payload")
                    return
                }

                with(payload) {
                    if(pageController.controlPage(playServiceId, direction)) {
                        sendPageControlEvent("$NAME_CONTROL_LYRICS_PAGE$NAME_SUCCEEDED", playServiceId, direction, referrerDialogRequestId)
                    } else {
                        sendPageControlEvent("$NAME_CONTROL_LYRICS_PAGE$NAME_FAILED", playServiceId, direction, referrerDialogRequestId)
                    }
                }
            }
        }

        setHandlingCompleted(info)
    }

    private fun sendVisibilityEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
                        .payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                            }.toString()
                        )
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, NamespaceAndName("supportedInterfaces", NAMESPACE))
    }

    private fun sendPageControlEvent(name: String, playServiceId: String, direction: Direction, referrerDialogRequestId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
                        .payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                                addProperty("direction", direction.name)
                            }.toString()
                        )
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, NamespaceAndName("supportedInterfaces", NAMESPACE))
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[SHOW_LYRICS] = nonBlockingPolicy
        configuration[HIDE_LYRICS] = nonBlockingPolicy
        configuration[CONTROL_LYRICS_PAGE] = nonBlockingPolicy

        return configuration
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }
}