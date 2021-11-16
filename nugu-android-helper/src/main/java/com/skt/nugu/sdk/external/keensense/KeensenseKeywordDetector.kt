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

package com.skt.nugu.sdk.external.keensense

import android.content.Context
import android.content.res.AssetManager
import com.skt.nugu.keensense.KeywordDetectorObserver
import com.skt.nugu.keensense.KeywordDetectorStateObserver
import com.skt.nugu.keensense.tyche.TycheKeywordDetector
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.speechrecognizer.KeywordDetector
import com.skt.nugu.sdk.platform.android.speechrecognizer.KeywordPowerMeasure
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.PowerMeasure
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.ArrayList

class KeensenseKeywordDetector(
    var keywordResource: KeywordResources,
    private val powerMeasure: PowerMeasure? = null,
    assetManager: AssetManager? = null,
) : KeywordDetector {
    companion object {
        private const val TAG = "KeenKeywordDetector"
    }

    data class KeywordResources(
        val keyword: String,
        val netFilePath: String,
        val searchFilePath: String
    )

    private val detector = TycheKeywordDetector(assetManager)
    private val onStateChangeListeners = CopyOnWriteArraySet<KeywordDetector.OnStateChangeListener>()

    init {
        detector.addDetectorStateObserver(object : KeywordDetectorStateObserver {
            override fun onStateChange(state: KeywordDetectorStateObserver.State) {
                onStateChangeListeners.forEach {
                    it.onStateChange(when(state) {
                        KeywordDetectorStateObserver.State.ACTIVE -> KeywordDetector.State.ACTIVE
                        KeywordDetectorStateObserver.State.INACTIVE -> KeywordDetector.State.INACTIVE
                    })
                }
            }
        })
    }

    override fun startDetect(
        inputStream: SharedDataStream,
        audioFormat: AudioFormat,
        observer: KeywordDetector.DetectorResultObserver
    ): Boolean {
        KeywordDetectorInput(inputStream).let {
            val keyword = keywordResource.keyword
            val keywordPowerMeasure = powerMeasure?.let { measure ->
                KeywordPowerMeasure(measure)
            }

            val result = detector.startDetect(
                it,
                com.skt.nugu.keensense.AudioFormat(
                    audioFormat.sampleRateHz,
                    audioFormat.bitsPerSample,
                    audioFormat.numChannels
                ),
                TycheKeywordDetector.KeywordResources(
                    keywordResource.netFilePath,
                    keywordResource.searchFilePath
                ),
                object : KeywordDetectorObserver {
                    override fun onDetecting(buffer: ByteBuffer) {
                        keywordPowerMeasure?.accumulate(buffer)
                    }

                    override fun onDetected(
                        startOffset: Long?,
                        endOffset: Long?,
                        detectOffset: Long?,
                        startMarginOffset: Long?
                    ) {
                        observer.onDetected(WakeupInfo(keyword, WakeupInfo.Boundary(
                            startOffset!!,
                            endOffset!!,
                            detectOffset!!
                        ), keywordPowerMeasure?.getEstimatedPower()))
                        it.release()
                    }

                    override fun onStopped() {
                        observer.onStopped()
                        it.release()
                    }

                    override fun onError(errorType: KeywordDetectorObserver.ErrorType) {
                        when (errorType) {
                            KeywordDetectorObserver.ErrorType.ERROR_AUDIO_INPUT -> observer.onError(
                                KeywordDetector.DetectorResultObserver.ErrorType.ERROR_AUDIO_INPUT
                            )
                            KeywordDetectorObserver.ErrorType.ERROR_UNKNOWN -> observer.onError(
                                KeywordDetector.DetectorResultObserver.ErrorType.ERROR_UNKNOWN
                            )
                        }
                        it.release()
                    }
                })

            return if (!result.get()) {
                it.release()
                Logger.w(TAG, "[startDetect] failed - already executing ")
                false
            } else {
                Logger.d(TAG, "[startDetect] start")
                true
            }
        }
    }

    override fun stopDetect() {
        detector.stopDetect()
    }

    override fun getDetectorState(): KeywordDetector.State {
        return when(detector.getDetectorState()) {
            KeywordDetectorStateObserver.State.ACTIVE -> KeywordDetector.State.ACTIVE
            KeywordDetectorStateObserver.State.INACTIVE -> KeywordDetector.State.INACTIVE
        }
    }

    override fun addOnStateChangeListener(listener: KeywordDetector.OnStateChangeListener) {
        onStateChangeListeners.add(listener)
    }

    override fun removeDetectorStateObserver(listener: KeywordDetector.OnStateChangeListener) {
        onStateChangeListeners.remove(listener)
    }

    override fun getSupportedFormats(): List<AudioFormat> = ArrayList<AudioFormat>().apply {
        detector.getSupportedFormats().forEach {
            add(AudioFormat(
                it.sampleRateHz,
                it.bitsPerSample,
                it.numChannels
            ))
        }
    }

    private class KeywordDetectorInput(audioInputStream: SharedDataStream) : com.skt.nugu.keensense.AudioInput {
        private val reader = audioInputStream.createReader()

        override fun getPosition(): Long = reader.position()

        override fun read(buffer: ByteBuffer, sizeInBytes: Int): Int {
            return reader.read(buffer, 0, sizeInBytes)
        }

        fun release() {
            reader.close()
        }
    }
}