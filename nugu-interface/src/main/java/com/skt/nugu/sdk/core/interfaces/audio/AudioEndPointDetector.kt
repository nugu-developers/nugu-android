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
package com.skt.nugu.sdk.core.interfaces.audio

import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream

/**
 * Interface for Audio End Point Detector(EPD).
 *
 * (EPD : It is an algorithm that detects the start and end positions of speech utterances.)
 *
 * This detect start and end of speech from audio provided at [startDetector]
 */
interface AudioEndPointDetector {
    /**
     * Enum class of AudioEndPointDetector's state
     */
    enum class State {
        /**
         * enter to state which can listen
         */
        EXPECTING_SPEECH,
        /**
         * speaker start speech
         */
        SPEECH_START,

        /**
         * speaker end speech
         */
        SPEECH_END,
        /**
         * Do not speak for a period of time after EXPECTING_SPEECH or
         * speak too long after SPEECH_START
         */
        TIMEOUT,
        /**
         * stop detecting on active states(EXPECTING_SPEECH or SPEECH_START)
         */
        STOP,
        /**
         * occur error on active state(EXPECTING_SPEECH or SPEECH_START)
         */
        ERROR;

        /** Check if in active state or not
         * @return true if active state(EXPECTING_SPEECH or SPEECH_START), false otherwise
         */
        fun isActive(): Boolean = this == EXPECTING_SPEECH || this == SPEECH_START
    }

    /**
     * Listener to notified on [State] change
     */
    interface OnStateChangedListener {
        /**
         * Called when state changed
         *
         * @param state changed state
         */
        fun onStateChanged(state: State)
    }

    /**
     * Start end point detection.
     *
     * @param reader reader for audio source
     * @param audioFormat audio format for [reader]'s stream
     * @param silenceTimeoutSec the silence timeout at seconds
     */
    fun startDetector(
        reader: SharedDataStream.Reader,
        audioFormat: AudioFormat,
        silenceTimeoutSec: Int
    )

    /**
     * Stop current end point detection.
     */
    fun stopDetector()


    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: OnStateChangedListener)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: OnStateChangedListener)

    /**
     * Get speech start position at stream of reader provided at [startDetector]
     * @return start position: if exist, null: otherwise
     */
    fun getSpeechStartPosition(): Long?

    /**
     * Get speech end position at stream of reader provided at [startDetector]
     * @return end position: if exist, null: otherwise
     */
    fun getSpeechEndPosition(): Long?
}