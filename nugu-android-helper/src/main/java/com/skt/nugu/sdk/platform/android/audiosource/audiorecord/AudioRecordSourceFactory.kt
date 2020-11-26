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
package com.skt.nugu.sdk.platform.android.audiosource.audiorecord

import android.media.MediaRecorder
import android.os.Build
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.audiosource.AudioSourceFactory
import com.skt.nugu.sdk.platform.android.audiosource.AudioSource

/**
 * Default implementation of [AudioSourceFactory] for [android.media.AudioRecord]
 */
class AudioRecordSourceFactory(
    val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    val sampleRate: Int = 16000,
    val channelConfig: Int = android.media.AudioFormat.CHANNEL_IN_FRONT,
    val audioFormat: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    val bufferSize: Int = sampleRate * 2 * 10
) : AudioSourceFactory {
    companion object {
        // To remove error, shorten TAG
        private const val TAG = "ARecordSourceFactory"
        //private const val TAG = "AudioRecordSourceFactory"
    }
    override fun create(): AudioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AudioRecordSourceAboveQ(
            audioSource,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
    } else {
        AudioRecordSource(
            audioSource,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
    }

    override fun getFormat(): AudioFormat {
        return AudioFormat(
            sampleRate,
            getBitsPerSample(),
            getNumChannels()
        )
    }

    private fun getBitsPerSample(): Int {
        return when(audioFormat) {
            android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
            android.media.AudioFormat.ENCODING_DEFAULT,
            android.media.AudioFormat.ENCODING_IEC61937,
            android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> {
                Logger.e(TAG, "[getBitsPerSample] unsupported audioFormat: $audioFormat")
                -1
            }
        }
    }

    private fun getNumChannels(): Int {
        return when(channelConfig) {
            android.media.AudioFormat.CHANNEL_IN_MONO -> 1
            android.media.AudioFormat.CHANNEL_IN_STEREO -> 2
            else -> {
                Logger.e(TAG, "[getBitsPerSample] unsupported channel: $channelConfig")
                -1
            }
        }
    }
}