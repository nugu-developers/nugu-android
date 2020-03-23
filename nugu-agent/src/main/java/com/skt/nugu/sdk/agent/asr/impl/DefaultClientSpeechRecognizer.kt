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
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
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
    }

    private data class RecognizeRequest(
        val audioInputStream: SharedDataStream,
        val audioFormat: AudioFormat,
        val wakeupInfo: WakeupInfo?,
        val sendPositionAndWakeupBoundary: Pair<Long?, WakeupBoundary?>,
        var senderThread: SpeechRecognizeAttachmentSenderThread?,
        val eventMessage: EventMessageRequest,
        val recognitionCallback: ASRAgentInterface.StartRecognitionCallback?,
        val resultListener: ASRAgentInterface.OnResultListener?
    ) {
        var errorTypeForCausingEpdStop: ASRAgentInterface.ErrorType? = null
        var stopByCancel: Boolean? = null
    }


    override var enablePartialResult: Boolean = true
        set(value) {
            if (field == value) {
                return
            }

            field = value
        }

    private val stateLock = ReentrantLock()
    private var state = SpeechRecognizer.State.STOP
    private var epdState = AudioEndPointDetector.State.STOP

    private val listeners = HashSet<SpeechRecognizer.OnStateChangeListener>()

    private var currentRequest: RecognizeRequest? = null

    init {
        endPointDetector.addListener(this)
    }

    override fun start(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        context: String,
        wakeupInfo: WakeupInfo?,
        payload: ExpectSpeechPayload?,
        referrerDialogRequestId: String?,
        epdParam: EndPointDetectorParam,
        recognitionCallback: ASRAgentInterface.StartRecognitionCallback?,
        resultListener: ASRAgentInterface.OnResultListener?
    ) {
        Logger.d(
            TAG,
            "[startProcessor] wakeupInfo:$wakeupInfo, currentInputPosition: ${audioInputStream.getPosition()}"
        )

//        val sendPositionAndWakeupBoundary =
//            computeSendPositionAndWakeupBoundary(audioFormat, wakeupBoundary)

        // Do not deliver wakeup info when client epd mode.
        val sendPositionAndWakeupBoundary = Pair(null, null)

        val eventMessage = EventMessageRequest.Builder(
            context,
            DefaultASRAgent.RECOGNIZE.namespace,
            DefaultASRAgent.RECOGNIZE.name,
            DefaultASRAgent.VERSION.toString()
        ).payload(
            AsrRecognizeEventPayload(
                codec = AsrRecognizeEventPayload.CODEC_SPEEX,
                sessionId = payload?.sessionId,
                playServiceId = payload?.playServiceId,
                domainTypes = payload?.domainTypes,
                endpointing = AsrRecognizeEventPayload.ENDPOINTING_CLIENT,
                encoding = if (enablePartialResult) AsrRecognizeEventPayload.ENCODING_PARTIAL else AsrRecognizeEventPayload.ENCODING_COMPLETE
//                wakeupBoundary = sendPositionAndWakeupBoundary.second
            ).toJsonString()
        ).referrerDialogRequestId(referrerDialogRequestId ?: "")
            .build()

        currentRequest =
            RecognizeRequest(
                audioInputStream,
                audioFormat,
                wakeupInfo,
                sendPositionAndWakeupBoundary,
                null,
                eventMessage,
                recognitionCallback,
                resultListener
            )

        val listeningTimeoutSec:Int = if(payload != null) {
            (payload.timeoutInMilliseconds / 1000L).toInt()
        } else {
            epdParam.timeoutInSeconds
        }

        if (!endPointDetector.startDetector(
                audioInputStream.createReader(),
                audioFormat,
                listeningTimeoutSec,
                epdParam.maxDurationInSeconds,
                epdParam.pauseLengthInMilliseconds
            )
        ) {
            Logger.e(TAG, "[startProcessor] failed to start epd.")
            currentRequest?.recognitionCallback?.onError(eventMessage.dialogRequestId,
                ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER
            )
            currentRequest = null
        } else {
            Logger.d(TAG, "[startProcessor] started")
            currentRequest?.recognitionCallback?.onSuccess(eventMessage.dialogRequestId)
        }
    }

    override fun stop(cancel: Boolean) {
        if (epdState.isActive()) {
            currentRequest?.stopByCancel = cancel
            endPointDetector.stopDetector()
        }

        // TODO : stop at SPEECH_END
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
                if(epdState.isActive()) {
                    request.errorTypeForCausingEpdStop = ASRAgentInterface.ErrorType.ERROR_UNKNOWN
                    endPointDetector.stopDetector()
                } else {
                    request.senderThread?.requestStop()
                    handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
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
    }

    override fun onStateChanged(state: AudioEndPointDetector.State) {
        Logger.d(TAG, "[onStateChanged] AudioEndPointDetector prev: ${this.epdState} / next: $state , $currentRequest")

        val request = currentRequest

        if(request == null) {
            Logger.e(TAG, "[onStateChanged] null request. check this!!!")
            epdState = state
            return
        }

        val speechProcessorState = when (state) {
            AudioEndPointDetector.State.EXPECTING_SPEECH -> {
                if(!messageSender.sendMessage(request.eventMessage)) {
                    request.errorTypeForCausingEpdStop = ASRAgentInterface.ErrorType.ERROR_NETWORK
                    endPointDetector.stopDetector()
                }
                SpeechRecognizer.State.EXPECTING_SPEECH
            }
            AudioEndPointDetector.State.SPEECH_START -> {
                startSpeechSenderThread(request, endPointDetector.getSpeechStartPosition())
                SpeechRecognizer.State.SPEECH_START
            }
            AudioEndPointDetector.State.SPEECH_END -> {
                request.senderThread?.requestFinish()
                SpeechRecognizer.State.SPEECH_END
            }
            AudioEndPointDetector.State.TIMEOUT -> {
                if (epdState == AudioEndPointDetector.State.EXPECTING_SPEECH) {
                    request.senderThread?.requestStop()
                    handleError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT)
                    epdState = AudioEndPointDetector.State.TIMEOUT
                    return
                } else {
                    request.senderThread?.requestFinish()
                    SpeechRecognizer.State.SPEECH_END
                }
            }
            AudioEndPointDetector.State.STOP -> {
                val errorType = request.errorTypeForCausingEpdStop
                val prevEpdState = epdState
                epdState = AudioEndPointDetector.State.STOP

                if(errorType != null) {
                    request.senderThread?.requestStop()
                    handleError(errorType)
                    return
                } else if(request.stopByCancel == false && prevEpdState == AudioEndPointDetector.State.SPEECH_START){
                    request.senderThread?.requestFinish()
                    SpeechRecognizer.State.SPEECH_END
                } else {
                    request.senderThread?.requestStop()
                    handleCancel()
                    return
                }
            }

            AudioEndPointDetector.State.ERROR -> {
                epdState = AudioEndPointDetector.State.ERROR
                request.senderThread?.requestStop()
                handleError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
                return
            }
        }

        epdState = state

        setState(speechProcessorState)
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
                            handleError(errorType)
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
        currentRequest?.resultListener?.onCancel()
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }

    private fun handleError(errorType: ASRAgentInterface.ErrorType) {
        currentRequest?.resultListener?.onError(errorType)
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }

    private fun handleFinish() {
        currentRequest = null
        setState(SpeechRecognizer.State.STOP)
    }

    private fun sendStopRecognizeEvent(request: EventMessageRequest): Boolean {
        Logger.d(TAG, "[sendStopRecognizeEvent] $this")
        return request.let {
            messageSender.sendMessage(
                EventMessageRequest.Builder(
                    it.context,
                    DefaultASRAgent.NAMESPACE,
                    DefaultASRAgent.EVENT_STOP_RECOGNIZE,
                    DefaultASRAgent.VERSION.toString()
                ).referrerDialogRequestId(it.dialogRequestId).build()
            )
        }
    }

    private fun setState(state: SpeechRecognizer.State) {
        stateLock.withLock {
            Logger.d(TAG, "[setState] prev: ${this.state} / next: $state")
            if (this.state == state) {
                return
            }

            this.state = state
        }

        notifyObservers(state)
    }

    private fun notifyObservers(state: SpeechRecognizer.State) {
        listeners.forEach {
            it.onStateChanged(state)
        }
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveDirectives(
        dialogRequestId: String,
        directives: List<Directive>
    ): Boolean {
        val receiveResponse = directives.filter { it.header.namespace != DefaultASRAgent.NAMESPACE }.any()

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