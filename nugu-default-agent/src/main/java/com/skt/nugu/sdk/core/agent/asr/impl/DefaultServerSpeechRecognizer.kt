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
package com.skt.nugu.sdk.core.agent.asr.impl

import com.skt.nugu.sdk.core.agent.asr.ExpectSpeechPayload
import com.skt.nugu.sdk.core.agent.asr.SpeechRecognizer
import com.skt.nugu.sdk.core.agent.asr.WakeupBoundary
import com.skt.nugu.sdk.core.agent.DefaultASRAgent
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.agent.asr.AsrNotifyResultPayload
import com.skt.nugu.sdk.core.agent.asr.AsrRecognizeEventPayload
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class DefaultServerSpeechRecognizer(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val audioEncoder: Encoder,
    private val messageSender: MessageSender,
    private val defaultTimeoutMillis: Long
) : SpeechRecognizer, InputProcessor {
    companion object {
        private const val TAG = "DefaultServerSpeechRecognizer"
    }

    private data class RecognizeRequest(
        val senderThread: SpeechRecognizeAttachmentSenderThread,
        val eventMessage: EventMessageRequest,
        val resultListener: ASRAgentInterface.OnResultListener?
    )

    override var enablePartialResult: Boolean = true
        set(value) {
            if (field == value) {
                return
            }

            field = value
        }

    private val listeners = HashSet<SpeechRecognizer.OnStateChangeListener>()
    private var state: SpeechRecognizer.State = SpeechRecognizer.State.STOP
    private var currentRequest: RecognizeRequest? = null

    private val timeoutScheduler = ScheduledThreadPoolExecutor(1)
    private var timeoutFuture: ScheduledFuture<*>? = null

    override fun start(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        context: String,
        wakeupBoundary: WakeupBoundary?,
        payload: ExpectSpeechPayload?,
        resultListener: ASRAgentInterface.OnResultListener?
    ) {
        Logger.d(
            TAG,
            "[startProcessor] wakeupBoundary:$wakeupBoundary, currentInputPosition: ${audioInputStream.getPosition()}"
        )

        val sendPositionAndWakeupBoundary =
            computeSendPositionAndWakeupBoundary(audioFormat, wakeupBoundary)

        val eventMessage = EventMessageRequest.Builder(
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
                endpointing = AsrRecognizeEventPayload.ENDPOINTING_SERVER,
                encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE,
                wakeupBoundary = sendPositionAndWakeupBoundary.second
            ).toJsonString()
        ).build()


        if (messageSender.sendMessage(eventMessage)) {
            val thread = createSenderThread(
                audioInputStream,
                audioFormat,
                sendPositionAndWakeupBoundary,
                eventMessage
            )
            currentRequest = RecognizeRequest(
                thread, eventMessage, resultListener
            )
            thread.start()

            setState(SpeechRecognizer.State.EXPECTING_SPEECH)
            timeoutFuture?.cancel(true)
            timeoutFuture = timeoutScheduler.schedule({
                handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT)
            }, defaultTimeoutMillis, TimeUnit.SECONDS)
        } else {
            Logger.w(TAG, "[startProcessor] failed to send recognize event")
        }
    }

    private fun computeSendPositionAndWakeupBoundary(
        audioFormat: AudioFormat,
        wakeupBoundary: WakeupBoundary?
    ): Pair<Long?, WakeupBoundary?> {
        val sendPosition: Long?
        val sendWakeupBoundary: WakeupBoundary?
        if (wakeupBoundary != null) {
            // 화자인식 ON && wakeup에 의한 시작 : wakeword 음성도 전송한다.
            // send stream before 500ms to ready at server ASR
            val offsetPosition = 500 * audioFormat.getBytesPerMillis()
            val wakewordStartPosition =
                wakeupBoundary.startSamplePosition * audioFormat.getBytesPerSample()
            sendPosition = max(wakewordStartPosition - offsetPosition, 0)
            val sendSamplePosition = sendPosition / audioFormat.getBytesPerSample()
            sendWakeupBoundary = WakeupBoundary(
                wakeupBoundary.detectSamplePosition - sendSamplePosition,
                wakeupBoundary.startSamplePosition - sendSamplePosition,
                wakeupBoundary.endSamplePosition - sendSamplePosition
            )
        } else {
            // 화자인식 OFF :
            sendPosition = null
            sendWakeupBoundary = null
        }

        return Pair(sendPosition, sendWakeupBoundary)
    }

    private fun createSenderThread(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        sendPositionAndWakeupBoundary: Pair<Long?, WakeupBoundary?>,
        eventMessage: EventMessageRequest
    ): SpeechRecognizeAttachmentSenderThread {
        Logger.d(
            TAG,
            "[createSenderThread] sendPositionAndWakeupBoundary :${sendPositionAndWakeupBoundary} / bytesPerMillis : ${audioFormat.getBytesPerMillis()}"
        )

        return SpeechRecognizeAttachmentSenderThread(
            audioInputStream.createReader(sendPositionAndWakeupBoundary.first),
            audioFormat,
            messageSender,
            object : SpeechRecognizeAttachmentSenderThread.RecognizeSenderObserver {
                override fun onFinish() {
                    // ignore
                }

                override fun onStop() {
                    handleCancel()
                    sendStopRecognizeEvent(eventMessage)
                }

                override fun onError(errorType: ASRAgentInterface.ErrorType) {
                    handleError(errorType)
                }
            },
            audioEncoder,
            eventMessage
        )
    }

    override fun stop() {
        currentRequest?.senderThread?.requestStop()
    }

    override fun addListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.remove(listener)
    }

    override fun notifyResult(state: String, result: String?) {
        val enumState = AsrNotifyResultPayload.State.values().find { it.name == state }
        val payload =
            AsrNotifyResultPayload(
                enumState!!,
                result
            )
        val request = currentRequest ?: return

        Logger.d(TAG, "[notifyResult] state: $state, result: $result, listener: $request")

        val resultListener: ASRAgentInterface.OnResultListener? = request.resultListener

        when (payload.state) {
            AsrNotifyResultPayload.State.PARTIAL -> {
                resultListener?.onPartialResult(payload.result ?: "")
            }
            AsrNotifyResultPayload.State.COMPLETE -> {
                resultListener?.onCompleteResult(payload.result ?: "")
            }
            AsrNotifyResultPayload.State.NONE -> {
                resultListener?.onNoneResult()
            }
            AsrNotifyResultPayload.State.ERROR -> {
                resultListener?.onError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
            }
            AsrNotifyResultPayload.State.FA -> {
                // TODO : Impl
            }
            AsrNotifyResultPayload.State.SOS -> {
                timeoutFuture?.cancel(true)
                timeoutFuture = null
                setState(SpeechRecognizer.State.SPEECH_START)
            }
            AsrNotifyResultPayload.State.EOS -> {
                timeoutFuture?.cancel(true)
                timeoutFuture = null
                request.senderThread.requestFinish()
                onSendEventFinished(request.eventMessage.dialogRequestId)
                setState(SpeechRecognizer.State.SPEECH_END)
            }
        }
    }

    private fun setState(state: SpeechRecognizer.State) {
        Logger.d(TAG, "[setState] prev: ${this.state} / next: $state")

        this.state = state

        notifyObservers(state)
    }

    private fun notifyObservers(state: SpeechRecognizer.State) {
        listeners.forEach {
            it.onStateChanged(state)
        }
    }

    private fun sendStopRecognizeEvent(request: EventMessageRequest): Boolean {
        Logger.d(TAG, "[sendStopRecognizeEvent] $this")
        return request.let {
            messageSender.sendMessage(
                EventMessageRequest.Builder(
                    it.context,
                    AbstractASRAgent.NAMESPACE,
                    DefaultASRAgent.EVENT_STOP_RECOGNIZE,
                    AbstractASRAgent.VERSION
                ).referrerDialogRequestId(it.dialogRequestId).build()
            )
        }
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveResponse(dialogRequestId: String, header: Header) {
        val request = currentRequest
        if (request == null) {
            Logger.e(TAG, "[onReceiveResponse] invalid : request is null")
            return
        }

        if (dialogRequestId == request.eventMessage.dialogRequestId) {
            Logger.e(
                TAG,
                "[onReceiveResponse] invalid : (receive: $dialogRequestId, current: ${request.eventMessage.dialogRequestId})"
            )
            return
        }

        if (header.namespace != AbstractASRAgent.NAMESPACE) {
            Logger.d(TAG, "[onReceiveResponse] $header")
            handleFinish()
        }
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        handleError(ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT)
    }

    private fun handleFinish() {
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }

    private fun handleError(errorType: ASRAgentInterface.ErrorType) {
        currentRequest?.resultListener?.onError(errorType)
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }

    private fun handleCancel() {
        currentRequest?.resultListener?.onCancel()
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }
}