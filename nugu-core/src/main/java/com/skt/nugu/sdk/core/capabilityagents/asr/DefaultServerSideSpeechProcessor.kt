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
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import com.skt.nugu.sdk.core.network.event.AsrRecognizeEventPayload
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DefaultServerSideSpeechProcessor(
    defaultAudioProvider: AudioProvider,
    private val speechToTextConverterEventObserver: ASRAgentInterface.OnResultListener,
    audioEncoder: Encoder,
    messageSender: MessageSender,
    defaultTimeoutMillis: Long
) : AbstractSpeechProcessor(defaultAudioProvider, defaultTimeoutMillis) {
    companion object {
        private const val TAG = "DefaultServerSideSpeechProcessor"
    }

    override val endPointDetector = EndPointDetectorImpl()
    override val speechToTextConverter =
        SpeechToTextConverterImpl(enablePartialResult, enableSpeakerRecognition, messageSender, audioEncoder)

    private val timeoutScheduler = ScheduledThreadPoolExecutor(1)
    private var timeoutFuture: ScheduledFuture<*>? = null

    init {
        endPointDetector.addListener(this)
        speechToTextConverter.addObserver(this)
    }

    override fun notifyResultSOS() {
        timeoutFuture?.cancel(true)
        timeoutFuture = null
        endPointDetector.state = AudioEndPointDetector.State.SPEECH_START
    }

    override fun notifyResultEOS() {
        timeoutFuture?.cancel(true)
        timeoutFuture = null
        endPointDetector.state = AudioEndPointDetector.State.SPEECH_END
    }

    inner class EndPointDetectorImpl : AudioEndPointDetector {
        private val listeners = HashSet<AudioEndPointDetector.OnStateChangedListener>()

        var state: AudioEndPointDetector.State =
            AudioEndPointDetector.State.STOP
            set(value) {
                if (field == value) {
                    return
                }

                field = value
                notifyObservers(value)
            }

        override fun startDetector(
            reader: SharedDataStream.Reader,
            audioFormat: AudioFormat,
            timeoutInSeconds: Int
        ) {
            speechToTextConverter.startSpeechToTextConverter(reader, audioFormat, speechToTextConverterEventObserver)
            state = AudioEndPointDetector.State.EXPECTING_SPEECH
            timeoutFuture?.cancel(true)
            timeoutFuture = timeoutScheduler.schedule({
                state = AudioEndPointDetector.State.TIMEOUT
                speechToTextConverter.stopSpeechToTextConverter()
            }, timeoutInSeconds.toLong(), TimeUnit.SECONDS)
        }

        override fun stopDetector() {
            speechToTextConverter.stopSpeechToTextConverter()
        }

        override fun addListener(listener: AudioEndPointDetector.OnStateChangedListener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: AudioEndPointDetector.OnStateChangedListener) {
            listeners.remove(listener)
        }

        override fun getSpeechStartPosition(): Long? = null

        override fun getSpeechEndPosition(): Long? = null

        private fun notifyObservers(state: AudioEndPointDetector.State) {
            listeners.forEach {
                it.onStateChanged(state)
            }
        }
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
                    endpointing = AsrRecognizeEventPayload.ENDPOINTING_SERVER,
                    encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE
                ).toJsonString()
            )
    }
}