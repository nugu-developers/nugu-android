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
package com.skt.nugu.sdk.agent.asr.impl

import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.*
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class DefaultServerSpeechRecognizer(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val audioEncoder: Encoder,
    private val messageSender: MessageSender
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
        wakeupInfo: WakeupInfo?,
        payload: ExpectSpeechPayload?,
        epdParam: EndPointDetectorParam,
        resultListener: ASRAgentInterface.OnResultListener?
    ) {
        Logger.d(
            TAG,
            "[startProcessor] wakeupInfo:$wakeupInfo, currentInputPosition: ${audioInputStream.getPosition()}"
        )

        val payloadWakeupInfo: PayloadWakeup?
        val sendPosition: Long?
        if(wakeupInfo != null) {
            with(wakeupInfo) {
                val bytesPerSample = audioFormat.getBytesPerSample()
                val offsetPosition = 500 * audioFormat.getBytesPerMillis()
                sendPosition = max(boundary.startPosition - offsetPosition, 0)
                payloadWakeupInfo = PayloadWakeup(
                    word, PayloadWakeup.Boundary(
                        (boundary.startPosition - sendPosition) / bytesPerSample,
                        (boundary.endPosition - sendPosition) / bytesPerSample,
                        (boundary.detectPosition - sendPosition) / bytesPerSample
                    )
                )
            }
        } else {
            sendPosition = null
            payloadWakeupInfo = null
        }

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
                wakeup = payloadWakeupInfo
            ).toJsonString()
        ).build()

        if (messageSender.sendMessage(eventMessage)) {
            val listeningTimeoutSec: Long = if(payload != null) {
                (payload.timeoutInMilliseconds / 1000L)
            } else {
                epdParam.timeoutInSeconds.toLong()
            }

            val thread = createSenderThread(
                audioInputStream,
                audioFormat,
                sendPosition,
                eventMessage
            )
            currentRequest =
                RecognizeRequest(
                    thread, eventMessage, resultListener
                )
            thread.start()

            setState(SpeechRecognizer.State.EXPECTING_SPEECH)
            timeoutFuture?.cancel(true)
            timeoutFuture = timeoutScheduler.schedule({
                handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT)
            }, listeningTimeoutSec, TimeUnit.SECONDS)
        } else {
            Logger.w(TAG, "[startProcessor] failed to send recognize event")
        }
    }

    private fun createSenderThread(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        sendPosition: Long?,
        eventMessage: EventMessageRequest
    ): SpeechRecognizeAttachmentSenderThread {
        Logger.d(
            TAG,
            "[createSenderThread] sendPosition :$sendPosition / bytesPerMillis : ${audioFormat.getBytesPerMillis()}"
        )

        return SpeechRecognizeAttachmentSenderThread(
            audioInputStream.createReader(sendPosition),
            audioFormat,
            messageSender,
            object :
                SpeechRecognizeAttachmentSenderThread.RecognizeSenderObserver {
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

    override fun stop(cancel: Boolean) {
        if(cancel) {
            currentRequest?.senderThread?.requestStop()
        } else {
            currentRequest?.senderThread?.requestFinish()
        }
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
                request.senderThread.requestStop()
                handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
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

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        val request = currentRequest
        if (request == null) {
            Logger.e(TAG, "[onReceiveResponse] invalid : request is null")
            return false
        }

        if (dialogRequestId == request.eventMessage.dialogRequestId) {
            Logger.e(
                TAG,
                "[onReceiveResponse] invalid : (receive: $dialogRequestId, current: ${request.eventMessage.dialogRequestId})"
            )
            return false
        }

        val receiveResponse = directives.filter { it.header.namespace != AbstractASRAgent.NAMESPACE }.any()

        return if(receiveResponse) {
            Logger.d(TAG, "[onReceiveDirectives] receive response")
            handleFinish()
            true
        } else {
            Logger.d(TAG, "[onReceiveDirectives] receive asr response")
            false
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