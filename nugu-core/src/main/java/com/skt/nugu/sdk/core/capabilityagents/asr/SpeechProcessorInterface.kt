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
package com.skt.nugu.sdk.core.capabilityagents.asr

import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream

interface SpeechProcessorInterface {
    enum class State {
        EXPECTING_SPEECH,
        SPEECH_START,
        SPEECH_END,
        STOP,
        TIMEOUT;

        fun isActive(): Boolean = when (this) {
            EXPECTING_SPEECH,
            SPEECH_START -> true
            else -> false
        }
    }

    interface OnStateChangeListener {
        fun onStateChanged(state: State)
    }

    val defaultAudioProvider: AudioProvider?
    var enablePartialResult: Boolean
    var enableSpeakerRecognition: Boolean

    fun startProcessor(audioInputStream: SharedDataStream?, audioFormat: AudioFormat?, context: String?, wakewordStartPosition: Long?, wakewordEndPosition: Long?, payload: ExpectSpeechPayload?)
    fun stopProcessor()

    fun addListener(listener: OnStateChangeListener)
    fun removeListener(listener: OnStateChangeListener)

    fun notifyResult(state: String, result: String?)
    fun notifyError(description: String)
    fun release()
}