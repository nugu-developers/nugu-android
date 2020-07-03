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
package com.skt.nugu.sdk.platform.android.speaker

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.skt.nugu.sdk.agent.speaker.Speaker

/**
 * Default Implementation of [Speaker] for android
 */
abstract class AndroidAudioSpeaker(
    private val context: Context,
    private val streamType: Int
) : Speaker {
    override fun getSpeakerSettings(): Speaker.SpeakerSettings? {
        return Speaker.SpeakerSettings(
            getAudioManager().getStreamVolume(streamType),
            isStreamMute()
        )
    }

    override fun getMaxVolume(): Int? = getAudioManager().getStreamMaxVolume(streamType)

    override fun getMinVolume(): Int? =
        if (Build.VERSION.SDK_INT < 28) 0
        else getAudioManager().getStreamMinVolume(streamType)

    override fun getDefaultVolumeStep(): Int? = 1
    override fun getDefaultVolumeLevel(): Int? = 1

    override fun setVolume(volume: Int, rate: Speaker.Rate): Boolean {
        // TODO :apply rate
        getAudioManager().setStreamVolume(streamType, volume, getVolumeFlag())
        return true
    }

    override fun setMute(mute: Boolean): Boolean {
        val audioManager = getAudioManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(streamType, direction, getVolumeFlag())
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(streamType, mute)
        }

        return true
    }

    @SuppressWarnings
    override fun adjustVolume(deltaVolume: Int): Boolean {

        val am = getAudioManager()
        val current = am.getStreamVolume(streamType)
        val target = current + deltaVolume
        // TODO : check range
        setVolume(target)
        return true
    }

    private fun getAudioManager() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun getVolumeFlag() = AudioManager.FLAG_SHOW_UI

    private fun isStreamMute(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getAudioManager().isStreamMute(streamType)
        } else {
            try {
                val m = AudioManager::class.java.getMethod("isStreamMute", Int::class.javaPrimitiveType)
                m.invoke(getAudioManager(), streamType) as Boolean
            } catch (ignored: Exception) {
                false
            }
        }
    }
}