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
package com.skt.nugu.sampleapp.player

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.skt.nugu.sampleapp.player.exo.ExoMediaPlayer
import com.skt.nugu.sdk.agent.mediaplayer.PlayerFactory
import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer
import com.skt.nugu.sdk.external.silvertray.NuguOpusPlayer
import com.skt.nugu.sdk.platform.android.mediaplayer.AndroidMediaPlayer
import com.skt.nugu.sdk.platform.android.mediaplayer.IntegratedMediaPlayer

class SamplePlayerFactory constructor(
    private val context: Context,
    private val useExoPlayer: Boolean
) : PlayerFactory {

    private val alertsPlayer by lazy {
        IntegratedMediaPlayer(
            createPlayer(),
            NuguOpusPlayer(AudioManager.STREAM_MUSIC)
        )
    }

    private val audioPlayer by lazy {
        IntegratedMediaPlayer(
            createPlayer(),
            NuguOpusPlayer(AudioManager.STREAM_MUSIC)
        )
    }

    private val speakPlayer by lazy {
        IntegratedMediaPlayer(
            createPlayer(),
            NuguOpusPlayer(AudioManager.STREAM_MUSIC)
        )
    }

    private val beepPlayer by lazy {
        createPlayer()
    }

    override fun createAlertsPlayer() = alertsPlayer

    override fun createAudioPlayer() = audioPlayer

    override fun createSpeakPlayer() = speakPlayer

    override fun createBeepPlayer() = beepPlayer

    private fun createPlayer(): UriSourcePlayablePlayer {
        return if (useExoPlayer) {
            ExoMediaPlayer(context)
        } else {
            AndroidMediaPlayer(context, MediaPlayer())
        }
    }
}