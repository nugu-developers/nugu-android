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
package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.HashMap

class DisplayAudioPlayerAgent(
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface,
    channelName: String
) : BaseDisplayAgent(
    focusManager,
    contextManager,
    messageSender,
    playSynchronizer,
    playStackManager,
    inputProcessorManager,
    channelName
)
    , AudioPlayerMetadataDirectiveHandler.Listener {
    companion object {
        private const val TAG = "DisplayAudioPlayerAgent"
        const val NAMESPACE = AbstractAudioPlayerAgent.NAMESPACE
        const val VERSION = AbstractAudioPlayerAgent.VERSION

        private const val NAME_AUDIOPLAYER_TEMPLATE1 = "Template1"
        private const val NAME_AUDIOPLAYER_TEMPLATE2 = "Template2"

        private val AUDIOPLAYER_TEMPLATE1 =
            NamespaceAndName(
                NAMESPACE,
                NAME_AUDIOPLAYER_TEMPLATE1
            )
        private val AUDIOPLAYER_TEMPLATE2 =
            NamespaceAndName(
                NAMESPACE,
                NAME_AUDIOPLAYER_TEMPLATE2
            )
    }

    override fun getNamespace(): String =
        NAMESPACE

    override fun getVersion(): String =
        VERSION

    override fun getContextPriority(): Int = 300

    override fun executeOnFocusBackground(info: DirectiveInfo) {
        // no-op
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[AUDIOPLAYER_TEMPLATE1] = nonBlockingPolicy
        configuration[AUDIOPLAYER_TEMPLATE2] = nonBlockingPolicy

        return configuration
    }

    override fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        // nothing
    }

    override fun onMetadataUpdate(playServiceId: String, jsonMetaData: String) {
        executor.submit {
            val info = currentInfo
            Logger.d(
                TAG,
                "[onMetadataUpdate] playServiceId: $playServiceId, jsonMetadata: $jsonMetaData"
            )
            if (info == null) {
                Logger.w(TAG, "[onMetadataUpdate] skip - currentInfo is null (no display)")
                return@submit
            }

            val currentPlayServiceId = info.payload.playServiceId
            if (currentPlayServiceId != playServiceId) {
                Logger.w(
                    TAG,
                    "[onMetadataUpdate] skip - playServiceId does not matched with current (current: $currentPlayServiceId)"
                )
                return@submit
            }

            getRenderer()?.update(info.getTemplateId(), jsonMetaData)
        }
    }
}