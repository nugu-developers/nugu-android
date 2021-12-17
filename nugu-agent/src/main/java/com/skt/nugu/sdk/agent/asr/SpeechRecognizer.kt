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

import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

interface SpeechRecognizer {
    enum class State {
        EXPECTING_SPEECH,
        SPEECH_START,
        SPEECH_END,
        STOP;

        fun isActive(): Boolean = when (this) {
            EXPECTING_SPEECH,
            SPEECH_START -> true
            else -> false
        }
    }

    interface OnStateChangeListener {
        fun onStateChanged(state: State, request: Request)
    }

    var enablePartialResult: Boolean

    interface Request {
        val eventMessage: EventMessageRequest
        val attributeKey: String?
    }

    fun start(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        context: String,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: DefaultASRAgent.ExpectSpeechDirectiveParam?,
        attribute: DialogAttribute?,
        epdParam: EndPointDetectorParam,
        resultListener: ASRAgentInterface.OnResultListener?
    ): Request?

    fun stop(cancel: Boolean, cause: ASRAgentInterface.CancelCause)

    fun isRecognizing(): Boolean

    fun addListener(listener: OnStateChangeListener)
    fun removeListener(listener: OnStateChangeListener)

    fun notifyResult(directive: Directive, payload: AsrNotifyResultPayload)
}