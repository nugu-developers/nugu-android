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
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import kotlin.math.max

class DefaultClientSideSpeechProcessor(
    defaultAudioProvider: AudioProvider,
    private val speechToTextConverterEventObserver: ASRAgentInterface.OnResultListener,
    audioEncoder: Encoder,
    messageSender: MessageSender,
    override val endPointDetector: AudioEndPointDetector,
    defaultTimeoutMillis: Long
) : AbstractSpeechProcessor(defaultAudioProvider, defaultTimeoutMillis) {
    companion object {
        private const val TAG = "DefaultClientSideSpeechProcessor"
    }

    override val speechToTextConverter = SpeechToTextConverterImpl(
        enablePartialResult,
        includeWakeupBoundary,
        false,
        messageSender,
        audioEncoder
    )

    init {
        endPointDetector.addListener(this)
        speechToTextConverter.addObserver(this)
    }

    override fun onStateChanged(state: AudioEndPointDetector.State) {
        if (state == AudioEndPointDetector.State.SPEECH_START) {
            if (!startSpeechToTextConverter()) {
                endPointDetector.stopDetector()
            }
        }
        super.onStateChanged(state)
    }

    private fun startSpeechToTextConverter(): Boolean {
        val recognizeContext = context
        val inputStream = audioInputStream
        val inputFormat = audioFormat
        val inputWakeupBoundary = wakeupBoundary

        if (recognizeContext == null) {
            Logger.w(TAG, "[startSpeechToTextConverter] failed: context is null")
            return false
        }

        if (inputStream == null) {
            Logger.w(TAG, "[startSpeechToTextConverter] failed: audioInputStream is null")
            return false
        }

        if (inputFormat == null) {
            Logger.w(TAG, "[startSpeechToTextConverter] failed: inputFormat is null")
            return false
        }

        val sendPosition: Long?
        val sendWakeupBoundary: WakeupBoundary?
        if (includeWakeupBoundary && inputWakeupBoundary != null) {
            // 화자인식 ON && wakeup에 의한 시작 : wakeword 음성도 전송한다.
            // send stream before 500ms to ready at server ASR
            val offsetPosition = 500 * inputFormat.getBytesPerMillis()
            val wakewordStartPosition = inputWakeupBoundary.startSamplePosition * inputFormat.getBytesPerSample()
            sendPosition = max(wakewordStartPosition - offsetPosition, 0)
            val sendSamplePosition = sendPosition / inputFormat.getBytesPerSample()
            sendWakeupBoundary = WakeupBoundary(
                inputWakeupBoundary.detectSamplePosition - sendSamplePosition,
                inputWakeupBoundary.startSamplePosition - sendSamplePosition,
                inputWakeupBoundary.endSamplePosition - sendSamplePosition
            )
        } else {
            // 화자인식 OFF : SPEECH_START 부터 전송한다.
            sendPosition = endPointDetector.getSpeechStartPosition()
            sendWakeupBoundary = null
        }

        Logger.d(
            TAG,
            "[startSpeechToTextConverter] send position : $sendPosition / send wakeupBoundary $sendWakeupBoundary / wakeupBoundary : $wakeupBoundary / bytesPerMillis : ${inputFormat.getBytesPerMillis()}"
        )

        speechToTextConverter.startSpeechToTextConverter(
            inputStream.createReader(sendPosition),
            inputFormat,
            recognizeContext,
            sendWakeupBoundary,
            payload,
            speechToTextConverterEventObserver
        )
        return true
    }

    override fun notifyResultSOS() {
        // TODO : handling exception
    }

    override fun notifyResultEOS() {
        // TODO : handling exception
    }
}