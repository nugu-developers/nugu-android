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

import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.*
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.withLock
import kotlin.math.max

class DefaultClientSpeechRecognizer(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val audioEncoder: Encoder,
    private val messageSender: MessageSender,
    private val endPointDetector: AudioEndPointDetector
) : SpeechRecognizer, InputProcessor
, AudioEndPointDetector.OnStateChangedListener {
    companion object {
        private const val TAG = "DefaultClientSpeechRecognizer"

        // RFC 2616 specified: RFC 822, updated by RFC 1123 format with fixed GMT.
        private val dateFormat: DateFormat by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
        }
    }

    private data class RecognizeRequest (
        val audioInputStream: SharedDataStream,
        val audioFormat: AudioFormat,
        val wakeupInfo: WakeupInfo?,
        val sendPositionAndWakeupBoundary: Pair<Long?, WakeupBoundary?>,
        var senderThread: SpeechRecognizeAttachmentSenderThread?,
        override val eventMessage: EventMessageRequest,
        val expectSpeechParam: DefaultASRAgent.ExpectSpeechDirectiveParam?,
        val resultListener: ASRAgentInterface.OnResultListener?
    ): SpeechRecognizer.Request {
        override val attributeKey: String? = expectSpeechParam?.directive?.header?.messageId
        var errorTypeForCausingEpdStop: ASRAgentInterface.ErrorType? = null

        var stopByCancel: Boolean? = null
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

    private val startLock = ReentrantLock()
    private val stateLock = ReentrantLock()
    private var state = SpeechRecognizer.State.STOP
    private var epdState = AudioEndPointDetector.State.STOP

    private val listeners = HashSet<SpeechRecognizer.OnStateChangeListener>()

    private var currentRequest: RecognizeRequest? = null
    private var isEpdRunning: Boolean = false

    init {
        endPointDetector.addListener(this)
    }

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

//        val sendPositionAndWakeupBoundary =
//            computeSendPositionAndWakeupBoundary(audioFormat, wakeupBoundary)
        val payloadWakeup = if(wakeupInfo?.word != null){
            PayloadWakeup(wakeupInfo.word, null, wakeupInfo.power)
        } else {
            null
        }

        // Do not deliver wakeup info when client epd mode.
        val sendPositionAndWakeupBoundary = Pair(null, null)
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
                asrContext = payloadAttribute?.asrContext?.let { JsonParser.parseString(it).asJsonObject },
                endpointing = AsrRecognizeEventPayload.ENDPOINTING_CLIENT,
                encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE,
                wakeup = payloadWakeup
            ).toJsonString()
        ).referrerDialogRequestId(referrerDialogRequestId ?: "")
            .build()

        val request =
            RecognizeRequest(
                audioInputStream,
                audioFormat,
                wakeupInfo,
                sendPositionAndWakeupBoundary,
                null,
                eventMessage,
                expectSpeechDirectiveParam,
                resultListener
            )

        return startLock.withLock {
            currentRequest = request
            isEpdRunning = true
            if (!endPointDetector.startDetector(
                    audioInputStream.createReader(),
                    audioFormat,
                    epdParam.timeoutInSeconds,
                    epdParam.maxDurationInSeconds,
                    epdParam.pauseLengthInMilliseconds
                )
            ) {
                Logger.e(TAG, "[startProcessor] failed to start epd.")
                currentRequest = null
                isEpdRunning = false
                null
            } else {
                Logger.d(TAG, "[startProcessor] started")
                request
            }
        }
    }

    override fun stop(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        Logger.d(TAG, "[stop] epdState: ${epdState}, cancel: $cancel, cause: $cause, request: $currentRequest")
        val request = currentRequest ?: return

        request.stopByCancel = cancel
        request.cancelCause = cause


        if(cancel) {
            request.call?.cancel()
        }

        if (isEpdRunning) {
            endPointDetector.stopDetector()
        } else {
            // Don't need to wait until response timeout occurs.
            handleCancel()
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
                if(epdState.isActive()) {
                    request.errorTypeForCausingEpdStop = ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                    endPointDetector.stopDetector()
                } else {
                    request.senderThread?.requestStop()
                    handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN, directive.header)
                }
            }
            AsrNotifyResultPayload.State.FA -> {
                // TODO : Impl
            }
            else -> {
                // AsrNotifyResultPayload.State.SOS or AsrNotifyResultPayload.State.EOS
                // ignore server's wrong message
            }
        }

        synchronized(request) {
            request.shouldBeHandledResult.remove(directive.getMessageId())
            if (request.shouldBeHandledResult.isEmpty() && request.receiveResponse) {
                handleFinish()
            }
        }
    }

    override fun onExpectingSpeech() {
        Logger.d(TAG, "[AudioEndPointDetector::onExpectingSpeech]")
        val recognizeRequest = startLock.withLock {
            currentRequest
        }

        if(recognizeRequest == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onExpectingSpeech] null request. check this!!!")
            epdState = AudioEndPointDetector.State.EXPECTING_SPEECH
            return
        }

        messageSender.newCall(
            recognizeRequest.eventMessage,
            hashMapOf("Last-Asr-Event-Time" to Preferences.get("Last-Asr-Event-Time").toString())
        ).apply {
            recognizeRequest.call = this
            if(!this.enqueue(object: MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                        Logger.d(TAG, "[onFailure] request: $request, status: $status")
                        if(recognizeRequest == currentRequest && status.error == Status.StatusError.TIMEOUT && recognizeRequest.senderThread != null) {
                            // if sender thread is working, we handle a timeout error as unknown error.
                            // this occur when some attachment sent too late(after timeout).
                            recognizeRequest.errorTypeForCausingEpdStop = ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                            endPointDetector.stopDetector()
                        } else {
                            recognizeRequest.errorTypeForCausingEpdStop = when (status.error) {
                                Status.StatusError.OK /** cancel, no error **/ ,
                                Status.StatusError.TIMEOUT /** Nothing to do because handle on [onResponseTimeout] **/ ,
                                Status.StatusError.UNKNOWN -> /** Same as return false of [enqueue] **/ return
                                Status.StatusError.NETWORK -> ASRAgentInterface.ErrorType.ERROR_NETWORK
                                Status.StatusError.UNAUTHENTICATED -> ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                            }
                            endPointDetector.stopDetector()
                        }
                    }

                    override fun onSuccess(request: MessageRequest) {
                        Logger.d(TAG, "[onSuccess] request: $request")
                    }

                    override fun onResponseStart(request: MessageRequest) {
                        Logger.d(TAG, "[onResponseStart] request: $request")
                        Preferences.set("Last-Asr-Event-Time", dateFormat.format(Calendar.getInstance().time))
                    }
                })) {
                recognizeRequest.errorTypeForCausingEpdStop = ASRAgentInterface.ErrorType.ERROR_NETWORK
                endPointDetector.stopDetector()
            }
        }
        epdState = AudioEndPointDetector.State.EXPECTING_SPEECH
        setState(SpeechRecognizer.State.EXPECTING_SPEECH, recognizeRequest)
    }

    override fun onSpeechStart(eventPosition: Long?) {
        Logger.d(TAG, "[AudioEndPointDetector::onSpeechStart] eventPosition:$eventPosition")
        val request = startLock.withLock {
            currentRequest
        }

        if(request == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onSpeechStart] null request. check this!!!")
            epdState = AudioEndPointDetector.State.SPEECH_START
            return
        }

        startSpeechSenderThread(request, eventPosition)
        epdState = AudioEndPointDetector.State.SPEECH_START
        setState(SpeechRecognizer.State.SPEECH_START, request)
    }

    override fun onSpeechEnd(eventPosition: Long?) {
        Logger.d(TAG, "[AudioEndPointDetector::onSpeechEnd] eventPosition:$eventPosition")
        val request = startLock.withLock {
            currentRequest
        }

        if(request == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onSpeechEnd] null request. check this!!!")
            epdState = AudioEndPointDetector.State.SPEECH_END
            return
        }

        isEpdRunning = false
        request.senderThread?.requestFinish()
        epdState = AudioEndPointDetector.State.SPEECH_END
        setState(SpeechRecognizer.State.SPEECH_END, request)
    }

    override fun onTimeout(type: AudioEndPointDetector.TimeoutType) {
        Logger.d(TAG, "[AudioEndPointDetector::onTimeout] type:$type")
        val request = startLock.withLock {
            currentRequest
        }

        if(request == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onTimeout] null request. check this!!!")
            epdState = AudioEndPointDetector.State.TIMEOUT
            return
        }

        isEpdRunning = false
        when(type) {
            AudioEndPointDetector.TimeoutType.LISTENING_TIMEOUT -> {
                request.senderThread?.requestStop()
                handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT, request.eventMessageHeader)
                epdState = AudioEndPointDetector.State.TIMEOUT
            }
            AudioEndPointDetector.TimeoutType.SPEECH_TIMEOUT -> {
                request.senderThread?.requestFinish()
                epdState = AudioEndPointDetector.State.TIMEOUT
                setState(SpeechRecognizer.State.SPEECH_END, request)
            }
        }
    }

    override fun onStop() {
        Logger.d(TAG, "[AudioEndPointDetector::onStop]")
        val request = startLock.withLock {
            currentRequest
        }

        if(request == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onStop] null request. check this!!!")
            epdState = AudioEndPointDetector.State.STOP
            return
        }

        isEpdRunning = false
        val senderThread = request.senderThread
        val errorType = request.errorTypeForCausingEpdStop
        val prevEpdState = epdState
        epdState = AudioEndPointDetector.State.STOP

        if(errorType != null) {
            senderThread?.requestStop()
            handleError(errorType, request.eventMessageHeader)
            return
        } else if(request.stopByCancel == false && prevEpdState == AudioEndPointDetector.State.SPEECH_START){
            senderThread?.requestFinish()
            epdState = AudioEndPointDetector.State.STOP
            setState(SpeechRecognizer.State.SPEECH_END, request)
        } else {
            if(senderThread == null) {
                sendStopRecognizeEvent(request.eventMessage)
            } else {
                senderThread.requestStop()
            }

            handleCancel()
            return
        }
    }

    override fun onError(type: AudioEndPointDetector.ErrorType, e: Exception?) {
        Logger.d(TAG, "[AudioEndPointDetector::onError] type: $type, e:$e")
        val request = startLock.withLock {
            currentRequest
        }

        if(request == null) {
            Logger.e(TAG, "[AudioEndPointDetector::onError] null request. check this!!!")
            epdState = AudioEndPointDetector.State.ERROR
            return
        }

        isEpdRunning = false
        epdState = AudioEndPointDetector.State.ERROR
        request.senderThread?.requestStop()
        handleError(when(type) {
            AudioEndPointDetector.ErrorType.ERROR_EPD_ENGINE -> ASRAgentInterface.ErrorType.ERROR_UNKNOWN
            AudioEndPointDetector.ErrorType.ERROR_AUDIO_INPUT -> ASRAgentInterface.ErrorType.ERROR_AUDIO_INPUT
            AudioEndPointDetector.ErrorType.ERROR_EXCEPTION -> ASRAgentInterface.ErrorType.ERROR_UNKNOWN
        }, request.eventMessageHeader)
    }

    private fun startSpeechSenderThread(request: RecognizeRequest, speechStartPosition: Long?) {
        val sendPositionAndWakeupBoundary = request.sendPositionAndWakeupBoundary
        val sendPosition =  if(sendPositionAndWakeupBoundary.first == null) {
            speechStartPosition
        } else {
            sendPositionAndWakeupBoundary.first
        }

        Logger.d(
            TAG,
            "[startSpeechToTextConverter] send position : $sendPosition / send wakeupBoundary ${sendPositionAndWakeupBoundary.second} / wakeupInfo : ${request.wakeupInfo} / bytesPerMillis : ${request.audioFormat.getBytesPerMillis()}"
        )

        with(request) {
            senderThread = SpeechRecognizeAttachmentSenderThread(
                audioInputStream.createReader(sendPosition),
                audioFormat,
                messageSender,
                object :
                    SpeechRecognizeAttachmentSenderThread.RecognizeSenderObserver {
                    override fun onFinish() {
                        // called from epd state changes.
                        onSendEventFinished(eventMessage.dialogRequestId)
                        senderThread = null
                    }

                    override fun onStop() {
                        // called from epd state changes.
                        senderThread = null
                        sendStopRecognizeEvent(eventMessage)
                    }

                    override fun onError(errorType: ASRAgentInterface.ErrorType) {
                        senderThread = null
                        if (epdState.isActive()) {
                            errorTypeForCausingEpdStop = errorType
                            endPointDetector.stopDetector()
                        } else {
                            handleError(errorType, eventMessageHeader)
                        }
                    }
                },
                audioEncoder,
                eventMessage
            ).apply {
                start()
            }
        }
    }

    private fun handleCancel() {
        currentRequest?.let {
            Logger.d(TAG, "[handleCancel]")
            it.resultListener?.onCancel(it.cancelCause ?: ASRAgentInterface.CancelCause.LOCAL_API, it.eventMessageHeader)
            currentRequest = null
            isEpdRunning = false
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun handleError(errorType: ASRAgentInterface.ErrorType, header: Header) {
        currentRequest?.let {
            Logger.d(TAG, "[handleError] errorType: $errorType")
            it.resultListener?.onError(errorType, header)
            currentRequest = null
            isEpdRunning = false
            setState(SpeechRecognizer.State.STOP, it)
        }
    }

    private fun handleFinish() {
        currentRequest?.let {
            Logger.d(TAG, "[handleFinish]")
            currentRequest = null
            isEpdRunning = false
            setState(SpeechRecognizer.State.STOP, it)
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

    private fun setState(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        stateLock.withLock {
            Logger.d(TAG, "[setState] prev: ${this.state} / next: $state")
            if (this.state == state) {
                return
            }

            this.state = state
        }

        notifyObservers(state, request)
    }

    private fun notifyObservers(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        listeners.forEach {
            it.onStateChanged(state, request)
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
            if(it.eventMessage.dialogRequestId == dialogRequestId) {
                handleError(ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT, it.eventMessageHeader)
            }
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
}