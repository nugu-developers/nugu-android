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

import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream

interface KeywordDetector {
    /**
     * Enum class for state
     */
    enum class State{
        /**
         * the detector is running
         */
        ACTIVE,
        /**
         * the detector is not running
         */
        INACTIVE
    }

    /**
     * Interface of a observer to be called when there has been an change of state
     */
    interface OnStateChangeListener {
        /** Called to notify an change of state
         * @param state the state
         */
        fun onStateChange(state: State)
    }

    /**
     * The observer for keyword detector
     */
    interface DetectorResultObserver {
        /**
         * Called when the keyword detected
         */
        fun onDetected(wakeupInfo: WakeupInfo)

        /**
         * Called when stopped by [stopDetect]
         */
        fun onStopped()

        /**
         * Called when occurs error
         */
        fun onError(errorType: ErrorType)

        /**
         * Enum class for error types
         */
        enum class ErrorType {
            /**
             * error caused by audio input
             */
            ERROR_AUDIO_INPUT,
            /**
             * unknown error
             */
            ERROR_UNKNOWN
        }
    }

    /**
     * Start Detector
     * @param inputStream the audio input used for detection
     * @param audioFormat the format of [inputStream]
     * @param observer the event observer
     * @return true: start detection, false: already started
     */
    fun startDetect(inputStream: SharedDataStream, audioFormat: AudioFormat, observer: DetectorResultObserver): Boolean

    /**
     * Stop Detector
     * If active state, [DetectorResultObserver.onStopped] and a state will be changed to [State.INACTIVE] abd [OnStateChangeListener.onStateChange] called.
     * Otherwise, nothing to happen.
     */
    fun stopDetect()

    /**
     * Get current state
     * @return the current state
     */
    fun getDetectorState(): State

    /**
     * Add observer
     * @param listener the state listener
     */
    fun addOnStateChangeListener(listener: OnStateChangeListener)

    /**
     * Remove observer
     * @param listener the state listener
     */
    fun removeDetectorStateObserver(listener: OnStateChangeListener)

    /**
     * Get supported audio formats.
     * Should use the audio format in this list at [startDetect].
     * If not supported format provided, [startDetect] return true. but, [DetectorResultObserver.onError] will be called.
     * @return supported audio formats
     */
    fun getSupportedFormats(): List<AudioFormat>
}