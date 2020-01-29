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
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
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
    private val presenter: LyricsPresenter
): AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "AudioPlayerLyricsDirectiveHandler"

        private const val NAMESPACE =
            AbstractAudioPlayerAgent.NAMESPACE
        private const val VERSION =
            AbstractAudioPlayerAgent.VERSION

        // v1.1
        private const val NAME_SHOW_LYRICS = "ShowLyrics"
        private const val NAME_HIDE_LYRICS = "HideLyrics"

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
    }

    private data class LyricsPayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, LyricsPayload::class.java)
        if(payload == null) {
            Logger.d(TAG, "[handleSetVolume] invalid payload")
            setHandlingFailed(info, "[handleSetVolume] invalid payload")
            return
        }

        val playServiceId = payload.playServiceId

        when(info.directive.getNamespaceAndName()) {
            SHOW_LYRICS -> {
                if(presenter.show(playServiceId)) {
                    sendEvent("$NAME_SHOW_LYRICS$NAME_SUCCEEDED", playServiceId)
                } else {
                    sendEvent("$NAME_SHOW_LYRICS$NAME_FAILED", playServiceId)
                }
            }
            HIDE_LYRICS -> {
                if(presenter.hide(playServiceId)) {
                    sendEvent("$NAME_HIDE_LYRICS$NAME_SUCCEEDED", playServiceId)
                } else {
                    sendEvent("$NAME_HIDE_LYRICS$NAME_FAILED", playServiceId)
                }
            }
        }

        setHandlingCompleted(info)
    }

    private fun sendEvent(name: String, playServiceId: String) {
        contextManager.getContext(object: ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION).payload(
                  JsonObject().apply {
                      addProperty("playServiceId", playServiceId)
                  }.toString()
                ).build())
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