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

import com.skt.nugu.sdk.core.capabilityagents.impl.DefaultASRAgent
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.network.event.AsrRecognizeEventPayload

class SpeechToTextConverterImpl(
    override var enablePartialResult: Boolean,
    override var enableSpeakerRecognition: Boolean,
    private val enableServerEpd: Boolean,
    private val messageSender: MessageSender,
    private val audioEncoder: Encoder
) : SpeechToTextConverterInterface {
    companion object {
        private const val TAG = "SpeechToTextConverterImpl"
    }

    private val observers = HashSet<SpeechToTextConverterInterface.OnStateChangedListener>()
    private var state: SpeechToTextConverterInterface.State =
        SpeechToTextConverterInterface.State.INACTIVE

    private var senderThread: RecognizeSenderThread? = null
    private var eventObserver: ASRAgentInterface.OnResultListener? = null
    private var dialogRequestId: String? = null

    override fun startSpeechToTextConverter(
        reader: SharedDataStream.Reader,
        format: AudioFormat,
        context: String,
        payload: ExpectSpeechPayload?,
        observer: ASRAgentInterface.OnResultListener
    ) {
        if (state == SpeechToTextConverterInterface.State.ACTIVE) {
            Logger.w(TAG, "[startSpeechToTextConverter] Not allowed in ACTIVE")
            return
        }

        Logger.d(TAG, "[startSpeechToTextConverter] speech to text")
        eventObserver = observer
        dialogRequestId = null
        senderThread = object : RecognizeSenderThread(
            reader,
            format,
            messageSender,
            object : RecognizeSenderObserver {
                override fun onStop() {
                    observer.onCancel()
                    setStateToInActive()
                }

                override fun onError(e: Exception) {
                    Logger.e(TAG, "[startSpeechToTextConverter] onError", e)
                    observer.onError(ASRAgentInterface.ErrorType.ERROR_NETWORK)
                    setStateToInActive()
                }
            },
            audioEncoder
        ) {
            override fun createRecognizeEvent(): EventMessageRequest {
                val event = this@SpeechToTextConverterImpl.createRecognizeEvent(context, payload)
                dialogRequestId = event.dialogRequestId
                return event
            }

            override fun onFinished() {
                senderThread = null
            }
        }.apply {
            start()
        }

        setState(SpeechToTextConverterInterface.State.ACTIVE)
    }

    override fun stopSpeechToTextConverter() {
        senderThread?.requestStop()
    }

    fun finishSpeechToTextConverter() {
        senderThread?.requestFinish()
    }

    fun createRecognizeEvent(context: String, payload: ExpectSpeechPayload?): EventMessageRequest = EventMessageRequest.Builder(
        context,
        DefaultASRAgent.RECOGNIZE.namespace,
        DefaultASRAgent.RECOGNIZE.name,
        AbstractASRAgent.VERSION
    ).payload(
        AsrRecognizeEventPayload(
            codec = AsrRecognizeEventPayload.CODEC_SPEEX,
            sessionId = payload?.sessionId,
            playServiceId = payload?.playServiceId,
            property = payload?.property,
            domainTypes = payload?.domainTypes,
            endpointing = if(enableServerEpd) AsrRecognizeEventPayload.ENDPOINTING_SERVER else AsrRecognizeEventPayload.ENDPOINTING_CLIENT,
            encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE
        ).toJsonString()
    ).build()

    override fun getState(): SpeechToTextConverterInterface.State = state

    override fun setState(state: SpeechToTextConverterInterface.State) {
        if (this.state == state) {
            return
        }

        this.state = state

        notifyObservers(state)
    }

    override fun addObserver(observer: SpeechToTextConverterInterface.OnStateChangedListener) {
        observers.add(observer)
    }

    override fun removeObserver(observer: SpeechToTextConverterInterface.OnStateChangedListener) {
        observers.remove(observer)
    }

    private fun notifyObservers(state: SpeechToTextConverterInterface.State) {
        for (observer in observers) {
            observer.onStateChanged(state)
        }
    }

    override fun notifyPartialResult(result: String?) {
        eventObserver?.onPartialResult(result ?: "")
    }

    override fun notifyCompleteResult(result: String?) {
        eventObserver?.onCompleteResult(result ?: "")
        setStateToInActive()
    }

    override fun notifyEmptyResult() {
        eventObserver?.onNoneResult()
        setStateToInActive()
    }

    override fun notifyError(description: String?) {
        // issue-77 : hack to prevent to call twice onError()
        if(DefaultASRAgent.DESCRIPTION_NOTIFY_ERROR_RESPONSE_TIMEOUT != description) {
            eventObserver?.onError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
        }
        setStateToInActive()
    }

    private fun setStateToInActive() {
        eventObserver = null
        setState(SpeechToTextConverterInterface.State.INACTIVE)
    }

    fun getDialogRequestId(): String? = dialogRequestId
}