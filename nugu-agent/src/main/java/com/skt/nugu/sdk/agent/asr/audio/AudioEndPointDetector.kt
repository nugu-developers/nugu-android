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
package com.skt.nugu.sdk.agent.asr.audio

import com.skt.nugu.sdk.agent.sds.SharedDataStream

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
         * Called when ready to listening(the epd start)
         */
        fun onExpectingSpeech()

        /**
         * Called when the speech start
         * @param eventPosition the position which speech start detected at reader, null if not available.
         */
        fun onSpeechStart(eventPosition: Long?)

        /**
         * Called when the speech end
         * @param eventPosition the position which speech end detected at reader, null if not available
         */
        fun onSpeechEnd(eventPosition: Long?)

        /**
         * Called when the timeout occur
         * @param type the type for timeout
         */
        fun onTimeout(type: TimeoutType)

        /**
         * Called when stopped by [stopDetector]
         */
        fun onStop()

        /**
         * Called when occur error
         * @param type the type for error
         * @param e the exception, only exist if [type] is [ErrorType.ERROR_EXCEPTION]
         */
        fun onError(type: ErrorType, e: Exception? = null)
    }

    enum class TimeoutType {
        LISTENING_TIMEOUT,
        SPEECH_TIMEOUT
    }

    enum class ErrorType {
        ERROR_EPD_ENGINE,
        ERROR_AUDIO_INPUT,
        ERROR_EXCEPTION
    }

    /**
     * Start end point detection.
     *
     * @param reader reader for audio source
     * @param audioFormat audio format for [reader]'s stream
     * @param timeoutInSeconds the silence timeout in seconds
     * @param maxDurationInSeconds the allowed maximum speech duration from SPEECH_START to SPEECH_END in seconds (default = 10sec)
     * @param pauseLengthInMilliseconds the inter-breath time which determine speech end in milliseconds(default = 700ms)
     * @return true: success to start, false: otherwise.
     */
    fun startDetector(
        reader: SharedDataStream.Reader,
        audioFormat: AudioFormat,
        timeoutInSeconds: Int,
        maxDurationInSeconds: Int = 10,
        pauseLengthInMilliseconds: Int = 700
    ): Boolean

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
}