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
package com.skt.nugu.sampleapp.utils

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.os.Build


object VolumeUtils {
    fun unMute(context: Context, percent: Int = 25, stream: Int = AudioManager.STREAM_MUSIC) {
        val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val adjustVolume = if (percent > 100) {
            maxVolume
        } else {
            Math.min(Math.max((maxVolume * (percent / 100.0f) + 0.5f).toLong(), 1), maxVolume.toLong()).toInt()
        }

        audioManager.setStreamVolume(stream, adjustVolume, 0)
    }

    fun isMute(context: Context, stream: Int = AudioManager.STREAM_MUSIC): Boolean {
        var isMute = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
            isMute = audioManager.isStreamMute(stream)
            if (audioManager.getStreamVolume(stream) == 0) {
                isMute = true
            }
        } else {
            try {
                val m = AudioManager::class.java.getMethod("isStreamMute", Int::class.javaPrimitiveType!!)
                val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
                isMute = m.invoke(audioManager, stream) as Boolean
            } catch (ignored: Exception) {
            }

        }
        return isMute
    }
}