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

package com.skt.nugu.sdk.agent.common.tts

import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface

class TTSPlayContextProvider
    : PlayStackManagerInterface.PlayContextProvider {
    companion object {
        private const val CONTEXT_PRESERVATION_DURATION_AFTER_TTS_FINISHED = 7000L
    }

    private var playContextValidTimestamp: Long = Long.MAX_VALUE
    private var currentPlayContext: PlayStackManagerInterface.PlayContext? = null

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? = if (playContextValidTimestamp > System.currentTimeMillis()) {
        currentPlayContext
    } else {
        null
    }

    fun onPlaybackStarted(playServiceId: String) {
        currentPlayContext =
            PlayStackManagerInterface.PlayContext(
                playServiceId,
                System.currentTimeMillis()
            )
        playContextValidTimestamp = Long.MAX_VALUE
    }

    fun onPlaybackStopped() {
        currentPlayContext = null
        playContextValidTimestamp = Long.MAX_VALUE
    }

    fun onPlaybackFinished() {
        currentPlayContext?.let {
            currentPlayContext =
                PlayStackManagerInterface.PlayContext(it.playServiceId, it.timestamp,
                    isBackground = false,
                    persistent = false
                )
            playContextValidTimestamp =
                System.currentTimeMillis() + CONTEXT_PRESERVATION_DURATION_AFTER_TTS_FINISHED
        }
    }
}