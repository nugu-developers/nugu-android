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
package com.skt.nugu.sdk.platform.android.speechrecognizer

import android.util.Log
import com.skt.nugu.keensense.KeywordDetectorObserver
import com.skt.nugu.keensense.KeywordDetectorStateObserver
import com.skt.nugu.keensense.tyche.TycheKeywordDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Implementation class of [SpeechRecognizerAggregatorInterface]
 * @param keywordResources the keyword resources for keywordDetector
 * @param speechProcessor the speech processor
 * @param audioProvider audioProvider to manage audio source for speech recognition
 */
class SpeechRecognizerAggregator(
    private var keywordResources: KeywordResources,
    private val speechProcessor: SpeechProcessorDelegate,
    private val audioProvider: AudioProvider
) : SpeechRecognizerAggregatorInterface {

    companion object {
        // const val TAG = "SpeechRecognizerAggregator"
        // To remove error, shorten TAG
        private const val TAG = "SpeechRecognizerAg"
    }

    data class KeywordResources(
        val netFilePath: String,
        val searchFilePath: String
    )

    override fun isActive(): Boolean = when (state) {
        SpeechRecognizerAggregatorInterface.State.WAITING,
        SpeechRecognizerAggregatorInterface.State.WAKEUP,
        SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
        SpeechRecognizerAggregatorInterface.State.SPEECH_START -> true
        else -> false
    }

    private val keywordDetector: TycheKeywordDetector = TycheKeywordDetector(
        TycheKeywordDetector.KeywordResources(
            keywordResources.netFilePath,
            keywordResources.searchFilePath
        )
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = HashSet<SpeechRecognizerAggregatorInterface.OnStateChangeListener>()
    private var state = SpeechRecognizerAggregatorInterface.State.STOP
    private var endPointDetectorState = AudioEndPointDetector.State.STOP
    private var keywordDetectorState = KeywordDetectorStateObserver.State.INACTIVE
    private var keywordDetectorResultRunnable: Runnable? = null

    private var isTriggerStoppingByStartListening = false

    private var audioFormat: com.skt.nugu.sdk.core.interfaces.audio.AudioFormat? = null
    private var keywordStartPosition: Long? = null
    private var keywordEndPosition: Long? = null
    private var keywordDetectPosition: Long? = null

    init {
        keywordDetector.addDetectorStateObserver(object : KeywordDetectorStateObserver {
            override fun onStateChange(state: KeywordDetectorStateObserver.State) {
                keywordDetectorState = state
                Log.d(TAG, "[KeywordDetectorStateObserver::onStateChange] state: $state")
                if (state == KeywordDetectorStateObserver.State.ACTIVE) {
                    setState(SpeechRecognizerAggregatorInterface.State.WAITING)
                } else {
                    val resultRunnable = keywordDetectorResultRunnable
                    if(resultRunnable != null) {
                        keywordDetectorResultRunnable = null
                        resultRunnable.run()
                    } else {
                        Log.e(TAG, "[KeywordDetectorStateObserver] keywordDetectorResultRunnable is null!!!")
                    }
                    isTriggerStoppingByStartListening = false
                }
            }
        })

        speechProcessor.addListener(object : AudioEndPointDetector.OnStateChangedListener {
            override fun onStateChanged(state: AudioEndPointDetector.State) {
                Log.d(TAG, "[AudioEndPointDetectorStateObserverInterface::onStateChange] state: $state")
                val aggregatorState = when (state) {
                    AudioEndPointDetector.State.EXPECTING_SPEECH -> {
                        if (keywordDetectorState == KeywordDetectorStateObserver.State.ACTIVE) {
                            keywordDetector.stopDetect()
                        }
                        isTriggerStoppingByStartListening = false
                        SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH
                    }
                    AudioEndPointDetector.State.SPEECH_START -> SpeechRecognizerAggregatorInterface.State.SPEECH_START
                    AudioEndPointDetector.State.SPEECH_END -> SpeechRecognizerAggregatorInterface.State.SPEECH_END
                    AudioEndPointDetector.State.STOP -> SpeechRecognizerAggregatorInterface.State.STOP
                    AudioEndPointDetector.State.ERROR -> SpeechRecognizerAggregatorInterface.State.ERROR
                    AudioEndPointDetector.State.TIMEOUT -> SpeechRecognizerAggregatorInterface.State.TIMEOUT
                }

                if (!state.isActive()) {
                    audioProvider.releaseAudioInputStream(speechProcessor)
                }

                endPointDetectorState = state
                setState(aggregatorState)
            }
        })
    }

    fun changeKeywordResource(keywordResource: KeywordResources) {
        keywordDetector.keywordResources = TycheKeywordDetector.KeywordResources(keywordResource.netFilePath, keywordResource.searchFilePath)
    }

    private val isStartListeningWithTriggering = AtomicBoolean(false)

    override fun startListeningWithTrigger() {
        if(isStartListeningWithTriggering.compareAndSet(false, true) && keywordDetectorState == KeywordDetectorStateObserver.State.INACTIVE) {
            val inputStream = audioProvider.acquireAudioInputStream(keywordDetector)
            Log.d(TAG, "[startListeningWithTrigger] start with input stream - $inputStream")

            if (inputStream != null) {
                KeywordDetectorInput(inputStream).let {
                    val audioFormat = audioProvider.getFormat()

                    val result = keywordDetector.startDetect(
                        it,
                        com.skt.nugu.keensense.AudioFormat(
                            audioFormat.sampleRateHz,
                            audioFormat.bitsPerSample,
                            audioFormat.numChannels
                        ),
                        object : KeywordDetectorObserver {
                            override fun onDetected() {
                                Log.d(
                                    TAG,
                                    "[onDetected] start: ${keywordDetector.getKeywordStartOffset()} , end : ${keywordDetector.getKeywordEndOffset()}"
                                )
                                keywordDetectorResultRunnable = Runnable {
                                    setState(SpeechRecognizerAggregatorInterface.State.WAKEUP)
                                    startListeningInternal(audioProvider.getFormat(), keywordDetector.getKeywordStartOffset(), keywordDetector.getKeywordEndOffset(), keywordDetector.getKeywordDetectOffset())
                                    it.release()
                                    audioProvider.releaseAudioInputStream(keywordDetector)
                                }
                            }

                            override fun onStopped() {
                                Log.d(TAG, "[onStopped] $isTriggerStoppingByStartListening")
                                keywordDetectorResultRunnable = Runnable {
                                    if (isTriggerStoppingByStartListening) {
                                        startListeningInternal(audioFormat, keywordStartPosition, keywordEndPosition, keywordDetectPosition)
                                        isTriggerStoppingByStartListening = false
                                    } else if (state == SpeechRecognizerAggregatorInterface.State.WAITING) {
                                        setState(SpeechRecognizerAggregatorInterface.State.STOP)
                                    }
                                    it.release()
                                    audioProvider.releaseAudioInputStream(keywordDetector)
                                }
                            }

                            override fun onError(errorType: KeywordDetectorObserver.ErrorType) {
                                Log.d(TAG, "[onError] errorType: $errorType")
                                keywordDetectorResultRunnable = Runnable {
                                    setState(SpeechRecognizerAggregatorInterface.State.ERROR)
                                    it.release()
                                    audioProvider.releaseAudioInputStream(keywordDetector)
                                }
                            }
                        })

                    executor.submit {
                        if(!result.get()) {
                            it.release()
                        }
                        isStartListeningWithTriggering.set(false)
                    }
                }
            } else {
                isStartListeningWithTriggering.set(false)
            }
        } else {
            Log.d(TAG, "[startListeningWithTrigger] failed - already executing")
        }
    }

    override fun startListening(
        keywordStartPosition: Long?,
        keywordEndPosition: Long?,
        keywordDetectPosition: Long?
    ) {
        Log.d(
            TAG,
            "[startListening] keywordStartPosition: $keywordStartPosition, keywordEndPosition: $keywordEndPosition, keywordDetectPosition: $keywordDetectPosition, state: $state, keywordDetectorState: $keywordDetectorState"
        )

        if (state == SpeechRecognizerAggregatorInterface.State.WAITING || keywordDetectorState == KeywordDetectorStateObserver.State.ACTIVE) {
            this.audioFormat = audioProvider.getFormat()
            this.keywordStartPosition = keywordStartPosition
            this.keywordEndPosition = keywordEndPosition
            isTriggerStoppingByStartListening = true
            stopTrigger()
            return
        } else if (state == SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH || state == SpeechRecognizerAggregatorInterface.State.SPEECH_START) {
            Log.w(TAG, "[startListening] Not allowed at $state")
            return
        }

        startListeningInternal(audioProvider.getFormat(), keywordStartPosition, keywordEndPosition, keywordDetectPosition)
    }

    private fun startListeningInternal(
        audioFormat: com.skt.nugu.sdk.core.interfaces.audio.AudioFormat,
        keywordStartPosition: Long?,
        keywordEndPosition: Long?,
        keywordDetectPosition: Long?
    ) {
//        if (speechProcessor.useSelfSource()) {
//            audioProvider.reset()
//        }

        val inputStream = audioProvider.acquireAudioInputStream(speechProcessor)
        if (inputStream != null) {
            val result = speechProcessor.start(inputStream, audioFormat, keywordStartPosition, keywordEndPosition, keywordDetectPosition)
            executor.submit {
                if(result.get() == false) {
                    setState(SpeechRecognizerAggregatorInterface.State.ERROR)
                }
            }
        } else {
            Log.e(TAG, "[startListeningInternal] Failed to open AudioInputStream")
            setState(SpeechRecognizerAggregatorInterface.State.ERROR)
        }
    }

    override fun stop() {
        when (state) {
            SpeechRecognizerAggregatorInterface.State.WAITING -> keywordDetector.stopDetect()
            SpeechRecognizerAggregatorInterface.State.WAKEUP,
            SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
            SpeechRecognizerAggregatorInterface.State.SPEECH_START,
            SpeechRecognizerAggregatorInterface.State.SPEECH_END -> speechProcessor.stop()
            else -> {
            }
        }
    }

    override fun stopListening() {
        Log.d(TAG, "[stopListening]")
        when (state) {
            SpeechRecognizerAggregatorInterface.State.WAKEUP,
            SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
            SpeechRecognizerAggregatorInterface.State.SPEECH_START -> speechProcessor.stop()
            else -> {
            }
        }
    }

    override fun stopTrigger() {
        Log.d(TAG, "[stopTrigger]")
        if (state == SpeechRecognizerAggregatorInterface.State.WAITING || keywordDetectorState == KeywordDetectorStateObserver.State.ACTIVE) {
            keywordDetector.stopDetect()
        }
    }

    override fun addListener(listener: SpeechRecognizerAggregatorInterface.OnStateChangeListener) {
        listeners.add(listener)
        listener.onStateChanged(state)
    }

    override fun removeListener(listener: SpeechRecognizerAggregatorInterface.OnStateChangeListener) {
        listeners.remove(listener)
    }

    private fun setState(state: SpeechRecognizerAggregatorInterface.State) {
        Log.d(TAG, "[setState] ${this.state} / $state")
        if (this.state == state) {
            return
        }

        this.state = state

        notifyOnStateChange(state)
    }

    private fun notifyOnStateChange(state: SpeechRecognizerAggregatorInterface.State) {
        Log.d(TAG, "[notifyOnStateChange] state: $state")
        for (listener in listeners) {
            listener.onStateChanged(state)
        }
    }

    private class KeywordDetectorInput(audioInputStream: SharedDataStream) : com.skt.nugu.keensense.AudioInput {
        private val reader = audioInputStream.createReader()

        override fun getPosition(): Long = reader.position()

        override fun read(audioBuffer: ByteBuffer, sizeInBytes: Int): Int {
            return reader.read(audioBuffer, 0, sizeInBytes)
        }

        fun release() {
            reader.close()
        }
    }
}