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
import com.skt.nugu.sdk.agent.dialog.FocusHolderManager
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextState
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DefaultASRAgent(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val focusManager: FocusManagerInterface,
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val sessionManager: SessionManagerInterface,
    dialogAttributeStorage: DialogAttributeStorageInterface,
    private val audioProvider: AudioProvider,
    audioEncoder: Encoder,
    endPointDetector: AudioEndPointDetector?,
    private val defaultEpdTimeoutMillis: Long,
    private val channelName: String,
    private val focusHolderManager: FocusHolderManager
) : AbstractCapabilityAgent(NAMESPACE)
    , ASRAgentInterface
    , SpeechRecognizer.OnStateChangeListener
    , ChannelObserver
    , FocusHolderManager.OnStateChangeListener
    , FocusHolderManager.FocusHolder {
    companion object {
        private const val TAG = "DefaultASRAgent"

        const val NAMESPACE = "ASR"
        val VERSION = Version(1,2)

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
    }

    data class ExpectSpeechDirectiveParam(
        val directive: ExpectSpeechDirective
    ) : SessionManagerInterface.Requester

    private val onStateChangeListeners = HashSet<ASRAgentInterface.OnStateChangeListener>()
    private val onResultListeners = HashSet<ASRAgentInterface.OnResultListener>()
    private val onMultiturnListeners = HashSet<ASRAgentInterface.OnMultiturnListener>()

    private val executor = Executors.newSingleThreadExecutor()
    private var state = ASRAgentInterface.State.IDLE

    private var focusState = FocusState.NONE

    private var isRequested: Boolean = false
    private var currentRequest: SpeechRecognizer.Request? = null

    private var audioInputStream: SharedDataStream? = null
    private var audioFormat: AudioFormat? = null
    private var wakeupInfo: WakeupInfo? = null
    private var currentDialogAttributeId: String? = null
    private var expectSpeechDirectiveParam: ExpectSpeechDirectiveParam? = null
    private var endPointDetectorParam: EndPointDetectorParam? = null
    private var startRecognitionCallback: ASRAgentInterface.StartRecognitionCallback? = null

    private var contextForRecognitionOnForegroundFocus: String? = null

    private var currentAudioProvider: AudioProvider? = null
    private val speechProcessorLock = ReentrantLock()

    private var currentSpeechRecognizer: SpeechRecognizer
    private var nextStartSpeechRecognizer: SpeechRecognizer
    private val serverEpdSpeechRecognizer: SpeechRecognizer
    private val clientEpdSpeechRecognizer: SpeechRecognizer?

    private val attributeStorageManager = AttributeStorageManager(dialogAttributeStorage)

    private val speechToTextConverterEventObserver =
        object : ASRAgentInterface.OnResultListener {
            override fun onNoneResult(dialogRequestId: String) {
                Logger.d(TAG, "[onNoneResult] $dialogRequestId")
                onResultListeners.forEach {
                    it.onNoneResult(dialogRequestId)
                }
            }

            override fun onPartialResult(result: String, dialogRequestId: String) {
                Logger.d(TAG, "[onPartialResult] $result, $dialogRequestId")
                onResultListeners.forEach {
                    it.onPartialResult(result, dialogRequestId)
                }
            }

            override fun onCompleteResult(result: String, dialogRequestId: String) {
                Logger.d(TAG, "[onCompleteResult] $result, $dialogRequestId")
                onResultListeners.forEach {
                    it.onCompleteResult(result, dialogRequestId)
                }
            }

            override fun onError(type: ASRAgentInterface.ErrorType, dialogRequestId: String) {
                Logger.w(TAG, "[onError] $type, $dialogRequestId")
                onResultListeners.forEach {
                    it.onError(type, dialogRequestId)
                }
            }

            override fun onCancel(cause: ASRAgentInterface.CancelCause, dialogRequestId: String) {
                Logger.d(TAG, "[onCancel] $cause, $dialogRequestId")
                onResultListeners.forEach {
                    it.onCancel(cause, dialogRequestId)
                }
            }
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
                executePreHandleExpectSpeechInternal(ExpectSpeechDirectiveParam(ExpectSpeechDirective(info.directive.header, payload)))
            }
        }
    }

    private fun executePreHandleExpectSpeechInternal(param: ExpectSpeechDirectiveParam) {
        Logger.d(TAG, "[executePreHandleExpectSpeechInternal] success, param: $param")
        expectSpeechDirectiveParam?.let {
            sessionManager.deactivate(it.directive.header.dialogRequestId, it)
        }

        expectSpeechDirectiveParam = param
        sessionManager.activate(param.directive.header.dialogRequestId, param)
        attributeStorageManager.setAttributes(param.directive.header.messageId, HashMap<String, Any>().apply {
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
        })
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
                if(state == ASRAgentInterface.State.EXPECTING_SPEECH && !isRequested) {
                    // back to idle state only when failed to request
                    setState(ASRAgentInterface.State.IDLE)
                    setHandlingExpectSpeechFailed(param, info, "[executeHandleExpectSpeechDirective] executeStartRecognition failed")
                } else {
                    setHandlingCompleted(info)
                }
            }
        })
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
            if (info.directive.getName() == NAME_EXPECT_SPEECH) {
                val request = currentRequest
                if (request == null) {
                    expectSpeechDirectiveParam?.let {
                        if(it.directive.header.messageId == info.directive.getMessageId()) {
                            sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                            clearPreHandledExpectSpeech()
                            clearCurrentAttributeKeyIfMatchWith(it)
                        }
                    }
                } else {
                    executeStopRecognitionOnAttributeUnset(info.directive.getMessageId())
                }
            }
        }
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] $stateRequestToken")
        contextSetter.setState(
            namespaceAndName,
            object: ContextState {
                val state = JsonObject().apply {
                    addProperty(
                        "version",
                        VERSION.toString()
                    )
                }.toString()

                override fun toFullJsonString(): String = state
                override fun toCompactJsonString(): String = state
            },
            StateRefreshPolicy.NEVER,
            stateRequestToken
        )
    }

    override fun onFocusChanged(newFocus: FocusState) {
        executor.submit {
            executeOnFocusChanged(newFocus)
        }
    }

    private fun executeStartRecognitionOnContextAvailable(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: ExpectSpeechDirectiveParam?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        jsonContext: String) {
        Logger.d(
            TAG,
            "[executeStartRecognitionOnContextAvailable] state: $state, focusState: $focusState"
        )
        if(isRequested) {
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

        if (focusState != FocusState.FOREGROUND) {
            if (!focusManager.acquireChannel(
                    channelName, this,
                    NAMESPACE
                )
            ) {
                Logger.e(
                    TAG,
                    "[executeStartRecognitionOnContextAvailable] Unable to acquire channel"
                )
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_UNKNOWN)
                return
            }
        }

        isRequested = true
        if (focusState == FocusState.FOREGROUND) {
            executeInternalStartRecognition(
                audioInputStream,
                audioFormat,
                wakeupInfo,
                expectSpeechDirectiveParam,
                param,
                callback,
                jsonContext
            )
        } else {
            this.audioInputStream = audioInputStream
            this.audioFormat = audioFormat
            this.wakeupInfo = wakeupInfo
            this.expectSpeechDirectiveParam = expectSpeechDirectiveParam
            this.endPointDetectorParam = param
            this.startRecognitionCallback = callback
            this.contextForRecognitionOnForegroundFocus = jsonContext
        }
    }

    private fun executeOnFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[executeOnFocusChanged] newFocus: $newFocus")

        focusState = newFocus

        when (newFocus) {
            FocusState.FOREGROUND -> {
                val inputStream = this.audioInputStream
                val audioFormat = this.audioFormat
                val wakeupInfo = this.wakeupInfo
                val expectSpeechDirectiveParam = this.expectSpeechDirectiveParam
                val epdParam = this.endPointDetectorParam
                val callback = this.startRecognitionCallback
                val context = contextForRecognitionOnForegroundFocus

                this.audioInputStream = null
                this.audioFormat = null
                this.wakeupInfo =  null
                this.expectSpeechDirectiveParam = null
                this.endPointDetectorParam = null
                this.startRecognitionCallback = null
                this.contextForRecognitionOnForegroundFocus = null

                if (inputStream == null || audioFormat == null) {
                    Logger.e(TAG, "[executeInternalStartRecognition] invalid audio input")
                    callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                    return
                }

                if (state != ASRAgentInterface.State.RECOGNIZING && context != null) {
                    executeInternalStartRecognition(inputStream, audioFormat, wakeupInfo, expectSpeechDirectiveParam, epdParam, callback, context)
                }
            }
            FocusState.BACKGROUND -> focusManager.releaseChannel(channelName, this)
            FocusState.NONE -> executeStopRecognition(true, ASRAgentInterface.CancelCause.LOSS_FOCUS)
        }
    }

    private fun executeInternalStartRecognition(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        expectSpeechDirectiveParam: ExpectSpeechDirectiveParam?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        jsonContext: String
    ) {
        Logger.d(TAG, "[executeInternalStartRecognition]")
        executeSelectSpeechProcessor()
        currentRequest = currentSpeechRecognizer.start(
            audioInputStream,
            audioFormat,
            jsonContext,
            wakeupInfo,
            expectSpeechDirectiveParam,
            param ?: EndPointDetectorParam(defaultEpdTimeoutMillis.div(1000).toInt()),
            object : ASRAgentInterface.OnResultListener {
                override fun onNoneResult(dialogRequestId: String) {
                    expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onNoneResult(dialogRequestId)
                }

                override fun onPartialResult(result: String, dialogRequestId: String) {
                    speechToTextConverterEventObserver.onPartialResult(result, dialogRequestId)
                }

                override fun onCompleteResult(result: String, dialogRequestId: String) {
                    expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onCompleteResult(result, dialogRequestId)
                }

                override fun onError(type: ASRAgentInterface.ErrorType, dialogRequestId: String) {
                    expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    if (type == ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT) {
                        sendResponseTimeout(expectSpeechDirectiveParam?.directive?.payload, dialogRequestId)
                    } else if (type == ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT) {
                        sendListenTimeout(expectSpeechDirectiveParam?.directive?.payload, dialogRequestId)
                    }
                    speechToTextConverterEventObserver.onError(type, dialogRequestId)
                }

                override fun onCancel(
                    cause: ASRAgentInterface.CancelCause,
                    dialogRequestId: String
                ) {
                    expectSpeechDirectiveParam?.let {
                        sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                    }

                    speechToTextConverterEventObserver.onCancel(cause, dialogRequestId)
                }
            }
        ).also {
            if(it == null) {
                expectSpeechDirectiveParam?.let {
                    sessionManager.deactivate(it.directive.header.dialogRequestId, it)
                }

                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
            } else {
                callback?.onSuccess(it.eventMessage.dialogRequestId)
            }
        }

        clearPreHandledExpectSpeech()
        isRequested = false
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[EXPECT_SPEECH] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
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

    override fun onStateChanged(state: FocusHolderManager.State) {
        executor.submit {
            if(state != FocusHolderManager.State.UNHOLD) {
                return@submit
            }

            if(focusState != FocusState.NONE && !currentSpeechRecognizer.isRecognizing()) {
                focusManager.releaseChannel(channelName, this)
                focusState = FocusState.NONE
            }
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

            focusHolderManager.abandon(this)
        } else {
            focusHolderManager.request(this)
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
        callback: ASRAgentInterface.StartRecognitionCallback?
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

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
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
                        jsonContext)
                }
            }
        })
    }

    private fun executeStopRecognition(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        currentSpeechRecognizer.stop(cancel, cause)
    }

    private fun executeStopRecognitionOnAttributeUnset(key: String) {
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
        messageSender.newCall(
            EventMessageRequest.Builder(
                contextManager.getContextWithoutUpdate(
                    namespaceAndName
                ), NAMESPACE, name, VERSION.toString()
            ).referrerDialogRequestId(referrerDialogRequestId ?: "").payload(payload.toString())
                .build()
        ).enqueue(object : MessageSender.Callback{
            override fun onFailure(request: MessageRequest, status: Status) {
            }

            override fun onSuccess(request: MessageRequest) {
            }
        })
    }

    private var currentAttributeKey: String? = null

    fun onSetAttribute(
        key: String
    ) {
        Logger.d(TAG, "[onSetAttribute] $key")
        currentAttributeKey = key
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(true)
            }
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
        }
        executeStopRecognitionOnAttributeUnset(key)
    }

    private inner class AttributeStorageManager(private val storage: DialogAttributeStorageInterface) {
        private var currentSetKey: String? = null
        fun setAttributes(key: String, attr: Map<String, Any>) {
            val current = currentSetKey
            if(current != null && current != key) {
                clearAttributes(current)
            }

            currentSetKey = key
            storage.setAttributes(attr)
            onSetAttribute(key)
        }

        fun clearAttributes(key: String) {
            if(currentSetKey == key) {
                storage.clearAttributes()
                currentSetKey = null
                onUnsetAttribute(key)
            }
        }
    }
}