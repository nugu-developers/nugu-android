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
package com.skt.nugu.sdk.agent.asr

import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import java.util.concurrent.Future

/**
 * The public interface for ASRAgent
 */
interface ASRAgentInterface {
    /**
     * State of ASRAgent
     */
    enum class State {
        /**
         * Not recognizing : Initial state or
         */
        IDLE,
        /**
         * Recognizing: waiting to start speech for speech recognition
         */
        EXPECTING_SPEECH,

        /**
         * Recognizing: speech recognition started, but not speech started yet.
         */
        LISTENING,

        /**
         * Recognizing: streaming speech data to recognize.
         */
        RECOGNIZING,

        /**
         * Recognizing: streaming finished, waiting to get complete recognized result.
         */
        BUSY;

        /**
         * Return whether recognizing state or not
         * @return true: recognizing state, false: otherwise
         */
        fun isRecognizing(): Boolean = when(this) {
            IDLE -> false
            else -> true
        }
    }

    /**
     * The error type for ASR result
     * @see [OnResultListener.onError]
     */
    enum class ErrorType {
        /**
         * the network error
         */
        ERROR_NETWORK,
        /**
         * the audio input error
         */
        ERROR_AUDIO_INPUT,
        /**
         * the unknown error
         */
        ERROR_UNKNOWN,
        /**
         * listening timeout.
         */
        ERROR_LISTENING_TIMEOUT,
        /**
         * response timeout.
         */
        ERROR_RESPONSE_TIMEOUT
    }

    /**
     * Interface of a listener to be called when there has been changes of state
     */
    interface OnStateChangeListener {
        /**
         * Called when state changed
         * @param state changed state
         */
        fun onStateChanged(state: State)
    }

    /**
     * Event(Result) Listener for speech to text(STT)
     */
    interface OnResultListener {
        /**
         * Called when there is no matched result.
         */
        fun onNoneResult()
        /**
         * Called when received a partial recognized text.
         * @param result recognized text
         */
        fun onPartialResult(result: String)
        /**
         * Called when received a complete recognized text.
         *
         * @param result recognized text
         */
        fun onCompleteResult(result: String)
        /**
         * Called when occur error on recognizing.
         * @param type reason for error
         */
        fun onError(type: ErrorType)

        /**
         * Called when canceled.
         */
        fun onCancel()
    }

    /**
     * Interface of a listener to be called when there has been an change of multi-turn state
     * @see [com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface]
     */
    interface OnMultiturnListener {
        /**
         * Called when multi-turn state changed.
         * @param enabled true if multi-turn state, otherwise.
         */
        fun onMultiturnStateChanged(enabled: Boolean)
    }

    /**
     * start recognition
     * @param audioInputStream the audio input stream which is used for recognition, if null
     * @param audioFormat the audio format for [audioInputStream].
     * @param wakewordStartPosition the start position of wakeword at [audioInputStream], null if not exist.
     * @param wakewordEndPosition the end position of wakeword at [audioInputStream], null if not exist.
     * @param wakewordDetectPosition the detect position of wakeword at [audioInputStream], null if not exist.
     * @return true: if start, false: failed to start(already recognizing or others).
     */
    fun startRecognition(
        audioInputStream: SharedDataStream? = null,
        audioFormat: AudioFormat? = null,
        wakewordStartPosition: Long? = null,
        wakewordEndPosition: Long? = null,
        wakewordDetectPosition: Long? = null
    ): Future<Boolean>

    /**
     * Stop current recognition
     */
    fun stopRecognition()

    /** Add a listener to be called when a state changed.
     * @param listener the state listener that added
     */
    fun addOnStateChangeListener(listener: OnStateChangeListener)
    /**
     * Remove a listener
     * @param listener the state listener that removed
     */
    fun removeOnStateChangeListener(listener: OnStateChangeListener)

    /** Add a listener to be called when receive a result for ASR.
     * @param listener the result listener that added
     */
    fun addOnResultListener(listener: OnResultListener)
    /**
     * Remove a listener
     * @param listener the result listener that removed
     */
    fun removeOnResultListener(listener: OnResultListener)

    /** Add a listener to be called when multi-turn state changed.
     * @param listener the multi-turn listener that added
     */
    fun addOnMultiturnListener(listener: OnMultiturnListener)
    /**
     * Remove a listener
     * @param listener the multi-turn listener that removed
     */
    fun removeOnMultiturnListener(listener: OnMultiturnListener)
}