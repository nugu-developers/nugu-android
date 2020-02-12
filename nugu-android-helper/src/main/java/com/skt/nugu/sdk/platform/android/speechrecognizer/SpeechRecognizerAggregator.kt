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
package com.skt.nugu.sdk.platform.android.speechrecognizer

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.skt.nugu.keensense.KeywordDetectorObserver
import com.skt.nugu.keensense.KeywordDetectorStateObserver
import com.skt.nugu.keensense.tyche.TycheKeywordDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class SpeechRecognizerAggregator(
    private var keywordResource: KeywordResources,
    private val speechProcessor: SpeechProcessorDelegate,
    private val audioProvider: AudioProvider,
    private val handler: Handler = Handler(Looper.getMainLooper())
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

    fun changeKeywordResource(keywordResource: KeywordResources) {
        this.keywordResource = keywordResource
    }

    private val keywordDetector: TycheKeywordDetector = TycheKeywordDetector()

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = HashSet<SpeechRecognizerAggregatorInterface.OnStateChangeListener>()

    private var state = SpeechRecognizerAggregatorInterface.State.STOP

    // should be run in executor.
    private var keywordDetectorResultRunnable: Runnable? = null

    private var isTriggerStoppingByStartListening = false

    private var audioFormat: AudioFormat? = null
    private var keywordStartPosition: Long? = null
    private var keywordEndPosition: Long? = null
    private var keywordDetectPosition: Long? = null

    init {
        keywordDetector.addDetectorStateObserver(object : KeywordDetectorStateObserver {
            override fun onStateChange(state: KeywordDetectorStateObserver.State) {
                Log.d(TAG, "[KeywordDetectorStateObserver::onStateChange] state: $state")
                executor.submit {
                    if (state == KeywordDetectorStateObserver.State.ACTIVE) {
                        setState(SpeechRecognizerAggregatorInterface.State.WAITING)
                    } else {
                        val resultRunnable = keywordDetectorResultRunnable
                        if (resultRunnable != null) {
                            Log.d(TAG, "[executeOnKeywordDetectorStateChanged] start runnable")
                            keywordDetectorResultRunnable = null
                            resultRunnable.run()
                        } else {
                            Log.e(TAG, "[executeOnKeywordDetectorStateChanged] keywordDetectorResultRunnable is null!!!")
                        }
                        isTriggerStoppingByStartListening = false
                    }
                }
            }
        })

        speechProcessor.addListener(object : AudioEndPointDetector.OnStateChangedListener {
            override fun onStateChanged(state: AudioEndPointDetector.State) {
                Log.d(
                    TAG,
                    "[AudioEndPointDetectorStateObserverInterface::onStateChange] state: $state"
                )
                executor.submit {
                    val aggregatorState = when (state) {
                        AudioEndPointDetector.State.EXPECTING_SPEECH -> {
                            if (keywordDetector.getDetectorState() == KeywordDetectorStateObserver.State.ACTIVE) {
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

                    setState(aggregatorState)
                }
            }
        })
    }

    override fun startListeningWithTrigger() {
        executor.submit {
            if (keywordDetector.getDetectorState() == KeywordDetectorStateObserver.State.ACTIVE) {
                Log.w(TAG, "[startListeningWithTrigger] failed - already executing")
                return@submit
            }

            val inputStream = audioProvider.acquireAudioInputStream(keywordDetector)
            if (inputStream == null) {
                Log.w(TAG, "[startListeningWithTrigger] failed - null input stream")
                return@submit
            }

            KeywordDetectorInput(inputStream).let {
                val audioFormat = audioProvider.getFormat()
                val result = keywordDetector.startDetect(
                    it,
                    com.skt.nugu.keensense.AudioFormat(
                        audioFormat.sampleRateHz,
                        audioFormat.bitsPerSample,
                        audioFormat.numChannels
                    ),
                    TycheKeywordDetector.KeywordResources(keywordResource.netFilePath, keywordResource.searchFilePath),
                    object : KeywordDetectorObserver {
                        override fun onDetected() {
                            Log.d(
                                TAG,
                                "[onDetected] start: ${keywordDetector.getKeywordStartOffset()} , end : ${keywordDetector.getKeywordEndOffset()}"
                            )
                            keywordDetectorResultRunnable = Runnable {
                                setState(SpeechRecognizerAggregatorInterface.State.WAKEUP)
                                executeStartListeningInternal(
                                    audioProvider.getFormat(),
                                    keywordDetector.getKeywordStartOffset(),
                                    keywordDetector.getKeywordEndOffset(),
                                    keywordDetector.getKeywordDetectOffset()
                                )

                                // To prevent releasing audio input resources, release after startListening.
                                releaseInputResources()
                            }
                        }

                        override fun onStopped() {
                            Log.d(TAG, "[onStopped] $isTriggerStoppingByStartListening")
                            keywordDetectorResultRunnable = Runnable {
                                if (isTriggerStoppingByStartListening) {
                                    executeStartListeningInternal(
                                        audioFormat,
                                        keywordStartPosition,
                                        keywordEndPosition,
                                        keywordDetectPosition
                                    )
                                    isTriggerStoppingByStartListening = false

                                    // To prevent releasing audio input resources, release after startListening.
                                    releaseInputResources()
                                } else if (state == SpeechRecognizerAggregatorInterface.State.WAITING) {
                                    releaseInputResources()
                                    setState(SpeechRecognizerAggregatorInterface.State.STOP)
                                } else {
                                    releaseInputResources()
                                }
                            }
                        }

                        override fun onError(errorType: KeywordDetectorObserver.ErrorType) {
                            Log.d(TAG, "[onError] errorType: $errorType")
                            keywordDetectorResultRunnable = Runnable {
                                setState(SpeechRecognizerAggregatorInterface.State.ERROR)
                                releaseInputResources()
                            }
                        }

                        private fun releaseInputResources() {
                            it.release()
                            audioProvider.releaseAudioInputStream(keywordDetector)
                        }
                    })

                if (!result.get()) {
                    it.release()
                    Log.w(TAG, "[startListeningWithTrigger] failed - already executing ")
                } else {
                    Log.d(TAG, "[startListeningWithTrigger] start")
                }
            }
        }
    }

    override fun startListening(
        keywordStartPosition: Long?,
        keywordEndPosition: Long?,
        keywordDetectPosition: Long?
    ) {
        Log.d(
            TAG,
            "[startListening]"
        )
        executor.submit {
            Log.d(
                TAG,
                "[startListening] on executor - keywordStartPosition: $keywordStartPosition, keywordEndPosition: $keywordEndPosition, keywordDetectPosition: $keywordDetectPosition, state: $state, keywordDetectorState: ${keywordDetector.getDetectorState()}, isTriggerStoppingByStartListening: $isTriggerStoppingByStartListening"
            )
            when (state) {
                SpeechRecognizerAggregatorInterface.State.WAITING -> {
                    if(isTriggerStoppingByStartListening || keywordDetector.getDetectorState() == KeywordDetectorStateObserver.State.INACTIVE) {
                        Log.w(TAG, "[startListening] will be started after trigger stopped. skip request.")
                        return@submit
                    }

                    Log.d(TAG, "[startListening] will be started after trigger stopped.")
                    this.audioFormat = audioProvider.getFormat()
                    this.keywordStartPosition = keywordStartPosition
                    this.keywordEndPosition = keywordEndPosition
                    isTriggerStoppingByStartListening = true
                    executeStopTrigger()
                }
                SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
                SpeechRecognizerAggregatorInterface.State.SPEECH_START -> {
                    Log.w(TAG, "[startListening] Not allowed at $state")
                }
                else -> {
                    executeStartListeningInternal(
                        audioProvider.getFormat(),
                        keywordStartPosition,
                        keywordEndPosition,
                        keywordDetectPosition
                    )
                }
            }
        }
    }

    private fun executeStartListeningInternal(
        audioFormat: AudioFormat,
        keywordStartPosition: Long?,
        keywordEndPosition: Long?,
        keywordDetectPosition: Long?
    ) {
//        if (speechProcessor.useSelfSource()) {
//            audioProvider.reset()
//        }

        val inputStream = audioProvider.acquireAudioInputStream(speechProcessor)
        if (inputStream != null) {
            val result = speechProcessor.start(
                inputStream,
                audioFormat,
                keywordStartPosition,
                keywordEndPosition,
                keywordDetectPosition
            )
            if (result.get() == false) {
                setState(SpeechRecognizerAggregatorInterface.State.ERROR)
            }
        } else {
            Log.e(
                TAG,
                "[startListeningInternal] Failed to open AudioInputStream"
            )
            setState(SpeechRecognizerAggregatorInterface.State.ERROR)
        }
    }

    override fun stop() {
        Log.d(TAG, "[stop]")
        executor.submit {
            Log.d(TAG, "[stop] on executor")
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
    }

    override fun stopListening() {
        Log.d(TAG, "[stopListening]")
        executor.submit {
            Log.d(TAG, "[stopListening] on executor")
            when (state) {
                SpeechRecognizerAggregatorInterface.State.WAKEUP,
                SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
                SpeechRecognizerAggregatorInterface.State.SPEECH_START -> speechProcessor.stop()
                else -> {
                }
            }
        }
    }

    override fun stopTrigger() {
        Log.d(TAG, "[stopTrigger]")
        executor.submit {
            executeStopTrigger()
        }
    }

    private fun executeStopTrigger() {
        Log.d(TAG, "[executeStopTrigger]")
        if (state == SpeechRecognizerAggregatorInterface.State.WAITING) {
            keywordDetector.stopDetect()
        }
    }

    override fun addListener(listener: SpeechRecognizerAggregatorInterface.OnStateChangeListener) {
        Log.d(TAG, "[addListener] $listener")
        listeners.add(listener)

        val state = this.state
        handler.post {
            listener.onStateChanged(state)
        }
    }

    override fun removeListener(listener: SpeechRecognizerAggregatorInterface.OnStateChangeListener) {
        Log.d(TAG, "[removeListener] $listener")
        listeners.remove(listener)
    }

    override fun isActive(): Boolean = when (state) {
        SpeechRecognizerAggregatorInterface.State.WAITING,
        SpeechRecognizerAggregatorInterface.State.WAKEUP,
        SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
        SpeechRecognizerAggregatorInterface.State.SPEECH_START -> true
        else -> false
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
        val copyListeners = HashSet(listeners)

        handler.post {
            for (listener in copyListeners) {
                listener.onStateChanged(state)
            }
        }
    }

    private class KeywordDetectorInput(audioInputStream: SharedDataStream) :
        com.skt.nugu.keensense.AudioInput {
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