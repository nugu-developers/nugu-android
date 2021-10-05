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
import com.skt.nugu.sdk.core.interfaces.common.EventCallback
import com.skt.nugu.sdk.core.interfaces.message.Header

/**
 * The public interface for ASRAgent
 */
interface ASRAgentInterface {
    sealed class State {
        /**
         * Not recognizing : Initial state or
         */
        object IDLE : State() {
            override fun isRecognizing(): Boolean = false
            override val name: String = "IDLE"

        }

        /**
         * Recognizing: waiting to start speech for speech recognition
         */
        object EXPECTING_SPEECH : State() {
            override val name: String = "EXPECTING_SPEECH"
        }

        /**
         * Recognizing: speech recognition started, but not speech started yet.
         */
        data class LISTENING(val initiator: Initiator) : State() {
            override val name: String = "LISTENING"
        }

        /**
         * Recognizing: streaming speech data to recognize.
         */
        object RECOGNIZING : State() {
            override val name: String = "RECOGNIZING"
        }

        /**
         * Recognizing: streaming finished, waiting to get complete recognized result.
         */
        object BUSY : State() {
            override val name: String = "BUSY"
        }

        /**
         * Return whether recognizing state or not
         * @return true: recognizing state, false: otherwise
         */
        open fun isRecognizing(): Boolean = true
        abstract val name: String
    }

    enum class Initiator {
        /**
         * recognition initiated by wakeup
         */
        WAKE_UP_WORD,

        /**
         * recognition initiated by button press and hold
         */
        PRESS_AND_HOLD,

        /**
         * recognition initiated by button tap
         */
        TAP,

        /**
         * recognition initiated by ASR.EXPECT_SPEECH directive
         */
        EXPECT_SPEECH,

        /**
         * recognition initiated by EARSET
         */
        EARSET
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
     * The cause for cancel
     */
    enum class CancelCause {
        /**
         * Caused by client call
         */
        LOCAL_API,

        /**
         * Caused by too low power for wakeup
         */
        WAKEUP_POWER,

        /**
         * Caused by loss focus
         */
        LOSS_FOCUS,

        /**
         * Caused by session closed
         */
        // TODO : Deprecate
        SESSION_CLOSED
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
         * @param header the request header
         */
        fun onNoneResult(header: Header)
        /**
         * Called when received a partial recognized text.
         * @param header the request header
         * @param result recognized text
         */
        fun onPartialResult(result: String, header: Header)
        /**
         * Called when received a complete recognized text.
         * @param header the request header
         * @param result recognized text
         */
        fun onCompleteResult(result: String, header: Header)
        /**
         * Called when occur error on recognizing.
         * @param type reason for error
         * @param header the request header
         * @param allowEffectBeep whether allow beep play or not
         */
        fun onError(type: ErrorType, header: Header, allowEffectBeep: Boolean = true)

        /**
         * Called when canceled.
         * @param cause the cancel cause
         * @param header the request header
         */
        fun onCancel(cause: CancelCause, header: Header)
    }

    /**
     * Interface of a listener to be called when there has been an change of multi-turn state
     */
    interface OnMultiturnListener {
        /**
         * Called when multi-turn state changed.
         * @param enabled true if multi-turn state, otherwise.
         */
        fun onMultiturnStateChanged(enabled: Boolean)
    }

    interface StartRecognitionCallback : EventCallback<StartRecognitionCallback.ErrorType> {
        enum class ErrorType {
            ERROR_CANNOT_START_RECOGNIZER,
            ERROR_ALREADY_RECOGNIZING,
            ERROR_TAKE_TOO_LONG_START_RECOGNITION,
            ERROR_UNKNOWN
        }
    }

    /**
     * start recognition
     * @param audioInputStream the audio input stream which is used for recognition, if null
     * @param audioFormat the audio format for [audioInputStream].
     * @param wakeupInfo the wakeup info(boundary & word) causing this recognition. The boundary should be relative position for [audioInputStream],
     * If the recognition was not invoked by wakeup, set to null.
     * @param param the params for EPD
     * @param callback the callback for request
     * @param initiator the initiator causing recognition
     */
    fun startRecognition(
        audioInputStream: SharedDataStream? = null,
        audioFormat: AudioFormat? = null,
        wakeupInfo: WakeupInfo? = null,
        param: EndPointDetectorParam? = null,
        callback: StartRecognitionCallback? = null,
        initiator: Initiator
    )

    /**
     * Stop current recognition
     */
    fun stopRecognition(cancel: Boolean = true, cause: CancelCause = CancelCause.LOCAL_API)

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

    /**
     * Interface of a callback to attach a set of request headers
     */
    interface OnHeaderAttachingCallback {
        /**
         * Returns the value corresponding to the specified field
         * @param the header name
         * @return the header value
         */
        fun getHeaders() : Map<String, String>?
    }
}