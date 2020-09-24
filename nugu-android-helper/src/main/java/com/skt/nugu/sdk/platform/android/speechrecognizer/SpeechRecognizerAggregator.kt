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
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class SpeechRecognizerAggregator(
    private val keywordDetector: KeywordDetector?,
    private val speechProcessor: SpeechProcessorDelegate,
    private val audioProvider: AudioProvider,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    threadFactory: ThreadFactory = Executors.defaultThreadFactory()
) : SpeechRecognizerAggregatorInterface {
    companion object {
        // const val TAG = "SpeechRecognizerAggregator"
        // To remove error, shorten TAG
        private const val TAG = "SpeechRecognizerAg"
    }

    private val executor = Executors.newSingleThreadExecutor(threadFactory)
    private val listeners = HashSet<SpeechRecognizerAggregatorInterface.OnStateChangeListener>()

    private var state = SpeechRecognizerAggregatorInterface.State.STOP

    // should be run in executor.
    private var keywordDetectorResultRunnable: Runnable? = null
    private var keywordDetectorInactivationRunnable: Runnable? = null

    private var isTriggerStoppingByStartListening = false

    private var audioFormat: AudioFormat? = null
    private var wakeupInfo: WakeupInfo? = null
    private var epdParam: EndPointDetectorParam? = null
    private var startListeningCallback: ASRAgentInterface.StartRecognitionCallback? = null
    private var keywordDetectorState = KeywordDetector.State.INACTIVE
    private var speechProcessorState = AudioEndPointDetector.State.STOP

    init {
        keywordDetector?.addOnStateChangeListener(object : KeywordDetector.OnStateChangeListener {
            override fun onStateChange(state: KeywordDetector.State) {
                Log.d(TAG, "[KeywordDetector::onStateChange] state: $state")
                executor.submit {
                    keywordDetectorState = state

                    if (state == KeywordDetector.State.ACTIVE) {
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

                        keywordDetectorInactivationRunnable?.run()
                        keywordDetectorInactivationRunnable = null
                    }
                }
            }
        })

        speechProcessor.addListener(object : AudioEndPointDetector.OnStateChangedListener {
            override fun onExpectingSpeech() {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.EXPECTING_SPEECH

                    if (keywordDetector?.getDetectorState() == KeywordDetector.State.ACTIVE) {
                        keywordDetector.stopDetect()
                    }
                    isTriggerStoppingByStartListening = false

                    updateState(AudioEndPointDetector.State.EXPECTING_SPEECH , SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH)
                }
            }

            override fun onSpeechStart(eventPosition: Long?) {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.SPEECH_START
                    updateState(AudioEndPointDetector.State.SPEECH_START , SpeechRecognizerAggregatorInterface.State.SPEECH_START)
                }
            }

            override fun onSpeechEnd(eventPosition: Long?) {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.SPEECH_END
                    updateState(AudioEndPointDetector.State.SPEECH_END , SpeechRecognizerAggregatorInterface.State.SPEECH_END)
                }
            }

            override fun onTimeout(type: AudioEndPointDetector.TimeoutType) {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.TIMEOUT
                    updateState(AudioEndPointDetector.State.TIMEOUT , SpeechRecognizerAggregatorInterface.State.TIMEOUT)
                }
            }

            override fun onStop() {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.STOP
                    updateState(AudioEndPointDetector.State.STOP , SpeechRecognizerAggregatorInterface.State.STOP)
                }
            }

            override fun onError(type: AudioEndPointDetector.ErrorType, e: Exception?) {
                executor.submit {
                    speechProcessorState = AudioEndPointDetector.State.ERROR
                    updateState(AudioEndPointDetector.State.ERROR , SpeechRecognizerAggregatorInterface.State.ERROR)
                }
            }

            private fun updateState(state: AudioEndPointDetector.State, aggregatorState: SpeechRecognizerAggregatorInterface.State) {
                if (!state.isActive()) {
                    audioProvider.releaseAudioInputStream(speechProcessor)
                    if(keywordDetectorState == KeywordDetector.State.ACTIVE) {
                        keywordDetectorInactivationRunnable = Runnable {
                            setState(aggregatorState)
                        }
                    } else {
                        setState(aggregatorState)
                    }
                } else {
                    setState(aggregatorState)
                }
            }
        })
    }

    override fun startListeningWithTrigger(
        epdParam: EndPointDetectorParam?,
        triggerCallback: SpeechRecognizerAggregatorInterface.TriggerCallback?,
        listeningCallback: ASRAgentInterface.StartRecognitionCallback?
    ) {
        if(keywordDetector == null) {
            Log.w(TAG, "[startListeningWithTrigger] ignored: keywordDetector is null")
            listeningCallback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
            return
        }

        executor.submit {
            if (keywordDetector.getDetectorState() == KeywordDetector.State.ACTIVE) {
                Log.w(TAG, "[startListeningWithTrigger] failed - already executing")
                return@submit
            }

            if(isActive()) {
                Log.w(TAG, "[startListeningWithTrigger] failed - active state($state)")
                return@submit
            }

            val inputStream = audioProvider.acquireAudioInputStream(keywordDetector)
            if (inputStream == null) {
                Log.w(TAG, "[startListeningWithTrigger] failed - null input stream")
                return@submit
            }

            val audioFormat = audioProvider.getFormat()
            val isStarted = keywordDetector.startDetect(inputStream, audioFormat, object: KeywordDetector.DetectorResultObserver {
                override fun onDetected(wakeupInfo: WakeupInfo) {
                    Log.d(
                        TAG,
                        "[onDetected] wakeupInfo: $wakeupInfo"
                    )

                    keywordDetectorResultRunnable = Runnable {
                        setState(SpeechRecognizerAggregatorInterface.State.WAKEUP)
                        triggerCallback?.onTriggerFinished(wakeupInfo)

                        executeStartListeningInternal(
                            audioProvider.getFormat(),
                            wakeupInfo,
                            epdParam,
                            listeningCallback
                        )

                        // To prevent releasing audio input resources, release after startListening.
                        releaseInputResources()
                    }
                }

                override fun onStopped() {
                    Log.d(TAG, "[onStopped] $isTriggerStoppingByStartListening")
                    keywordDetectorResultRunnable = Runnable {

                        if (isTriggerStoppingByStartListening) {
                            triggerCallback?.onTriggerFinished(null)

                            executeStartListeningInternal(
                                audioFormat,
                                this@SpeechRecognizerAggregator.wakeupInfo,
                                this@SpeechRecognizerAggregator.epdParam,
                                this@SpeechRecognizerAggregator.startListeningCallback
                            )
                            isTriggerStoppingByStartListening = false

                            // To prevent releasing audio input resources, release after startListening.
                            releaseInputResources()
                        } else if (state == SpeechRecognizerAggregatorInterface.State.WAITING) {
                            releaseInputResources()
                            setState(SpeechRecognizerAggregatorInterface.State.STOP)
                            triggerCallback?.onTriggerFinished(null)
                        } else {
                            releaseInputResources()
                            triggerCallback?.onTriggerFinished(null)
                        }
                    }
                }

                override fun onError(errorType: KeywordDetector.DetectorResultObserver.ErrorType) {
                    Log.d(TAG, "[onError] errorType: $errorType")
                    keywordDetectorResultRunnable = Runnable {
                        setState(SpeechRecognizerAggregatorInterface.State.ERROR)
                        triggerCallback?.onTriggerFinished(null)
                        releaseInputResources()
                    }
                }

                private fun releaseInputResources() {
                    audioProvider.releaseAudioInputStream(keywordDetector)
                }
            })

            if(isStarted) {
                triggerCallback?.onTriggerStarted(inputStream, audioFormat)
            } else {
                triggerCallback?.onTriggerFinished(null)
            }
        }
    }

    override fun startListening(wakeupInfo: WakeupInfo?, epdParam: EndPointDetectorParam?, callback: ASRAgentInterface.StartRecognitionCallback?) {
        Log.d(
            TAG,
            "[startListening]"
        )
        executor.submit {
            Log.d(
                TAG,
                "[startListening] on executor - wakeupInfo: $wakeupInfo, state: $state, keywordDetectorState: ${keywordDetector?.getDetectorState()}, isTriggerStoppingByStartListening: $isTriggerStoppingByStartListening"
            )
            when (state) {
                SpeechRecognizerAggregatorInterface.State.WAITING -> {
                    if(isTriggerStoppingByStartListening || keywordDetector?.getDetectorState() == KeywordDetector.State.INACTIVE) {
                        Log.w(TAG, "[startListening] will be started after trigger stopped. skip request.")
                        callback?.onError(UUIDGeneration.toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
                        return@submit
                    }

                    Log.d(TAG, "[startListening] will be started after trigger stopped.")
                    this.audioFormat = audioProvider.getFormat()
                    this.wakeupInfo = wakeupInfo
                    this.epdParam = epdParam
                    this.startListeningCallback = callback
                    isTriggerStoppingByStartListening = true
                    keywordDetector?.stopDetect()
                }
                SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH,
                SpeechRecognizerAggregatorInterface.State.SPEECH_START -> {
                    Log.w(TAG, "[startListening] Not allowed at $state")
                }
                else -> {
                    executeStartListeningInternal(
                        audioProvider.getFormat(),
                        wakeupInfo,
                        epdParam,
                        callback
                    )
                }
            }
        }
    }

    private fun executeStartListeningInternal(
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        epdParam : EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?
    ) {
//        if (speechProcessor.useSelfSource()) {
//            audioProvider.reset()
//        }

        val inputStream = audioProvider.acquireAudioInputStream(speechProcessor)
        if (inputStream != null) {
            val countDownLatch = CountDownLatch(1)
            speechProcessor.start(
                inputStream,
                audioFormat,
                wakeupInfo,
                epdParam,
                object: ASRAgentInterface.StartRecognitionCallback {
                    override fun onSuccess(dialogRequestId: String) {
                        Logger.d(TAG, "[executeStartListeningInternal] onSuccess")
                        countDownLatch.countDown()
                        callback?.onSuccess(dialogRequestId)
                    }

                    override fun onError(
                        dialogRequestId: String,
                        errorType: ASRAgentInterface.StartRecognitionCallback.ErrorType
                    ) {
                        Logger.d(TAG, "[executeStartListeningInternal] onError: $errorType")
                        setState(SpeechRecognizerAggregatorInterface.State.ERROR)
                        countDownLatch.countDown()
                        callback?.onError(dialogRequestId, errorType)
                    }
                }
            )
            countDownLatch.await()
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
            keywordDetector?.stopDetect()
            speechProcessor.stop(true)
        }
    }

    override fun stopListening(cancel: Boolean) {
        Log.d(TAG, "[stopListening]")
        executor.submit {
            Log.d(TAG, "[stopListening] on executor")
            speechProcessor.stop(cancel)
        }
    }

    override fun stopTrigger() {
        if(keywordDetector == null) {
            Log.w(TAG, "[stopTrigger] ignored: keywordDetector is null")
            return
        }

        Log.d(TAG, "[stopTrigger]")
        executor.submit {
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
}