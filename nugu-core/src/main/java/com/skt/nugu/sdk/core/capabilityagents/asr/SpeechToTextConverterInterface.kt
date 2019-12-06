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

import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream

interface SpeechToTextConverterInterface {
    enum class State {
        ACTIVE,
        INACTIVE
    }

    interface OnStateChangedListener {
        /**
         * Called when state changed.
         *
         * @param state changed state
         */
        fun onStateChanged(state: State)
    }

    var enablePartialResult: Boolean
    var enableSpeakerRecognition: Boolean

    fun startSpeechToTextConverter(reader: SharedDataStream.Reader, format: AudioFormat, context: String, wakeupBoundary: WakeupBoundary?, payload: ExpectSpeechPayload?, observer: ASRAgentInterface.OnResultListener)
    fun stopSpeechToTextConverter()

    fun getState(): State
    fun setState(state: State)

    fun addObserver(observer: OnStateChangedListener)
    fun removeObserver(observer: OnStateChangedListener)

    fun notifyPartialResult(result: String?)
    fun notifyCompleteResult(result: String?)
    fun notifyEmptyResult()
    fun notifyError(description: String?)
}