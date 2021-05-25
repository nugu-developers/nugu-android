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

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream

/**
 * Utility interface for speech recognition
 *
 * Integrate functionality of keyword detector and speech processor and input management.
 *
 * the speech processor has in charge of three roles:
 * * EPD : refer to [com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector]
 * * STT : convert speech to text
 * * IP(Input Processing) : send request to NUGU
 */
interface SpeechRecognizerAggregatorInterface {

    /**
     * State of [SpeechRecognizerAggregatorInterface]
     */
    enum class State {
        /**
         * initial or when receive response for asr request.
         */
        IDLE,
        /**
         * Keyword detection started
         */
        WAITING,
        /**
         * keyword detected
         */
        WAKEUP,
        /**
         * Listening started
         */
        EXPECTING_SPEECH,
        /**
         * Speech started
         */
        SPEECH_START,
        /**
         * Speech ended
         */
        SPEECH_END,
        /**
         * Stopped on detecting or listening
         */
        STOP,
        /**
         * Occur error on detecting or listening
         */
        ERROR,
        /**
         * Time out on detecting or listening
         */
        TIMEOUT
    }

    interface OnStateChangeListener {
        /**
         * Called when state changed
         *
         * @param state changed state
         */
        fun onStateChanged(state: State)
    }

    interface TriggerCallback {
        /**
         * Called when triggered
         *
         * @param inputStream the input stream for trigger
         * @param format the format for [inputStream]
         */
        fun onTriggerStarted(inputStream: SharedDataStream, format: AudioFormat)

        /**
         * Called  when keyword detected
         *
         * @param wakeupInfo the wakeupInfo
         * @return true: continue listening, false: not start listening
         */
        fun onTriggerDetected(wakeupInfo: WakeupInfo?): Boolean

        /**
         * Called when stopped
         */
        fun onTriggerStopped()

        /**
         * Called when occur error
         *
         * @param errorType the error type
         */
        fun onTriggerError(errorType: KeywordDetector.DetectorResultObserver.ErrorType)
    }

    /**
     * Start recognizing
     *
     * Start keyword detector first.
     * After detection, start end point detector
     */
    fun startListeningWithTrigger(epdParam: EndPointDetectorParam? = null, triggerCallback: TriggerCallback? = null, listeningCallback: ASRAgentInterface.StartRecognitionCallback? = null)

    /**
     * Start keyword detector.
     * @param wakeupInfo the wakeup info of wakeup word for input
     * @param epdParam the epd params
     * @param initiator the initiator
     */
    fun startListening(wakeupInfo: WakeupInfo? = null, epdParam: EndPointDetectorParam? = null, callback: ASRAgentInterface.StartRecognitionCallback? = null, initiator: ASRAgentInterface.Initiator)

    /**
     * Stop Recognizing
     */
    fun stop()

    /**
     * Stop end point detector.
     * If [cancel] is true, stop epd and cancel asr processing.
     * Otherwise, stop epd but finish asr processing.
     * @param cancel the flag to cancel or finish processing
     */
    fun stopListening(cancel: Boolean = true)

    /**
     * Stop keyword detector
     */
    fun stopTrigger()

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: OnStateChangeListener)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: OnStateChangeListener)

    /**
     * @return true: if recognizing, false: otherwise
     */
    fun isActive(): Boolean

    /**
     * Get state of [SpeechRecognizerAggregatorInterface]
     * @return The current state of [SpeechRecognizerAggregatorInterface]
     */
    fun getState(): State
}