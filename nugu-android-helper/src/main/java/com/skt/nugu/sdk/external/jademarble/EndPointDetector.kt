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
package com.skt.nugu.sdk.external.jademarble

import android.util.Log
import com.skt.nugu.jademarblelib.TycheEdgePointDetectorStateObserver
import com.skt.nugu.jademarblelib.TycheEndPointDetector
import com.skt.nugu.jademarblelib.TycheEndPointDetectorInterface
import com.skt.nugu.jademarblelib.core.AudioInput
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream

/**
 * Porting class for [TycheEndPointDetector] to use in NUGU SDK
 * @param epdModelFilePath the absolute path for epd model file
 */
class EndPointDetector(epdModelFilePath: String) : AudioEndPointDetector {
    companion object {
        private const val TAG = "EndPointDetector"
    }

    private val endPointDetector: TycheEndPointDetectorInterface = TycheEndPointDetector(epdModelFilePath)
    private val listeners = HashSet<AudioEndPointDetector.OnStateChangedListener>()
    private var state = AudioEndPointDetector.State.STOP
    private var audioInputStreamReader: SharedDataStream.Reader? = null
    private var audioFormat: AudioFormat? = null

    private var speechStartPosition: Long? = null
    private var speechEndPosition: Long? = null

    init {
        endPointDetector.addStateChangeObserver(object : TycheEdgePointDetectorStateObserver {
            override fun onExpectingSpeech() {
                setState(AudioEndPointDetector.State.EXPECTING_SPEECH)
            }

            override fun onSpeechStart(eventPosition: Long) {
                speechStartPosition = eventPosition
                setState(AudioEndPointDetector.State.SPEECH_START)
            }

            override fun onSpeechEnd(eventPosition: Long) {
                speechEndPosition = eventPosition
                closeAudioInputStreamReader()
                setState(AudioEndPointDetector.State.SPEECH_END)
            }

            override fun onStop() {
                closeAudioInputStreamReader()
                setState(AudioEndPointDetector.State.STOP)
            }

            override fun onTimeout(type: TycheEdgePointDetectorStateObserver.TimeoutType) {
                closeAudioInputStreamReader()
                setState(AudioEndPointDetector.State.TIMEOUT)
            }

            override fun onError(
                type: TycheEdgePointDetectorStateObserver.ErrorType,
                e: Exception?
            ) {
                closeAudioInputStreamReader()
                setState(AudioEndPointDetector.State.ERROR)
            }

            private fun closeAudioInputStreamReader() {
                Log.d(TAG, "[closeAudioInputStreamReader]")
                audioInputStreamReader?.close()
                audioInputStreamReader = null
            }
        })
    }

    private fun setState(state: AudioEndPointDetector.State) {
        if (this.state == state) {
            return
        }

        Log.d(TAG, "[setState] state: $state")

        this.state = state

        notifyOnStateChanged(state)
    }

    override fun startDetector(
        reader: SharedDataStream.Reader,
        audioFormat: AudioFormat,
        timeoutInSeconds: Int,
        maxDurationInSeconds: Int,
        pauseLengthInMilliseconds: Int
    ): Boolean {
        Log.d(TAG, "[startDetector] $reader")
        speechStartPosition = null
        speechEndPosition = null
        this.audioInputStreamReader = reader
        this.audioFormat = audioFormat
        return reader.let {
            endPointDetector.start(
                object : AudioInput {
                    override fun getPosition(): Long = it.position()

                    override fun read(buffer: ByteArray, sizeInBytes: Int): Int = it.read(buffer, 0, sizeInBytes)
                }, com.skt.nugu.jademarblelib.core.AudioFormat(
                    audioFormat.sampleRateHz,
                    audioFormat.bitsPerSample,
                    audioFormat.numChannels
                )
                , timeoutInSeconds
                , maxDurationInSeconds
                , pauseLengthInMilliseconds
            ).get()
        }
    }

    override fun stopDetector() {
        Log.d(TAG, "[stopDetector]")
        endPointDetector.stop()
    }

    override fun addListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        Log.d(TAG, "[addObserver] $listener")
        listeners.add(listener)
    }

    override fun removeListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        Log.d(TAG, "[removeObserver] $listener")
        listeners.remove(listener)
    }

    override fun getSpeechStartPosition(): Long? = speechStartPosition

    override fun getSpeechEndPosition(): Long? = speechEndPosition

    private fun notifyOnStateChanged(state: AudioEndPointDetector.State) {
        for (listener in listeners) {
            listener.onStateChanged(state)
        }
    }
}