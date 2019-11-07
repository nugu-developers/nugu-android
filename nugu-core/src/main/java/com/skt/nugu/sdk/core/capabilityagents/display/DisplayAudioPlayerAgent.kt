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
package com.skt.nugu.sdk.core.capabilityagents.display

import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.playsynchronizer.PlaySynchronizer
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.HashMap

class DisplayAudioPlayerAgent(
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizer,
    channelName: String
) : BaseDisplayAgent(focusManager, contextManager, messageSender, playSynchronizer, channelName),
    AudioPlayerAgentInterface.Listener {
    companion object {
        const val NAMESPACE = "AudioPlayer"
        const val VERSION = "1.0"

        private const val NAME_AUDIOPLAYER_TEMPLATE1 = "Template1"
        private const val NAME_AUDIOPLAYER_TEMPLATE2 = "Template2"

        private val AUDIOPLAYER_TEMPLATE1 =
            NamespaceAndName(NAMESPACE, NAME_AUDIOPLAYER_TEMPLATE1)
        private val AUDIOPLAYER_TEMPLATE2 =
            NamespaceAndName(NAMESPACE, NAME_AUDIOPLAYER_TEMPLATE2)
    }

    private var playerActivity: AudioPlayerAgentInterface.State =
        AudioPlayerAgentInterface.State.IDLE

    override fun getNamespace(): String = NAMESPACE

    override fun getVersion(): String = VERSION

    override fun getDisplayType(): DisplayAggregatorInterface.Type = DisplayAggregatorInterface.Type.AUDIO_PLAYER

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

    override fun onStateChanged(
        activity: AudioPlayerAgentInterface.State,
        context: AudioPlayerAgentInterface.Context
    ) {
        executor.submit {
            if (activity == playerActivity) {
                return@submit
            }

            playerActivity = activity

            when (activity) {
                AudioPlayerAgentInterface.State.PAUSED -> restartClearTimer(10 * 60 * 1000L)
//                State.PLAYING -> stopClearTimer()
//                State.STOPPED,
//                State.FINISHED -> restartClearTimer()
                else -> {
                }
            }
        }
    }
}