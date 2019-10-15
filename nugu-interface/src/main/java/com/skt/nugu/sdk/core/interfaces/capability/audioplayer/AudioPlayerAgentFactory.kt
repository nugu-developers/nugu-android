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
package com.skt.nugu.sdk.core.interfaces.capability.audioplayer

import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter

interface AudioPlayerAgentFactory {
    fun create(
        mediaPlayer: MediaPlayerInterface,
        messageSender: MessageSender,
        focusManager: FocusManagerInterface,
        contextManager: ContextManagerInterface,
        playbackRouter: PlaybackRouter,
        playSynchronizer: PlaySynchronizerInterface,
        channelName: String,
        displayAgent: DisplayAgentInterface?
    ): AbstractAudioPlayerAgent
}