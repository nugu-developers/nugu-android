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

import android.media.AudioRecord
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.audiosource.AudioSource
import java.nio.ByteBuffer

/**
 * Default implementation of [AudioSource] for [android.media.AudioRecord]
 */
internal open class AudioRecordSource(
    private val audioSource: Int,
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
    private val bufferSize: Int
) : AudioSource {
    companion object {
        const val TAG = "AudioRecordSource"
    }

    protected var audioRecord: AudioRecord? = null

    override fun open(): Boolean {
        Logger.d(TAG, "[open]")
        if(audioRecord != null) {
            return false
        }

        AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        ).apply {
            if(state == AudioRecord.STATE_UNINITIALIZED) {
                return false
            }

            try {
                startRecording()
            } catch (th: Throwable) {
                return false
            }

            audioRecord = this
        }

        return true
    }

    override fun read(buffer: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        audioRecord?.apply {
            if(recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                return read(buffer, offsetInBytes, sizeInBytes)
            }
        }

        return AudioSource.SOURCE_CLOSED
    }

    override fun read(buffer: ByteBuffer, sizeInBytes: Int): Int {
        audioRecord?.apply {
            if(recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                return read(buffer, sizeInBytes)
            }
        }

        return AudioSource.SOURCE_CLOSED
    }

    override fun close() {
        Logger.d(TAG, "[close]")
        audioRecord?.apply {
            if(recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
    }
}