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

import android.content.res.AssetManager
import com.skt.nugu.jademarblelib.TycheEdgePointDetectorStateObserver
import com.skt.nugu.jademarblelib.TycheEndPointDetectorFactory
import com.skt.nugu.jademarblelib.TycheEndPointDetectorInterface
import com.skt.nugu.jademarblelib.core.AudioInput
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.utils.Logger

/**
 * Porting class for [TycheEndPointDetector] to use in NUGU SDK
 * @param epdModelFilePath the absolute path for epd model file
 */
class EndPointDetector(epdModelFilePath: String, assetManager: AssetManager? = null) : AudioEndPointDetector {
    companion object {
        private const val TAG = "EndPointDetector"
    }

    private val endPointDetector: TycheEndPointDetectorInterface = if(assetManager != null ) {
        TycheEndPointDetectorFactory.create(assetManager, epdModelFilePath)
    } else {
        TycheEndPointDetectorFactory.create(epdModelFilePath)
    }
    private val listeners = HashSet<AudioEndPointDetector.OnStateChangedListener>()
    private var state = AudioEndPointDetector.State.STOP
    private var audioInputStreamReader: SharedDataStream.Reader? = null
    private var audioFormat: AudioFormat? = null

    init {
        endPointDetector.addStateChangeObserver(object : TycheEdgePointDetectorStateObserver {
            override fun onExpectingSpeech() {
                if(setState(AudioEndPointDetector.State.EXPECTING_SPEECH)) {
                    listeners.forEach {
                        it.onExpectingSpeech()
                    }
                }
            }

            override fun onSpeechStart(eventPosition: Long) {
                if(setState(AudioEndPointDetector.State.SPEECH_START)) {
                    listeners.forEach {
                        it.onSpeechStart(eventPosition)
                    }
                }
            }

            override fun onSpeechEnd(eventPosition: Long) {
                closeAudioInputStreamReader()
                if(setState(AudioEndPointDetector.State.SPEECH_END)) {
                    listeners.forEach {
                        it.onSpeechEnd(eventPosition)
                    }
                }
            }

            override fun onStop() {
                closeAudioInputStreamReader()
                if(setState(AudioEndPointDetector.State.STOP)) {
                    listeners.forEach {
                        it.onStop()
                    }
                }
            }

            override fun onTimeout(type: TycheEdgePointDetectorStateObserver.TimeoutType) {
                closeAudioInputStreamReader()
                if(setState(AudioEndPointDetector.State.TIMEOUT)) {
                    listeners.forEach {
                        it.onTimeout(
                            when (type) {
                                TycheEdgePointDetectorStateObserver.TimeoutType.SPEECH_TIMEOUT -> AudioEndPointDetector.TimeoutType.SPEECH_TIMEOUT
                                TycheEdgePointDetectorStateObserver.TimeoutType.LISTENING_TIMEOUT -> AudioEndPointDetector.TimeoutType.LISTENING_TIMEOUT
                            }
                        )
                    }
                }
            }

            override fun onError(
                type: TycheEdgePointDetectorStateObserver.ErrorType,
                e: Exception?
            ) {
                closeAudioInputStreamReader()
                if(setState(AudioEndPointDetector.State.ERROR)) {
                    listeners.forEach {
                        it.onError(
                            when (type) {
                                TycheEdgePointDetectorStateObserver.ErrorType.ERROR_EPD_ENGINE -> AudioEndPointDetector.ErrorType.ERROR_EPD_ENGINE
                                TycheEdgePointDetectorStateObserver.ErrorType.ERROR_AUDIO_INPUT -> AudioEndPointDetector.ErrorType.ERROR_AUDIO_INPUT
                                TycheEdgePointDetectorStateObserver.ErrorType.ERROR_EXCEPTION -> AudioEndPointDetector.ErrorType.ERROR_EXCEPTION
                            }, e
                        )
                    }
                }
            }

            private fun closeAudioInputStreamReader() {
                Logger.d(TAG, "[closeAudioInputStreamReader]")
                audioInputStreamReader?.close()
                audioInputStreamReader = null
            }
        })
    }

    private fun setState(state: AudioEndPointDetector.State): Boolean {
        if (this.state == state) {
            return false
        }

        Logger.d(TAG, "[setState] state: $state")
        this.state = state
        return true
    }

    override fun startDetector(
        reader: SharedDataStream.Reader,
        audioFormat: AudioFormat,
        timeoutInSeconds: Int,
        maxDurationInSeconds: Int,
        pauseLengthInMilliseconds: Int
    ): Boolean {
        Logger.d(TAG, "[startDetector] $reader")
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
        Logger.d(TAG, "[stopDetector]")
        endPointDetector.stop()
    }

    override fun addListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        Logger.d(TAG, "[addObserver] $listener")
        listeners.add(listener)
    }

    override fun removeListener(listener: AudioEndPointDetector.OnStateChangedListener) {
        Logger.d(TAG, "[removeObserver] $listener")
        listeners.remove(listener)
    }
}