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

import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.capabilityagents.impl.DefaultASRAgent
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.event.AsrRecognizeEventPayload
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface

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

    override val speechToTextConverter =
        SpeechToTextConverterImpl(enablePartialResult, enableSpeakerRecognition, messageSender, audioEncoder)

    init {
        endPointDetector.addListener(this)
        speechToTextConverter.addObserver(this)
    }

    override fun onStateChanged(state: AudioEndPointDetector.State) {
        if (state == AudioEndPointDetector.State.SPEECH_START) {
            startSpeechToTextConverter()
        }
        super.onStateChanged(state)
    }

    private fun startSpeechToTextConverter() {
        Logger.d(TAG, "[startSpeechToTextConverter]")
        audioInputStream?.let { inputStream ->
            var sendPosition = if (enableSpeakerRecognition) {
                // 화자인식 ON : wakeword 음성도 전송한다.
                wakewordStartPosition ?: endPointDetector.getSpeechStartPosition()
            } else {
                // 화자인식 OFF : SPEECH_START 부터 전송한다.
                endPointDetector.getSpeechStartPosition()
            }

            audioFormat?.let {
                speechToTextConverter.startSpeechToTextConverter(
                    inputStream.createReader(sendPosition),
                    it,
                    speechToTextConverterEventObserver
                )
            }
        }
    }

    override fun notifyResultSOS() {
        // TODO : handling exception
    }

    override fun notifyResultEOS() {
        // TODO : handling exception
    }

    inner class SpeechToTextConverterImpl(
        enablePartialResult: Boolean,
        enableSpeakerRecognition: Boolean,
        messageSender: MessageSender,
        audioEncoder: Encoder
    ) :
        AbstractSpeechToTextConverter(enablePartialResult, enableSpeakerRecognition, messageSender, audioEncoder) {
        override fun createRecognizeEvent(): EventMessageRequest =
            EventMessageRequest(
                UUIDGeneration.shortUUID().toString(),
                UUIDGeneration.timeUUID().toString(),
                context ?: "",
                DefaultASRAgent.RECOGNIZE.namespace,
                DefaultASRAgent.RECOGNIZE.name,
                AbstractASRAgent.VERSION,
                AsrRecognizeEventPayload(
                    codec = AsrRecognizeEventPayload.CODEC_SPEEX,
                    sessionId = payload?.sessionId,
                    playServiceId = payload?.playServiceId,
                    property = payload?.property,
                    domainTypes = payload?.domainTypes,
                    endpointing = AsrRecognizeEventPayload.ENDPOINTING_CLIENT,
                    encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE
                ).toJsonString()
            )
    }
}