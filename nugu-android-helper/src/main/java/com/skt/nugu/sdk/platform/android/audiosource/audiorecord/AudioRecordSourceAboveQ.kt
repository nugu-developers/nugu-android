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

package com.skt.nugu.sdk.platform.android.audiosource.audiorecord

import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.audiosource.AudioSource
import java.nio.ByteBuffer
import java.util.concurrent.Executors

internal class AudioRecordSourceAboveQ(
    audioSource: Int,
    sampleRate: Int,
    channelConfig: Int,
    audioFormat: Int,
    bufferSize: Int
) : AudioRecordSource(
    audioSource, sampleRate, channelConfig, audioFormat, bufferSize
) {
    private var isClientSilenced: Boolean = false
    private val audioRecordingCallback: AudioManager.AudioRecordingCallback? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    super.onRecordingConfigChanged(configs)
                    configs?.find { it.clientAudioSessionId == audioRecord?.audioSessionId }?.let {
                        Logger.d(
                            TAG,
                            "[onRecordingConfigChanged] isClientSilenced: ${it.isClientSilenced}"
                        )
                        isClientSilenced = it.isClientSilenced
                    }
                }
            }
        } else {
            null
        }
    }

    private val recordingCallbackExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    override fun open(): Boolean {
        return if (super.open()) {
            isClientSilenced = false
            audioRecord?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    audioRecordingCallback?.let {
                        registerAudioRecordingCallback(
                            recordingCallbackExecutor,
                            it
                        )
                    }
                }
            }
            true
        } else {
            false
        }
    }

    override fun read(buffer: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        return if(isClientSilenced) {
            AudioSource.SOURCE_BAD_VALUE
        } else {
            super.read(buffer, offsetInBytes, sizeInBytes)
        }
    }

    override fun read(buffer: ByteBuffer, sizeInBytes: Int): Int {
        return if(isClientSilenced) {
            AudioSource.SOURCE_BAD_VALUE
        } else {
            super.read(buffer, sizeInBytes)
        }
    }

    override fun close() {
        audioRecord?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                audioRecordingCallback?.let {
                    unregisterAudioRecordingCallback(it)
                }
            }
        }
        super.close()
        isClientSilenced = false
    }
}