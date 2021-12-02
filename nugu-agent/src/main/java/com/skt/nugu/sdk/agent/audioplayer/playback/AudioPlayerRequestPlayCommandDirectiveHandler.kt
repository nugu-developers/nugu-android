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

package com.skt.nugu.sdk.agent.audioplayer.playback

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class AudioPlayerRequestPlayCommandDirectiveHandler(
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
): AbstractDirectiveHandler() {
    companion object {
        const val NAMESPACE =
            DefaultAudioPlayerAgent.NAMESPACE
        val VERSION =
            DefaultAudioPlayerAgent.VERSION

        // v1.2
        private const val NAME_REQUEST_PLAY_COMMAND = "RequestPlayCommand"
        private const val NAME_REQUEST_PLAY_COMMAND_ISSUED = "${NAME_REQUEST_PLAY_COMMAND}Issued"
        private val REQUEST_PLAY_COMMAND = NamespaceAndName(NAMESPACE, NAME_REQUEST_PLAY_COMMAND)
    }

    private var handler: AudioPlayerAgentInterface.RequestCommandHandler? = null

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        info.result.setCompleted()
        contextGetter.getContext(object: IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val header = info.directive.header
                val payload = info.directive.payload

                if(handler?.handleRequestCommand(payload, header) != true) {
                    val message = EventMessageRequest.Builder(
                        jsonContext,
                        header.namespace,
                        NAME_REQUEST_PLAY_COMMAND_ISSUED,
                        VERSION.toString()
                    )
                        .payload(payload)
                        .referrerDialogRequestId(info.directive.getDialogRequestId())
                        .build()

                    messageSender.newCall(message).enqueue(null)
                }
            }
        })
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[REQUEST_PLAY_COMMAND] = BlockingPolicy.sharedInstanceFactory.get()
    }

    fun setRequestCommandHandler(handler: AudioPlayerAgentInterface.RequestCommandHandler) {
        this.handler = handler
    }
}