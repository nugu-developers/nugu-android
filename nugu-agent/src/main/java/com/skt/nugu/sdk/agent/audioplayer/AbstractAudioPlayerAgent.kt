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
package com.skt.nugu.sdk.agent.audioplayer

import com.skt.nugu.sdk.core.interfaces.capability.AbstractCapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackHandler
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter

abstract class AbstractAudioPlayerAgent(
    protected val mediaPlayer: MediaPlayerInterface,
    protected val messageSender: MessageSender,
    protected val focusManager: FocusManagerInterface,
    protected val contextManager: ContextManagerInterface,
    protected val playbackRouter: PlaybackRouter,
    protected val playSynchronizer: PlaySynchronizerInterface,
    protected val playStackManager: PlayStackManagerInterface,
    protected val channelName: String
) : AbstractCapabilityAgent()
    , ChannelObserver
    ,
    AudioPlayerAgentInterface
    , PlaybackHandler {
    companion object {
        const val NAMESPACE = "AudioPlayer"
        const val VERSION = "1.0"

        const val NAME_PLAY = "Play"
        const val NAME_STOP = "Stop"
        const val NAME_PAUSE = "Pause"

        val PLAY = NamespaceAndName(
            NAMESPACE,
            NAME_PLAY
        )
        val STOP = NamespaceAndName(
            NAMESPACE,
            NAME_STOP
        )
        val PAUSE = NamespaceAndName(
            NAMESPACE,
            NAME_PAUSE
        )
    }

    abstract fun shutdown()
}