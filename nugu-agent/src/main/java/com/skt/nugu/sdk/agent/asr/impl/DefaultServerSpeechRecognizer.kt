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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.*
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.Preferences
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet
import kotlin.math.max

class DefaultServerSpeechRecognizer(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val audioEncoder: Encoder,
    private val messageSender: MessageSender
) : SpeechRecognizer, InputProcessor {
    companion object {
        private const val TAG = "DefaultServerSpeechRecognizer"

        private val dateFormat: DateFormat by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
        }
    }

    private data class RecognizeRequest(
        val senderThread: SpeechRecognizeAttachmentSenderThread,
        override val eventMessage: EventMessageRequest,
        val expectSpeechParam: DefaultASRAgent.ExpectSpeechDirectiveParam?,
        val resultListener: ASRAgentInterface.OnResultListener?
    ) : SpeechRecognizer.Request {
        override val attributeKey: String? = expectSpeechParam?.directive?.header?.messageId
        var cancelCause: ASRAgentInterface.CancelCause? = null
        val shouldBeHandledResult = HashSet<String>()
        var receiveResponse = false
        var call: Call? = null

        val eventMessageHeader = with(eventMessage) {
            Header(dialogRequestId, messageId, name, namespace, version, referrerDialogRequestId)
        }
    }

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
        expectSpeechDirectiveParam: DefaultASRAgent.ExpectSpeechDirectiveParam?,
        attribute: DialogAttribute?,
        epdParam: EndPointDetectorParam,
        resultListener: ASRAgentInterface.OnResultListener?
    ): SpeechRecognizer.Request? {
        Logger.d(
            TAG,
            "[startProcessor] wakeupInfo:$wakeupInfo, currentInputPosition: ${audioInputStream.getPosition()}"
        )
        if(currentRequest != null) {
            return null
        }

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
                    ), power
                )
            }
        } else {
            sendPosition = null
            payloadWakeupInfo = null
        }

        val referrerDialogRequestId = expectSpeechDirectiveParam?.directive?.header?.dialogRequestId
        val payloadAttribute = expectSpeechDirectiveParam?.directive?.payload?.let {
            ExpectSpeechPayload.getDialogAttribute(it)
        } ?: attribute

        val eventMessage = EventMessageRequest.Builder(
            context,
            DefaultASRAgent.RECOGNIZE.namespace,
            DefaultASRAgent.RECOGNIZE.name,
            DefaultASRAgent.VERSION.toString()
        ).payload(
            AsrRecognizeEventPayload(
                codec = audioEncoder.getCodecName().uppercase(Locale.getDefault()),
                playServiceId = payloadAttribute?.playServiceId,
                domainTypes = payloadAttribute?.domainTypes,
                endpointing = AsrRecognizeEventPayload.ENDPOINTING_SERVER,
                encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE,
                wakeup = payloadWakeupInfo,
                asrContext =  payloadAttribute?.asrContext?.let { JsonParser.parseString(it).asJsonObject },
                timeout = AsrRecognizeEventPayload.Timeout(
                    epdParam.timeoutInSeconds * 1000L,
                    epdParam.maxDurationInSeconds * 1000L,
                    10 * 1000L // TODO : fixed at 10sec now.
                )
            ).toJsonString()
        ).referrerDialogRequestId(referrerDialogRequestId ?: "")
            .build()

        val call = messageSender.newCall(
            eventMessage, hashMapOf("Last-Asr-Event-Time" to Preferences.get("Last-Asr-Event-Time").toString())
        )

        val thread = createSenderThread(
            audioInputStream,
            audioFormat,
            sendPosition,
            eventMessage
        )

        val recognizeRequest =
            RecognizeRequest(
                thread, eventMessage, expectSpeechDirectiveParam, resultListener
            )
        recognizeRequest.call = call
        currentRequest = recognizeRequest
        call.enqueue(object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                Logger.w(TAG, "[startProcessor] failed to send recognize event")
                val errorType = when (status.error) {
                    Status.StatusError.OK,
                    Status.StatusError.TIMEOUT -> return
                    Status.StatusError.NETWORK -> ASRAgentInterface.ErrorType.ERROR_NETWORK
                    else -> ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                }
                handleError(errorType, recognizeRequest.eventMessageHeader)
            }

            override fun onSuccess(request : MessageRequest) {
                thread.start()

                setState(SpeechRecognizer.State.EXPECTING_SPEECH, recognizeRequest)
                timeoutFuture?.cancel(true)
                timeoutFuture = timeoutScheduler.schedule({
                    handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT, recognizeRequest.eventMessageHeader)
                }, epdParam.timeoutInSeconds.toLong(), TimeUnit.SECONDS)
            }

            override fun onResponseStart(request: MessageRequest) {
                Preferences.set("Last-Asr-Event-Time", dateFormat.format(Calendar.getInstance().time))
            }
        })

        return recognizeRequest

       /* if (messageSender.sendMessage(eventMessage)) {
            val thread = createSenderThread(
                audioInputStream,
                audioFormat,
                sendPosition,
                eventMessage
            )
            val request =
                RecognizeRequest(
                    thread, eventMessage, expectSpeechDirectiveParam, resultListener
                )
            currentRequest = request
            thread.start()

            setState(SpeechRecognizer.State.EXPECTING_SPEECH, request)
            timeoutFuture?.cancel(true)
            timeoutFuture = timeoutScheduler.schedule({
                handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT)
            }, epdParam.timeoutInSeconds.toLong(), TimeUnit.SECONDS)

            return request
        } else {
            Logger.w(TAG, "[startProcessor] failed to send recognize event")
            return null
        }*/
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
                    handleError(errorType, with(eventMessage) {
                        Header(dialogRequestId, messageId, name, namespace, version, referrerDialogRequestId)
                    })
                }
            },
            audioEncoder,
            eventMessage
        )
    }

    override fun stop(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        if(cancel) {
            currentRequest?.cancelCause = cause
            currentRequest?.senderThread?.requestStop()
            currentRequest?.call?.cancel()
        } else {
            currentRequest?.senderThread?.requestFinish()
        }
    }

    override fun isRecognizing(): Boolean = currentRequest != null

    override fun addListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SpeechRecognizer.OnStateChangeListener) {
        listeners.remove(listener)
    }

    override fun notifyResult(
        directive: Directive,
        payload: AsrNotifyResultPayload
    ) {
        val request = currentRequest ?: return

        Logger.d(TAG, "[notifyResult] $payload, listener: $request")

        val resultListener: ASRAgentInterface.OnResultListener? = request.resultListener

        when (payload.state) {
            AsrNotifyResultPayload.State.PARTIAL -> {
                resultListener?.onPartialResult(payload.result ?: "", directive.header)
            }
            AsrNotifyResultPayload.State.COMPLETE -> {
                resultListener?.onCompleteResult(payload.result ?: "", directive.header)
            }
            AsrNotifyResultPayload.State.NONE -> {
                resultListener?.onNoneResult(directive.header)
            }
            AsrNotifyResultPayload.State.ERROR -> {
                request.senderThread.requestStop()
                handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN, directive.header)
            }
            AsrNotifyResultPayload.State.FA -> {
                // TODO : Impl
            }
            AsrNotifyResultPayload.State.SOS -> {
                timeoutFuture?.cancel(true)
                timeoutFuture = null
                setState(SpeechRecognizer.State.SPEECH_START, request)
            }
            AsrNotifyResultPayload.State.EOS -> {
                timeoutFuture?.cancel(true)
                timeoutFuture = null
                request.senderThread.requestFinish()
                onSendEventFinished(request.eventMessage.dialogRequestId)
                setState(SpeechRecognizer.State.SPEECH_END, request)
            }
        }

        synchronized(request) {
            request.shouldBeHandledResult.remove(directive.getMessageId())
            if (request.shouldBeHandledResult.isEmpty() && request.receiveResponse) {
                handleFinish()
            }
        }
    }

    private fun setState(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        Logger.d(TAG, "[setState] prev: ${this.state} / next: $state")

        this.state = state

        notifyObservers(state, request)
    }

    private fun notifyObservers(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        listeners.forEach {
            it.onStateChanged(state, request)
        }
    }

    private fun sendStopRecognizeEvent(request: EventMessageRequest): Boolean {
        Logger.d(TAG, "[sendStopRecognizeEvent] $this")
        return messageSender.newCall(
            EventMessageRequest.Builder(
                request.context,
                DefaultASRAgent.NAMESPACE,
                DefaultASRAgent.EVENT_STOP_RECOGNIZE,
                DefaultASRAgent.VERSION.toString()
            ).referrerDialogRequestId(request.dialogRequestId).build()
        ).enqueue(null)
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

        if (dialogRequestId != request.eventMessage.dialogRequestId) {
            Logger.e(
                TAG,
                "[onReceiveResponse] invalid : (receive: $dialogRequestId, current: ${request.eventMessage.dialogRequestId})"
            )
            return false
        }

        val receiveResponse = directives.filter { it.header.namespace != DefaultASRAgent.NAMESPACE }.any()

        return synchronized(request) {
            if (receiveResponse) {
                if (request.shouldBeHandledResult.isEmpty()) {
                    Logger.d(TAG, "[onReceiveResponse] receive response : no result should be handled, handleFinish()")
                    handleFinish()
                } else {
                    Logger.d(TAG, "[onReceiveResponse] receive response : exist result should be handled, pend handling.")
                    request.receiveResponse = true
                }
                true
            } else {
                directives.filter { it.header.namespace == DefaultASRAgent.NAMESPACE && it.header.name == DefaultASRAgent.NAME_NOTIFY_RESULT }
                    .forEach {
                        request.shouldBeHandledResult.add(it.getMessageId())
                    }
                Logger.d(
                    TAG,
                    "[onReceiveResponse] receive asr response : ${request.shouldBeHandledResult.size}"
                )
                false
            }
        }
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        currentRequest?.let {
            if(it.eventMessage.messageId == dialogRequestId) {
                handleError(ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT, it.eventMessageHeader)
            }
        }
    }

    private fun handleFinish() {
        currentRequest?.let {
            currentRequest = null
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun handleError(errorType: ASRAgentInterface.ErrorType, header: Header) {
        currentRequest?.let {
            it.resultListener?.onError(errorType, header)
            currentRequest = null
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun handleCancel() {
        currentRequest?.let {
            it.resultListener?.onCancel(it.cancelCause ?: ASRAgentInterface.CancelCause.LOCAL_API, it.eventMessageHeader)
            currentRequest = null
            setState(SpeechRecognizer.State.STOP, it)
        }
    }
}