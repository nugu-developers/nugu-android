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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.asr.*
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.agent.asr.impl.DefaultClientSpeechRecognizer
import com.skt.nugu.sdk.agent.asr.impl.DefaultServerSpeechRecognizer
import com.skt.nugu.sdk.agent.asr.payload.ExpectSpeechDirective
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DefaultASRAgent(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val focusManager: SeamlessFocusManagerInterface,
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val sessionManager: SessionManagerInterface,
    dialogAttributeStorage: DialogAttributeStorageInterface,
    private val audioProvider: AudioProvider,
    audioEncoder: Encoder,
    endPointDetector: AudioEndPointDetector?,
    private val defaultEpdTimeoutMillis: Long,
    userInteractionDialogChannelName: String,
    internalDialogChannelName: String,
    dummyChannelName: String,
    private val playSynchronizer: PlaySynchronizerInterface,
    private val interactionControlManager: InteractionControlManagerInterface
) : AbstractCapabilityAgent(NAMESPACE)
    , ASRAgentInterface
    , SpeechRecognizer.OnStateChangeListener
    , ChannelObserver {
    companion object {
        private const val TAG = "DefaultASRAgent"

        const val NAMESPACE = "ASR"
        val VERSION = Version(1,4)

        const val NAME_EXPECT_SPEECH = "ExpectSpeech"
        const val NAME_RECOGNIZE = "Recognize"
        const val NAME_NOTIFY_RESULT = "NotifyResult"

        private const val NAME_LISTEN_TIMEOUT = "ListenTimeout"
        private const val NAME_LISTEN_FAILED = "ListenFailed"
        private const val NAME_RESPONSE_TIMEOUT = "ResponseTimeout"

        const val DESCRIPTION_NOTIFY_ERROR_RESPONSE_TIMEOUT = "Response timeout"

        const val EVENT_STOP_RECOGNIZE = "StopRecognize"

        val EXPECT_SPEECH = NamespaceAndName(
            NAMESPACE,
            NAME_EXPECT_SPEECH
        )
        val RECOGNIZE = NamespaceAndName(
            NAMESPACE,
            NAME_RECOGNIZE
        )
        val NOTIFY_RESULT = NamespaceAndName(
            NAMESPACE,
            NAME_NOTIFY_RESULT
        )

        private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    data class ExpectSpeechDirectiveParam(
        val directive: ExpectSpeechDirective,
        val playSyncObject: PlaySynchronizerInterface.SynchronizeObject
    ) : SessionManagerInterface.Requester

    data class InternalStartRecognitionParam(
        val audioInputStream: SharedDataStream,
        val audioFormat: AudioFormat,
        val wakeupInfo: WakeupInfo?,
        val expectSpeechDirectiveParam: ExpectSpeechDirectiveParam?,
        val endPointDetectorParam: EndPointDetectorParam?,
        val callback: ASRAgentInterface.StartRecognitionCallback?,
        val jsonContext: String
    )

    private val onStateChangeListeners = HashSet<ASRAgentInterface.OnStateChangeListener>()
    private val onResultListeners = HashSet<ASRAgentInterface.OnResultListener>()
    private val onMultiturnListeners = HashSet<ASRAgentInterface.OnMultiturnListener>()

    private val executor = Executors.newSingleThreadExecutor()
    private var state = ASRAgentInterface.State.IDLE

    private var focusState = FocusState.NONE

    private var waitingFocusInternalStartRecognitionParam: InternalStartRecognitionParam? = null
    private var currentRequest: SpeechRecognizer.Request? = null

    private var expectSpeechDirectiveParam: ExpectSpeechDirectiveParam? = null

    private var currentAudioProvider: AudioProvider? = null
    private val speechProcessorLock = ReentrantLock()

    private var currentSpeechRecognizer: SpeechRecognizer
    private var nextStartSpeechRecognizer: SpeechRecognizer
    private val serverEpdSpeechRecognizer: SpeechRecognizer
    private val clientEpdSpeechRecognizer: SpeechRecognizer?

    private val attributeStorageManager = AttributeStorageManager(dialogAttributeStorage)

    private val asrFocusRequester = object : SeamlessFocusManagerInterface.Requester {}
    private val userSpeechFocusChannel =
        SeamlessFocusManagerInterface.Channel(userInteractionDialogChannelName, this, NAMESPACE)
    private val expectSpeechFocusChannel =
        SeamlessFocusManagerInterface.Channel(internalDialogChannelName, this, NAMESPACE)
    private val dummyFocusRequester = object : SeamlessFocusManagerInterface.Requester {}

    private val scheduleExecutor = ScheduledThreadPoolExecutor(1)

    private val speechToTextConverterEventObserver =
        object : ASRAgentInterface.OnResultListener {
            override fun onNoneResult(header: Header) {
                Logger.d(TAG, "[onNoneResult] $header")
                onResultListeners.forEach {
                    it.onNoneResult(header)
                }
            }

            override fun onPartialResult(result: String, header: Header) {
                Logger.d(TAG, "[onPartialResult] $result, $header")
                onResultListeners.forEach {
                    it.onPartialResult(result, header)
                }
            }

            override fun onCompleteResult(result: String, header: Header) {
                Logger.d(TAG, "[onCompleteResult] $result, $header")
                onResultListeners.forEach {
                    it.onCompleteResult(result, header)
                }
            }

            override fun onError(type: ASRAgentInterface.ErrorType, header: Header) {
                Logger.w(TAG, "[onError] $type, $header")
                onResultListeners.forEach {
                    it.onError(type, header)
                }
            }

            override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
                Logger.d(TAG, "[onCancel] $cause, $header")
                onResultListeners.forEach {
                    it.onCancel(cause, header)
                }
            }
        }

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    init {
        serverEpdSpeechRecognizer =
            DefaultServerSpeechRecognizer(
                inputProcessorManager,
                audioEncoder,
                messageSender
            )

        clientEpdSpeechRecognizer = if (endPointDetector != null) {
            DefaultClientSpeechRecognizer(
                inputProcessorManager,
                audioEncoder,
                messageSender,
                endPointDetector
            )
        } else {
            null
        }

        val initialSpeechProcessor = clientEpdSpeechRecognizer ?: serverEpdSpeechRecognizer
        currentSpeechRecognizer = initialSpeechProcessor
        nextStartSpeechRecognizer = initialSpeechProcessor

        initialSpeechProcessor.addListener(this)
        contextManager.setStateProvider(namespaceAndName, this)

        provideState(contextManager, namespaceAndName, ContextType.FULL, 0)
        provideState(contextManager, namespaceAndName, ContextType.COMPACT, 0)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandleDirective] $info")
        when (info.directive.getNamespaceAndName()) {
            EXPECT_SPEECH -> preHandleExpectSpeech(info)
        }
    }

    private fun preHandleExpectSpeech(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandleExpectSpeech] $info")

        executor.submit {
            val payload = parseExpectSpeechPayload(info.directive)
            if (payload == null) {
                setHandlingExpectSpeechFailed(
                    payload,
                    info,
                    "[executePreHandleExpectSpeechDirective] invalid payload"
                )
            } else {
                executePreHandleExpectSpeechInternal(ExpectSpeechDirectiveParam(ExpectSpeechDirective(info.directive.header, payload), object: PlaySynchronizerInterface.SynchronizeObject {
                    override fun getPlayServiceId(): String? = payload.playServiceId

                    override fun getDialogRequestId(): String = info.directive.header.dialogRequestId

                    override fun requestReleaseSync() {
                        executor.submit {
                            Logger.d(TAG, "[requestReleaseSync]")
                            executeCancelExpectSpeechDirective(info.directive.header.messageId)
                        }
                    }

                    override fun onSyncStateChanged(
                        prepared: List<PlaySynchronizerInterface.SynchronizeObject>,
                        started: List<PlaySynchronizerInterface.SynchronizeObject>
                    ) {
                        // ignore
                    }

                }))
            }
        }
    }

    private fun executePreHandleExpectSpeechInternal(param: ExpectSpeechDirectiveParam) {
        Logger.d(TAG, "[executePreHandleExpectSpeechInternal] success, param: $param")
        expectSpeechDirectiveParam?.let {
            sessionManager.deactivate(it.directive.header.dialogRequestId, it)
            playSynchronizer.releaseSync(it.playSyncObject)
        }

        if(focusState != FocusState.FOREGROUND) {
            focusManager.prepare(asrFocusRequester)
        }
        expectSpeechDirectiveParam = param
        sessionManager.activate(param.directive.header.dialogRequestId, param)
        attributeStorageManager.setAttributes(param)
    }

    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleDirective] $info")
        when (info.directive.getNamespaceAndName()) {
            EXPECT_SPEECH -> handleExpectSpeechDirective(info)
            NOTIFY_RESULT -> handleNotifyResult(info)
            else -> {
                handleDirectiveException(info)
            }
        }
    }

    private fun handleExpectSpeechDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleExpectSpeechDirective] $info")

        executor.submit {
            executeHandleExpectSpeechDirective(info)
        }
    }

    private fun executeSelectSpeechProcessor() {
        Logger.d(TAG, "[executeSelectSpeechProcessor]")
        val speechProcessorToStart = speechProcessorLock.withLock {
            nextStartSpeechRecognizer
        }

        if (speechProcessorToStart != currentSpeechRecognizer) {
            currentSpeechRecognizer.removeListener(this)
            currentSpeechRecognizer = speechProcessorToStart
            currentSpeechRecognizer.addListener(this)
        }
    }


    private fun executeHandleExpectSpeechDirective(info: DirectiveInfo) {
        // Here we do not check validation of payload due to checked at preHandleExpectSpeech already.
        val payload = parseExpectSpeechPayload(info.directive)
        if(payload == null) {
            setHandlingFailed(info, "[executeHandleExpectSpeechDirective] invalid payload")
            return
        }

        val param = expectSpeechDirectiveParam
        if(info.directive.getMessageId() != param?.directive?.header?.messageId) {
            setHandlingFailed(info, "[executeHandleExpectSpeechDirective] not match with current expectSpeechDirective (${info.directive.getMessageId()} / ${param?.directive?.header?.messageId})")
            return
        }

        if (!canRecognizing()) {
            Logger.w(
                TAG,
                "[executeHandleExpectSpeechDirective] ExpectSpeech only allowed in IDLE or BUSY state."
            )
            setHandlingExpectSpeechFailed(
                param,
                info,
                "[executeHandleExpectSpeechDirective] ExpectSpeech only allowed in IDLE or BUSY state."
            )
            return
        }

        currentAudioProvider = audioProvider

        val audioInputStream: SharedDataStream? = audioProvider.acquireAudioInputStream(this)
        val audioFormat: AudioFormat = audioProvider.getFormat()

        if (audioInputStream == null) {
            setHandlingExpectSpeechFailed(
                param,
                info,
                "[executeHandleExpectSpeechDirective] audioInputStream is null"
            )
            return
        }

        setState(ASRAgentInterface.State.EXPECTING_SPEECH)
        executeStartRecognition(audioInputStream, audioFormat, null, param, null, object: ASRAgentInterface.StartRecognitionCallback {
            override fun onSuccess(dialogRequestId: String) {
                setHandlingCompleted(info)
            }

            override fun onError(
                dialogRequestId: String,
                errorType: ASRAgentInterface.StartRecognitionCallback.ErrorType
            ) {
                if(state == ASRAgentInterface.State.EXPECTING_SPEECH && waitingFocusInternalStartRecognitionParam == null) {
                    // back to idle state only when failed to request
                    setState(ASRAgentInterface.State.IDLE)
                    setHandlingExpectSpeechFailed(param, info, "[executeHandleExpectSpeechDirective] executeStartRecognition failed")
                } else {
                    setHandlingCompleted(info)
                }
            }
        }, false)
    }

    private fun parseExpectSpeechPayload(directive: Directive): ExpectSpeechPayload? {
        return try {
            MessageFactory.create(directive.payload, ExpectSpeechPayload::class.java)
        } catch (e: Exception) {
            Logger.w(
                TAG,
                "[executeHandleExpectSpeechDirective] invalid payload (${directive.payload})"
            )
            return null
        }
    }


    private fun handleNotifyResult(info: DirectiveInfo) {
        Logger.d(TAG, "[handleNotifyResult] $info")
        val directive = info.directive

        val notifyResultPayload =
            MessageFactory.create(directive.payload, AsrNotifyResultPayload::class.java)
        if (notifyResultPayload == null) {
            Logger.e(TAG, "[handleNotifyResult] invalid payload: ${directive.payload}")
            setHandlingFailed(
                info,
                "[handleNotifyResult] invalid payload: ${directive.payload}"
            )
            return
        }

        if (!notifyResultPayload.isValidPayload()) {
            Logger.e(TAG, "[handleNotifyResult] invalid payload : $notifyResultPayload")
            setHandlingFailed(
                info,
                "[handleNotifyResult] invalid payload : $notifyResultPayload"
            )
            return
        }

        executor.submit {
            currentSpeechRecognizer.notifyResult(info.directive, notifyResultPayload)
            setHandlingCompleted(info)
        }
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
        info.result.setFailed(msg)
    }

    private fun setHandlingExpectSpeechFailed(param: ExpectSpeechDirectiveParam?, info: DirectiveInfo, msg: String) {
        setHandlingFailed(info, msg)
        clearCurrentAttributeKeyIfMatchWith(param)
        param?.let {
            sessionManager.deactivate(it.directive.header.dialogRequestId, it)
        }
        param?.directive?.payload?.let {
            sendListenFailed(it, info.directive.getDialogRequestId())
        }
    }

    private fun clearCurrentAttributeKeyIfMatchWith(param: ExpectSpeechDirectiveParam?) {
        currentAttributeKey?.let {
            param?.directive?.header?.messageId?.let { key->
                attributeStorageManager.clearAttributes(key)
            }
        }
    }

    private fun handleDirectiveException(info: DirectiveInfo) {
        setHandlingFailed(info, "invalid directive")
    }

    override fun cancelDirective(info: DirectiveInfo) {
        executor.submit {
            Logger.d(TAG, "[cancelDirective] ${info.directive}")
            if (info.directive.getName() == NAME_EXPECT_SPEECH) {
                executeCancelExpectSpeechDirective(info.directive.getMessageId())
            }
        }
    }

    private fun executeCancelExpectSpeechDirective(messageId: String) {
        Logger.d(TAG, "[executeCancelExpectSpeechDirective] messageId: $messageId")
        val request = currentRequest
        if (request == null) {
            expectSpeechDirectiveParam?.let {
                if(it.directive.header.messageId == messageId) {
                    focusManager.cancel(asrFocusRequester)
                    sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    clearPreHandledExpectSpeech()
                    clearCurrentAttributeKeyIfMatchWith(it)
                }
                setState(ASRAgentInterface.State.IDLE)
            }
        } else {
            executeStopRecognitionOnAttributeUnset(messageId)
        }
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")

        contextSetter.setState(
            namespaceAndName,
            contextState,
            StateRefreshPolicy.NEVER,
            contextType,
            stateRequestToken
        )
    }

    override fun onFocusChanged(newFocus: FocusState) {
        try {
            Logger.d(TAG, "[onFocusChanged] start $newFocus")
            executor.submit {
                executeOnFocusChanged(newFocus)
            }.get(300, TimeUnit.MILLISECONDS)

            if(newFocus == FocusState.BACKGROUND) {
                while(state != ASRAgentInterface.State.IDLE) {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "[onFocusChanged] end $newFocus / occur exception: $e")
        } finally {
            Logger.d(TAG, "[onFocusChanged] end $newFocus")
        }
    }

    private fun executeStartRecognitionOnContextAvailable(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: ExpectSpeechDirectiveParam?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        jsonContext: String,
        byUser: Boolean) {
        Logger.d(
            TAG,
            "[executeStartRecognitionOnContextAvailable] state: $state, focusState: $focusState, expectSpeechDirectiveParam: $expectSpeechDirectiveParam"
        )
        if(waitingFocusInternalStartRecognitionParam != null) {
            callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
            return
        }

        if (state == ASRAgentInterface.State.RECOGNIZING) {
            Logger.e(
                TAG,
                "[executeStartRecognitionOnContextAvailable] Not permmited in current state: $state"
            )

            callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
            return
        }

        // getContext 하는 중에 preHandle로 인해 expectSpeechDirectiveParam이 변경되었을 경우 실패처리한다.
        if(expectSpeechDirectiveParam != null && expectSpeechDirectiveParam != this.expectSpeechDirectiveParam) {
            callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_UNKNOWN)
            return
        }

        if (focusState != FocusState.FOREGROUND) {
            val channel = if (expectSpeechDirectiveParam == null || byUser) {
                userSpeechFocusChannel
            } else {
                expectSpeechFocusChannel
            }

            if (!focusManager.acquire(asrFocusRequester, channel)
            ) {
                Logger.e(
                    TAG,
                    "[executeStartRecognitionOnContextAvailable] Unable to acquire channel"
                )
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_UNKNOWN)
                return
            }
        }

        InternalStartRecognitionParam(
            audioInputStream,
            audioFormat,
            wakeupInfo,
            expectSpeechDirectiveParam,
            param,
            callback,
            jsonContext
        ).apply {
            if (focusState == FocusState.FOREGROUND) {
                executeInternalStartRecognition(this)
            } else {
                waitingFocusInternalStartRecognitionParam = this
            }
        }
    }

    private fun executeOnFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[executeOnFocusChanged] newFocus: $newFocus")

        val prevState = focusState
        focusState = newFocus
        when (newFocus) {
            FocusState.FOREGROUND -> {
                val param = waitingFocusInternalStartRecognitionParam
                waitingFocusInternalStartRecognitionParam = null
                if(param == null) {
                    return
                }

                if(param.expectSpeechDirectiveParam != null && param.expectSpeechDirectiveParam != expectSpeechDirectiveParam) {
                    param.callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                    return
                }

                if (state != ASRAgentInterface.State.RECOGNIZING) {
                    executeInternalStartRecognition(param)
                } else {
                    param.callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                }
            }
            FocusState.BACKGROUND -> {
                if(expectSpeechDirectiveParam == null && state == ASRAgentInterface.State.EXPECTING_SPEECH) {
                    currentAttributeKey?.let { key ->
                        attributeStorageManager.clearAttributes(key)
                    }
                }

                if(prevState == FocusState.NONE) {
                    // disable flag if we failed
                    val param = waitingFocusInternalStartRecognitionParam
                    waitingFocusInternalStartRecognitionParam = null

                    expectSpeechDirectiveParam?.let {
                        executeCancelExpectSpeechDirective(it.directive.header.messageId)
                    }

                    param?.callback?.onError(
                        UUIDGeneration.timeUUID().toString(),
                        ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER
                    )
                }

                currentSpeechRecognizer.stop(true, ASRAgentInterface.CancelCause.LOSS_FOCUS)
                while(currentSpeechRecognizer.isRecognizing()) {
                    try {
                        Thread.sleep(10)
                    } catch (e: Exception) {
                        Logger.w(TAG, "[executeOnFocusChanged] occur exception", e)
                    }
                }

                focusManager.release(asrFocusRequester, userSpeechFocusChannel)
                focusManager.release(asrFocusRequester, expectSpeechFocusChannel)
            }
            FocusState.NONE -> {
            }
        }
    }

    private fun executeInternalStartRecognition(
        param: InternalStartRecognitionParam
    ) {
        Logger.d(TAG, "[executeInternalStartRecognition]")
        executeSelectSpeechProcessor()
        val expectSpeechDirectiveEndPointDetectorParam = param.expectSpeechDirectiveParam?.directive?.payload?.epd
        val endPointDetectorParam: EndPointDetectorParam = if(expectSpeechDirectiveEndPointDetectorParam != null) {
            with(expectSpeechDirectiveEndPointDetectorParam) {
                EndPointDetectorParam(
                    (timeoutMilliseconds ?: defaultEpdTimeoutMillis).div(1000).toInt(),
                    (maxSpeechDurationMilliseconds?.div(1000))?.toInt() ?: 10,
                    (silenceIntervalInMilliseconds?.toInt()) ?: 700
                )
            }
        } else {
            param.endPointDetectorParam ?: EndPointDetectorParam(defaultEpdTimeoutMillis.div(1000).toInt())
        }

        currentRequest = currentSpeechRecognizer.start(
            param.audioInputStream,
            param.audioFormat,
            param.jsonContext,
            param.wakeupInfo,
            param.expectSpeechDirectiveParam,
            endPointDetectorParam,
            object : ASRAgentInterface.OnResultListener {
                override fun onNoneResult(header: Header) {
                    param.expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onNoneResult(header)
                }

                override fun onPartialResult(result: String, header: Header) {
                    speechToTextConverterEventObserver.onPartialResult(result, header)
                }

                override fun onCompleteResult(result: String, header: Header) {
                    param.expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onCompleteResult(result, header)
                }

                override fun onError(type: ASRAgentInterface.ErrorType, header: Header) {
                    param.expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    if (type == ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT) {
                        sendResponseTimeout(param.expectSpeechDirectiveParam?.directive?.payload, header.dialogRequestId)
                    } else if (type == ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT) {
                        sendListenTimeout(param.expectSpeechDirectiveParam?.directive?.payload, header.dialogRequestId)
                    }
                    speechToTextConverterEventObserver.onError(type, header)
                }

                override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
                    param.expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onCancel(cause, header)
                }
            }
        ).also {
            if(it == null) {
                param.expectSpeechDirectiveParam?.let {
                    sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                }

                param.callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
            } else {
                param.callback?.onSuccess(it.eventMessage.dialogRequestId)
            }
        }

        if(expectSpeechDirectiveParam == param.expectSpeechDirectiveParam) {
            expectSpeechDirectiveParam = null
        }
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[EXPECT_SPEECH] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
        configuration[NOTIFY_RESULT] = BlockingPolicy()

        return configuration
    }

    override fun addOnStateChangeListener(listener: ASRAgentInterface.OnStateChangeListener) {
        Logger.d(TAG, "[addOnStateChangeListener] listener: $listener")
        executor.submit {
            onStateChangeListeners.add(listener)
        }
    }

    override fun removeOnStateChangeListener(listener: ASRAgentInterface.OnStateChangeListener) {
        Logger.d(TAG, "[removeOnStateChangeListener] listener: $listener")
        executor.submit {
            onStateChangeListeners.remove(listener)
        }
    }

    override fun addOnResultListener(listener: ASRAgentInterface.OnResultListener) {
        Logger.d(TAG, "[addOnResultListener] listener: $listener")
        executor.submit {
            onResultListeners.add(listener)
        }
    }

    override fun removeOnResultListener(listener: ASRAgentInterface.OnResultListener) {
        Logger.d(TAG, "[removeOnResultListener] listener: $listener")
        executor.submit {
            onResultListeners.remove(listener)
        }
    }

    override fun addOnMultiturnListener(listener: ASRAgentInterface.OnMultiturnListener) {
        Logger.d(TAG, "[addOnMultiturnListener] listener: $listener")
        executor.submit {
            onMultiturnListeners.add(listener)
        }
    }

    override fun removeOnMultiturnListener(listener: ASRAgentInterface.OnMultiturnListener) {
        Logger.d(TAG, "[removeOnMultiturnListener] listener: $listener")
        executor.submit {
            onMultiturnListeners.remove(listener)
        }
    }

    override fun onStateChanged(state: SpeechRecognizer.State, request: SpeechRecognizer.Request) {
        Logger.d(TAG, "[SpeechProcessorInterface] state: $state, request: $request")
        executor.submit {
            val aipState = when (state) {
                SpeechRecognizer.State.EXPECTING_SPEECH -> {
                    ASRAgentInterface.State.LISTENING
                }
                SpeechRecognizer.State.SPEECH_START -> ASRAgentInterface.State.RECOGNIZING
                SpeechRecognizer.State.SPEECH_END -> {
                    currentAudioProvider?.releaseAudioInputStream(this)
                    currentAudioProvider = null
                    ASRAgentInterface.State.BUSY
                }
                SpeechRecognizer.State.STOP -> {
                    currentAudioProvider?.releaseAudioInputStream(this)
                    currentAudioProvider = null
                    currentRequest = null
                    ASRAgentInterface.State.IDLE
                }
            }
            setState(aipState)
        }
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakeupInfo: WakeupInfo?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?
    ) {
        Logger.d(TAG, "[startRecognition] audioInputStream: $audioInputStream")
        executor.submit {
            if(state == ASRAgentInterface.State.EXPECTING_SPEECH) {
                Logger.w(TAG, "[startRecognition] cannot start recognize when EXPECT_SPEECH state.")
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
                return@submit
            }

            if (audioInputStream != null && audioFormat != null) {
                executeStartRecognition(
                    audioInputStream,
                    audioFormat,
                    wakeupInfo,
                    expectSpeechDirectiveParam,
                    param,
                    callback
                )
            } else {
                currentAudioProvider = audioProvider
                val newAudioInputStream: SharedDataStream? =
                    audioProvider.acquireAudioInputStream(this)
                val newAudioFormat: AudioFormat = audioProvider.getFormat()

                if (newAudioInputStream == null) {
                    Logger.w(
                        TAG,
                        "[startRecognition] audioInputStream is null"
                    )
                    callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                    return@submit
                }

                executeStartRecognition(
                    newAudioInputStream,
                    newAudioFormat,
                    null,
                    expectSpeechDirectiveParam,
                    param,
                    callback
                )
            }
        }
    }

    override fun stopRecognition(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        Logger.d(TAG, "[stopRecognition] $cancel, $cause")
        executor.submit {
            executeStopRecognition(cancel, cause)
        }
    }

//        fun setSpeechProcessor(speechProcessor: SpeechProcessorInterface) {
//            speechProcessorLock.withLock {
//                nextStartSpeechProcessor = speechProcessor
//            }
//        }
//
//        fun getSpeechProcessor() = speechProcessorLock.withLock {
//            nextStartSpeechProcessor
//        }

    private fun setState(state: ASRAgentInterface.State) {
        if (this.state == state) {
            return
        }

        Logger.d(TAG, "[setState] $state")
        if (state == ASRAgentInterface.State.IDLE) {
            Logger.d(TAG, "[setState] currentAttributeKey: $currentAttributeKey, $expectSpeechDirectiveParam")
            if(expectSpeechDirectiveParam == null) {
                currentAttributeKey?.let {
                    attributeStorageManager.clearAttributes(it)
                }
            }

            if(focusState != FocusState.NONE) {
                if(this.state == ASRAgentInterface.State.BUSY && focusState == FocusState.FOREGROUND) {
                    focusManager.prepare(dummyFocusRequester)
                    scheduleExecutor.schedule({
                        focusManager.cancel(dummyFocusRequester)
                    }, 200, TimeUnit.MILLISECONDS)
                }

                focusManager.release(asrFocusRequester, userSpeechFocusChannel)
                focusManager.release(asrFocusRequester, expectSpeechFocusChannel)
            }
        }

//        if (!state.isRecognizing()) {
//            clearPreHandledExpectSpeech()
//        }
        this.state = state

        for (listener in onStateChangeListeners) {
            listener.onStateChanged(state)
        }
    }

    private fun clearPreHandledExpectSpeech() {
        Logger.d(TAG, "[clearPreHandledExpectSpeech]")
        expectSpeechDirectiveParam = null
    }

    private fun executeStartRecognition(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: ExpectSpeechDirectiveParam?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        byUser: Boolean = true
    ) {
        Logger.d(TAG, "[executeStartRecognition] state: $state")
        if (!canRecognizing()) {
            Logger.w(
                TAG,
                "[executeStartRecognition] StartRecognizing allowed in IDLE or BUSY state."
            )
            callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_ALREADY_RECOGNIZING)
            return
        }

        contextManager.getContext(contextRequester = object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                Logger.d(TAG, "[onContext]")
                executor.submit {
                    executeStartRecognitionOnContextAvailable(
                        audioInputStream,
                        audioFormat,
                        wakeupInfo,
                        expectSpeechDirectiveParam,
                        param,
                        callback,
                        jsonContext,
                        byUser
                    )
                }
            }
        }, timeoutInMillis = 2000L)
    }

    private fun executeStopRecognition(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        currentSpeechRecognizer.stop(cancel, cause)
        expectSpeechDirectiveParam?.let {
            executeCancelExpectSpeechDirective(it.directive.header.messageId)
        }
    }

    private fun executeStopRecognitionOnAttributeUnset(key: String) {
        Logger.d(TAG, "[executeStopRecognitionOnAttributeUnset] key: $key, currentRequest: $currentRequest")
        currentRequest?.let {
            if(it.attributeKey == key) {
                Logger.d(TAG, "[executeStopRecognitionOnAttributeUnset] key: $key")
                currentSpeechRecognizer.stop(true, ASRAgentInterface.CancelCause.SESSION_CLOSED)
            }
        }
    }

    private fun canRecognizing(): Boolean {
        // It is possible to start recognition internally when
        // * ASR State == IDLE
        // * ASR State == BUSY
        // * ASR State == EXPECTING_SPEECH (prepared state for DM)
        return !state.isRecognizing() || state == ASRAgentInterface.State.BUSY || state == ASRAgentInterface.State.EXPECTING_SPEECH
    }

    private fun sendListenFailed(payload: ExpectSpeechPayload?, referrerDialogRequestId: String?) {
        JsonObject().apply {
            payload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_LISTEN_FAILED, this, referrerDialogRequestId)
        }
    }

    private fun sendResponseTimeout(payload: ExpectSpeechPayload?, referrerDialogRequestId: String) {
        JsonObject().apply {
            payload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_RESPONSE_TIMEOUT, this, referrerDialogRequestId)
        }
    }

    private fun sendListenTimeout(payload: ExpectSpeechPayload?, referrerDialogRequestId: String) {
        JsonObject().apply {
            payload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_LISTEN_TIMEOUT, this, referrerDialogRequestId)
        }
    }

    private fun sendEvent(name: String, payload: JsonObject, referrerDialogRequestId: String?) {
        contextManager.getContext(object: IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString()
                    ).referrerDialogRequestId(referrerDialogRequestId ?: "").payload(payload.toString())
                        .build()
                ).enqueue(object : MessageSender.Callback{
                    override fun onFailure(request: MessageRequest, status: Status) {
                    }

                    override fun onSuccess(request: MessageRequest) {
                    }

                    override fun onResponseStart(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)
    }

    private var currentAttributeKey: String? = null
    private val interactionControl = object: InteractionControl {
        override fun getMode(): InteractionControlMode = InteractionControlMode.MULTI_TURN
    }

    fun onSetAttribute(
        key: String
    ) {
        Logger.d(TAG, "[onSetAttribute] $key")
        currentAttributeKey = key
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(true)
            }
            interactionControlManager.start(interactionControl)
        }
    }

    fun onUnsetAttribute(key: String) {
        Logger.d(TAG, "[onUnsetAttribute] $key")
        if (currentAttributeKey != key) {
            Logger.e(TAG, "[onUnsetAttribute] current: $currentAttributeKey, unset: $key")
        }
        currentAttributeKey = null
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(false)
            }
            interactionControlManager.finish(interactionControl)
        }
        executeStopRecognitionOnAttributeUnset(key)
    }

    private inner class AttributeStorageManager(private val storage: DialogAttributeStorageInterface) {
        private var currentParam: ExpectSpeechDirectiveParam? = null

        fun setAttributes(param: ExpectSpeechDirectiveParam) {
            Logger.d(TAG, "[AttributeStorageManager::setAttributes] currentParam: $currentParam, param: $param")
            val key = param.directive.header.messageId
            val attr = HashMap<String, Any>().apply {
                param.directive.payload.also { payload ->
                    payload.playServiceId?.let {
                        put("playServiceId", it)
                    }
                    payload.domainTypes?.let {
                        put("domainTypes", it)
                    }
                    payload.asrContext?.let {
                        put("asrContext", it)
                    }
                }
            }

            val current = currentParam
            if(current != null && current != param) {
                clearAttributes(current.directive.header.messageId)
            }

            currentParam = param
            storage.setAttributes(attr)
            onSetAttribute(key)

            playSynchronizer.prepareSync(param.playSyncObject)
            playSynchronizer.startSync(param.playSyncObject)
        }

        fun clearAttributes(key: String) {
            Logger.d(TAG, "[AttributeStorageManager::clearAttributes] currentParam: $currentParam, key: $key")
            currentParam?.let {
                if(it.directive.header.messageId == key) {
                    storage.clearAttributes()
                    currentParam = null
                    onUnsetAttribute(key)
                    playSynchronizer.releaseSync(it.playSyncObject)
                }
            }
        }
    }
}