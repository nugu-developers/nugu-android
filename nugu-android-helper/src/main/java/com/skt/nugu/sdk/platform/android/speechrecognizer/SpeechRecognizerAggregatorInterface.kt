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

/**
 * Utility interface for speech recognition
 *
 * Integrate functionality of keyword detector and speech processor and input management.
 *
 * the speech processor has in charge of three roles:
 * * EPD : refer to [com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector]
 * * STT : convert speech to text
 * * IP(Input Processing) : send request to NUGU
 */
interface SpeechRecognizerAggregatorInterface {

    /**
     * State of [SpeechRecognizerAggregatorInterface]
     */
    enum class State {
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

    /**
     * Start recognizing
     *
     * Start keyword detector first.
     * After detection, start end point detector
     */
    fun startListeningWithTrigger()

    /**
     * Start keyword detector.
     * @param keywordStartPosition start position of keyword at input
     * @param keywordEndPosition end position of keyword at input
     * @param keywordDetectPosition detect position of keyword at input
     */
    fun startListening(keywordStartPosition: Long? = null, keywordEndPosition: Long? = null, keywordDetectPosition: Long? = null)

    /**
     * Stop Recognizing
     */
    fun stop()

    /**
     * Stop end point detector
     */
    fun stopListening()

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
}