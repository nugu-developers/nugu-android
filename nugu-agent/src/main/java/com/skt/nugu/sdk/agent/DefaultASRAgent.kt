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
import com.skt.nugu.sdk.agent.dialog.FocusHolderManager
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class DefaultASRAgent(
    private val inputProcessorManager: InputProcessorManagerInterface,
    private val focusManager: FocusManagerInterface,
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    private val dialogSessionManager: DialogSessionManagerInterface,
    private val audioProvider: AudioProvider,
    audioEncoder: Encoder,
    endPointDetector: AudioEndPointDetector?,
    private val defaultEpdTimeoutMillis: Long,
    private val channelName: String,
    private val focusHolderManager: FocusHolderManager
) : AbstractCapabilityAgent(NAMESPACE)
    , ASRAgentInterface
    , DialogSessionManagerInterface.OnSessionStateChangeListener
    , SpeechRecognizer.OnStateChangeListener
    , ChannelObserver
    , FocusHolderManager.OnStateChangeListener
    , FocusHolderManager.FocusHolder {
    companion object {
        private const val TAG = "DefaultASRAgent"

        const val NAMESPACE = "ASR"
        val VERSION = Version(1,1)

        const val NAME_EXPECT_SPEECH = "ExpectSpeech"
        const val NAME_RECOGNIZE = "Recognize"
        private const val NAME_NOTIFY_RESULT = "NotifyResult"

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

    private val onStateChangeListeners = HashSet<ASRAgentInterface.OnStateChangeListener>()
    private val onResultListeners = HashSet<ASRAgentInterface.OnResultListener>()
    private val onMultiturnListeners = HashSet<ASRAgentInterface.OnMultiturnListener>()

    private val executor = Executors.newSingleThreadExecutor()
    private var state = ASRAgentInterface.State.IDLE

    private var focusState = FocusState.NONE

    private var isRequested: Boolean = false
    private var audioInputStream: SharedDataStream? = null
    private var audioFormat: AudioFormat? = null
    private var wakeupInfo: WakeupInfo? = null
    private var expectSpeechPayload: ExpectSpeechPayload? = null
    private var referrerDialogRequestId: String? = null
    private var endPointDetectorParam: EndPointDetectorParam? = null
    private var startRecognitionCallback: ASRAgentInterface.StartRecognitionCallback? = null

    private var contextForRecognitionOnForegroundFocus: String? = null

    private var initialDialogUXStateReceived: Boolean = false

    private var currentAudioProvider: AudioProvider? = null
    private val speechProcessorLock = ReentrantLock()

    private var currentSpeechRecognizer: SpeechRecognizer
    private var nextStartSpeechRecognizer: SpeechRecognizer
    private val serverEpdSpeechRecognizer: SpeechRecognizer
    private val clientEpdSpeechRecognizer: SpeechRecognizer?

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

            override fun onError(errorType: ASRAgentInterface.ErrorType, dialogRequestId: String) {
                Logger.w(TAG, "[onError] $errorType, $dialogRequestId")
                onResultListeners.forEach {
                    it.onError(errorType, dialogRequestId)
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
        contextManager.setStateProvider(namespaceAndName, this, buildContext())
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
                executePreHandleExpectSpeechInternal(payload)
            }
        }
    }

    private fun executePreHandleExpectSpeechInternal(payload: ExpectSpeechPayload) {
        Logger.d(TAG, "[executePreHandleExpectSpeechInternal] success, payload: $payload")
        expectSpeechPayload = payload
        val asrContext = payload.asrContext
        dialogSessionManager.openSession(
            payload.sessionId,
            payload.domainTypes,
            payload.playServiceId,
            if(asrContext != null) {
                DialogSessionManagerInterface.Context(
                    asrContext.task,
                    asrContext.sceneId,
                    asrContext.sceneText
                )
            } else {
                null
            }
        )
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

        if (expectSpeechPayload == null) {
            Logger.e(TAG, "[executeHandleExpectSpeechDirective] re-parse payload due to loss")
            // re parse payload here.
            expectSpeechPayload = payload
        }

        if (!canRecognizing()) {
            Logger.w(
                TAG,
                "[executeHandleExpectSpeechDirective] ExpectSpeech only allowed in IDLE or BUSY state."
            )
            setHandlingExpectSpeechFailed(
                payload,
                info,
                "[executeHandleExpectSpeechDirective] ExpectSpeech only allowed in IDLE or BUSY state."
            )
            return
        }

        currentAudioProvider = audioProvider

        val audioInputStream: SharedDataStream? = audioProvider.acquireAudioInputStream(this)
        val audioFormat: AudioFormat = audioProvider.getFormat()

        if (audioInputStream == null) {
            Logger.w(
                TAG,
                "[executeHandleExpectSpeechDirective] audioInputStream is null"
            )
            setHandlingExpectSpeechFailed(
                payload,
                info,
                "[executeHandleExpectSpeechDirective] audioInputStream is null"
            )
            return
        }

        setState(ASRAgentInterface.State.EXPECTING_SPEECH)
        executeStartRecognition(audioInputStream, audioFormat, null, expectSpeechPayload, info.directive.getDialogRequestId(),null, object: ASRAgentInterface.StartRecognitionCallback {
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
                    setHandlingExpectSpeechFailed(payload, info, "[executeHandleExpectSpeechDirective] executeStartRecognition failed")
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

        val state = notifyResultPayload.state
        val result = notifyResultPayload.result

        executor.submit {
            currentSpeechRecognizer.notifyResult(state.name, result)
            setHandlingCompleted(info)
        }
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
        info.result.setFailed(msg)
        removeDirective(info)
    }

    private fun setHandlingExpectSpeechFailed(payload: ExpectSpeechPayload?, info: DirectiveInfo, msg: String) {
        setHandlingFailed(info, msg)
        payload?.let {
            sendListenFailed(it, info.directive.getDialogRequestId())
        }
    }

    private fun handleDirectiveException(info: DirectiveInfo) {
        setHandlingFailed(info, "invalid directive")
    }

    override fun cancelDirective(info: DirectiveInfo) {
        if (info.directive.getName() == NAME_EXPECT_SPEECH) {
            clearPreHandledExpectSpeech()
        }
        removeDirective(info)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(
            namespaceAndName,
            buildContext(),
            StateRefreshPolicy.NEVER,
            stateRequestToken
        )
    }

    private fun buildContext(): String = JsonObject().apply {
        addProperty(
            "version",
            VERSION.toString()
        )
    }.toString()

    override fun onFocusChanged(newFocus: FocusState) {
        executor.submit {
            executeOnFocusChanged(newFocus)
        }
    }

    private fun executeStartRecognitionOnContextAvailable(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        payload: ExpectSpeechPayload?,
        referrerDialogRequestId: String?,
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
                payload,
                referrerDialogRequestId,
                param,
                callback,
                jsonContext
            )
        } else {
            this.audioInputStream = audioInputStream
            this.audioFormat = audioFormat
            this.wakeupInfo = wakeupInfo
            this.expectSpeechPayload = payload
            this.referrerDialogRequestId = referrerDialogRequestId
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
                val payload = this.expectSpeechPayload
                val referrerDialogRequestId = this.referrerDialogRequestId
                val epdParam = this.endPointDetectorParam
                val callback = this.startRecognitionCallback
                val context = contextForRecognitionOnForegroundFocus

                this.audioInputStream = null
                this.audioFormat = null
                this.wakeupInfo =  null
                this.expectSpeechPayload = null
                this.referrerDialogRequestId = null
                this.endPointDetectorParam = null
                this.startRecognitionCallback = null
                this.contextForRecognitionOnForegroundFocus = null

                if (inputStream == null || audioFormat == null) {
                    Logger.e(TAG, "[executeInternalStartRecognition] invalid audio input")
                    callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_CANNOT_START_RECOGNIZER)
                    return
                }

                if (state != ASRAgentInterface.State.RECOGNIZING && context != null) {
                    executeInternalStartRecognition(inputStream, audioFormat, wakeupInfo, payload, referrerDialogRequestId, epdParam, callback, context)
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
        payload: ExpectSpeechPayload?,
        referrerDialogRequestId: String?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        jsonContext: String
    ) {
        Logger.d(TAG, "[executeInternalStartRecognition]")
        executeSelectSpeechProcessor()
        currentSpeechRecognizer.start(
            audioInputStream,
            audioFormat,
            jsonContext,
            wakeupInfo,
            payload,
            referrerDialogRequestId,
            param ?: EndPointDetectorParam(defaultEpdTimeoutMillis.div(1000).toInt()),
            callback,
            object : ASRAgentInterface.OnResultListener {
                override fun onNoneResult(dialogRequestId: String) {
                    speechToTextConverterEventObserver.onNoneResult(dialogRequestId)
                }

                override fun onPartialResult(result: String, dialogRequestId: String) {
                    speechToTextConverterEventObserver.onPartialResult(result, dialogRequestId)
                }

                override fun onCompleteResult(result: String, dialogRequestId: String) {
                    speechToTextConverterEventObserver.onCompleteResult(result, dialogRequestId)
                }

                override fun onError(type: ASRAgentInterface.ErrorType, dialogRequestId: String) {
                    if (type == ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT) {
                        sendResponseTimeout(payload, referrerDialogRequestId)
                    } else if (type == ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT) {
                        sendListenTimeout(payload, referrerDialogRequestId)
                    }
                    speechToTextConverterEventObserver.onError(type, dialogRequestId)
                }

                override fun onCancel(
                    cause: ASRAgentInterface.CancelCause,
                    dialogRequestId: String
                ) {
                    speechToTextConverterEventObserver.onCancel(cause, dialogRequestId)
                }
            }
        )

        clearPreHandledExpectSpeech()
        isRequested = false
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
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

    override fun onStateChanged(state: SpeechRecognizer.State) {
        Logger.d(TAG, "[SpeechProcessorInterface] state: $state")
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
                    expectSpeechPayload,
                    referrerDialogRequestId,
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
                    expectSpeechPayload,
                    referrerDialogRequestId,
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
            if(expectSpeechPayload == null) {
                currentSessionId?.let {
                    dialogSessionManager.closeSession()
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
        expectSpeechPayload = null
    }

    private fun executeStartRecognition(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        payload: ExpectSpeechPayload?,
        referrerDialogRequestId: String?,
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

        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                Logger.d(TAG, "[onContextAvailable]")
                executor.submit {
                    executeStartRecognitionOnContextAvailable(
                        audioInputStream,
                        audioFormat,
                        wakeupInfo,
                        payload,
                        referrerDialogRequestId,
                        param,
                        callback,
                        jsonContext)
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                Logger.w(TAG, "[onContextFailure] error: $error")
                callback?.onError(UUIDGeneration.timeUUID().toString(), ASRAgentInterface.StartRecognitionCallback.ErrorType.ERROR_TAKE_TOO_LONG_START_RECOGNITION)
            }
        })
    }

    private fun executeStopRecognition(cancel: Boolean, cause: ASRAgentInterface.CancelCause) {
        currentSpeechRecognizer.stop(cancel, cause)
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

    private fun sendResponseTimeout(payload: ExpectSpeechPayload?, referrerDialogRequestId: String?) {
        JsonObject().apply {
            payload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_RESPONSE_TIMEOUT, this, referrerDialogRequestId)
        }
    }

    private fun sendListenTimeout(payload: ExpectSpeechPayload?, referrerDialogRequestId: String?) {
        JsonObject().apply {
            payload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_LISTEN_TIMEOUT, this, referrerDialogRequestId)
        }
    }

    private fun sendEvent(name: String, payload: JsonObject, referrerDialogRequestId: String?) {
        messageSender.sendMessage(
            EventMessageRequest.Builder(
                contextManager.getContextWithoutUpdate(
                    namespaceAndName
                ), NAMESPACE, name, VERSION.toString()
            ).referrerDialogRequestId(referrerDialogRequestId ?: "").payload(payload.toString()).build()
        )
    }

    private var currentSessionId: String? = null

    override fun onSessionOpened(
        sessionId: String,
        domainTypes: Array<String>?,
        playServiceId: String?,
        context: DialogSessionManagerInterface.Context?
    ) {
        Logger.d(TAG, "[onSessionOpened] $sessionId")
        currentSessionId = sessionId
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(true)
            }
        }
    }

    override fun onSessionClosed(sessionId: String) {
        Logger.d(TAG, "[onSessionClosed] $sessionId")
        if (currentSessionId != sessionId) {
            Logger.e(TAG, "[onSessionClosed] current: $currentSessionId, closed: $sessionId")
        }
        currentSessionId = null
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(false)
            }
        }
        executeStopRecognition(true, ASRAgentInterface.CancelCause.SESSION_CLOSED)
    }
}