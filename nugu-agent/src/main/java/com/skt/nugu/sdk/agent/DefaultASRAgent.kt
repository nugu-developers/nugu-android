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
import com.skt.nugu.sdk.agent.asr.impl.DefaultClientSpeechRecognizer
import com.skt.nugu.sdk.agent.asr.impl.DefaultServerSpeechRecognizer
import com.skt.nugu.sdk.agent.asr.audio.AudioProvider
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.util.HashMap
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DefaultASRAgent(
    inputProcessorManager: InputProcessorManagerInterface,
    focusManager: FocusManagerInterface,
    messageSender: MessageSender,
    contextManager: ContextManagerInterface,
    dialogSessionManager: DialogSessionManagerInterface,
    audioProvider: AudioProvider,
    audioEncoder: Encoder,
    endPointDetector: AudioEndPointDetector?,
    defaultEpdTimeoutMillis: Long,
    channelName: String
) : AbstractASRAgent(
    inputProcessorManager,
    focusManager,
    messageSender,
    contextManager,
    dialogSessionManager,
    audioProvider,
    audioEncoder,
    endPointDetector,
    defaultEpdTimeoutMillis,
    channelName
), SpeechRecognizer.OnStateChangeListener, ChannelObserver, DialogUXStateAggregatorInterface.Listener {
    companion object {
        private const val TAG = "DefaultASRAgent"

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

    private var audioInputStream: SharedDataStream? = null
    private var audioFormat: AudioFormat? = null
    private var wakeupInfo: WakeupInfo? = null
    private var expectSpeechPayload: ExpectSpeechPayload? = null
    private var endPointDetectorParam: EndPointDetectorParam? = null

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
            override fun onNoneResult() {
                onResultListeners.forEach {
                    it.onNoneResult()
                }
            }

            override fun onPartialResult(result: String) {
                Logger.d(TAG, "[onPartialResult] $result")
                onResultListeners.forEach {
                    it.onPartialResult(result)
                }
            }

            override fun onCompleteResult(result: String) {
                onResultListeners.forEach {
                    it.onCompleteResult(result)
                }
            }

            override fun onError(errorType: ASRAgentInterface.ErrorType) {
                Logger.w(TAG, "[onError] $errorType")
                onResultListeners.forEach {
                    it.onError(errorType)
                }
            }

            override fun onCancel() {
                Logger.d(TAG, "[onCancel]")
                onResultListeners.forEach {
                    it.onCancel()
                }
            }
        }

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName(
            "supportedInterfaces",
            NAMESPACE
        )

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
        contextManager.setState(namespaceAndName, buildContext(), StateRefreshPolicy.NEVER)
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
                    info,
                    "[executePreHandleExpectSpeechDirective] invalid payload"
                )
            } else if (state != ASRAgentInterface.State.IDLE && state != ASRAgentInterface.State.BUSY) {
                setHandlingExpectSpeechFailed(
                    info,
                    "[executePreHandleExpectSpeechDirective] not allowed state($state)"
                )
            } else {
                executePreHandleExpectSpeechInternal(payload)
            }
        }
    }

    private fun executePreHandleExpectSpeechInternal(payload: ExpectSpeechPayload) {
        Logger.d(TAG, "[executePreHandleExpectSpeechInternal] success, payload: $payload")
        expectSpeechPayload = payload
        dialogSessionManager.openSession(
            payload.sessionId,
            payload.property,
            payload.domainTypes,
            payload.playServiceId
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
        if (!canRecognizing()) {
            Logger.w(
                TAG,
                "[executeHandleExpectSpeechDirective] ExpectSpeech only allowed in IDLE or BUSY state."
            )
            setHandlingExpectSpeechFailed(
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
                info,
                "[executeHandleExpectSpeechDirective] audioInputStream is null"
            )
            return
        }

        // Here we do not check validation of payload due to checked at preHandleExpectSpeech already.
        if (expectSpeechPayload == null) {
            Logger.e(TAG, "[executeHandleExpectSpeechDirective] re-parse payload due to loss")
            // re parse payload here.
            val payload = parseExpectSpeechPayload(info.directive)
            expectSpeechPayload = payload
        }

        setState(ASRAgentInterface.State.EXPECTING_SPEECH)
        executeStartRecognition(audioInputStream, audioFormat, null, expectSpeechPayload, null)
        setHandlingCompleted(info)
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

    private fun setHandlingExpectSpeechFailed(info: DirectiveInfo, msg: String) {
        setHandlingFailed(info, msg)
        sendListenFailed()
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
            VERSION
        )
    }.toString()

    override fun onFocusChanged(newFocus: FocusState) {
        executor.submit {
            executeOnFocusChanged(newFocus)
        }
    }

    private fun executeStartRecognitionOnContextAvailable(jsonContext: String): Boolean {
        Logger.d(
            TAG,
            "[executeStartRecognitionOnContextAvailable] state: $state, focusState: $focusState"
        )
        if (state == ASRAgentInterface.State.RECOGNIZING) {
            Logger.e(
                TAG,
                "[executeStartRecognitionOnContextAvailable] Not permmited in current state: $state"
            )
            return false
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
                return false
            }
            contextForRecognitionOnForegroundFocus = jsonContext
        }

        if (focusState == FocusState.FOREGROUND) {
            executeInternalStartRecognition(jsonContext)
        }

        return true
    }

    private fun executeStartRecognitionOnContextFailure(error: ContextRequester.ContextRequestError) {
        Logger.w(TAG, "[executeStartRecognitionOnContextFailure] error: $error")
    }

    private fun executeOnFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[executeOnFocusChanged] newFocus: $newFocus")

        focusState = newFocus

        when (newFocus) {
            FocusState.FOREGROUND -> {
                val context = contextForRecognitionOnForegroundFocus
                contextForRecognitionOnForegroundFocus = null
                if (state != ASRAgentInterface.State.RECOGNIZING && context != null) {
                    executeInternalStartRecognition(context)
                }
            }
            FocusState.BACKGROUND -> focusManager.releaseChannel(channelName, this)
            FocusState.NONE -> executeStopRecognition(true)
        }
    }
    
    private fun executeInternalStartRecognition(context: String) {
        Logger.d(TAG, "[executeInternalStartRecognition]")

        val inputStream = audioInputStream
        if (inputStream == null) {
            Logger.e(TAG, "[executeInternalStartRecognition] audioInputProcessor is null")
            return
        }

        val inputFormat = audioFormat
        if (inputFormat == null) {
            Logger.e(TAG, "[executeInternalStartRecognition] audioFormat is null")
            return
        }

        executeSelectSpeechProcessor()
        currentSpeechRecognizer.start(
            inputStream,
            inputFormat,
            context,
            wakeupInfo,
            expectSpeechPayload,
            endPointDetectorParam ?: EndPointDetectorParam(defaultEpdTimeoutMillis.div(1000).toInt()),
            object : ASRAgentInterface.OnResultListener {
                override fun onNoneResult() {
                    speechToTextConverterEventObserver.onNoneResult()
                }

                override fun onPartialResult(result: String) {
                    speechToTextConverterEventObserver.onPartialResult(result)
                }

                override fun onCompleteResult(result: String) {
                    speechToTextConverterEventObserver.onCompleteResult(result)
                }

                override fun onError(type: ASRAgentInterface.ErrorType) {
                    if (type == ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT) {
                        sendEvent(NAME_RESPONSE_TIMEOUT, JsonObject())
                    } else if (type == ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT) {
                        sendListenTimeout()
                    }
                    speechToTextConverterEventObserver.onError(type)
                }

                override fun onCancel() {
                    speechToTextConverterEventObserver.onCancel()
                }
            }
        )

        clearPreHandledExpectSpeech()
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

    override fun onDialogUXStateChanged(
        newState: DialogUXStateAggregatorInterface.DialogUXState,
        dialogMode: Boolean
    ) {
        executor.submit {
            if(newState != DialogUXStateAggregatorInterface.DialogUXState.IDLE) {
                return@submit
            }

            if(focusState != FocusState.NONE) {
                focusManager.releaseChannel(channelName, this)
                focusState = FocusState.NONE
            }
        }
    }

    override fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakeupInfo: WakeupInfo?,
        param: EndPointDetectorParam?
    ): Future<Boolean> {
        Logger.d(TAG, "[startRecognition] audioInputStream: $audioInputStream")
        return executor.submit(Callable<Boolean> {
            if (audioInputStream != null && audioFormat != null) {
                executeStartRecognition(
                    audioInputStream,
                    audioFormat,
                    wakeupInfo,
                    expectSpeechPayload,
                    param
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
                    return@Callable false
                }

                executeStartRecognition(
                    newAudioInputStream,
                    newAudioFormat,
                    null,
                    expectSpeechPayload,
                    param
                )
            }
        })
    }

    override fun stopRecognition(cancel: Boolean) {
        Logger.d(TAG, "[stopRecognition]")
        executor.submit {
            executeStopRecognition(cancel)
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
        if (state == ASRAgentInterface.State.IDLE && expectSpeechPayload == null) {
            currentSessionId?.let {
                dialogSessionManager.closeSession()
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
        expectSpeechPayload = null
    }

    private fun executeStartRecognition(
        audioInputStream: SharedDataStream,
        audioFormat: AudioFormat,
        wakeupInfo: WakeupInfo?,
        payload: ExpectSpeechPayload?,
        param: EndPointDetectorParam?
    ): Boolean {
        Logger.d(TAG, "[executeStartRecognition] state: $state")
        if (!canRecognizing()) {
            Logger.w(
                TAG,
                "[executeStartRecognition] StartRecognizing allowed in IDLE or BUSY state."
            )
            return false
        }

        this.wakeupInfo = wakeupInfo
        this.audioInputStream = audioInputStream
        this.audioFormat = audioFormat
        this.expectSpeechPayload = payload
        this.endPointDetectorParam = param


        val waitResult = CountDownLatch(1)
        var result = true
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                Logger.d(TAG, "[onContextAvailable]")
                result = executeStartRecognitionOnContextAvailable(jsonContext)
                waitResult.countDown()
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                executeStartRecognitionOnContextFailure(error)
                result = false
                waitResult.countDown()
            }
        })
        waitResult.await()
        return result
    }

    private fun executeStopRecognition(cancel: Boolean) {
        currentSpeechRecognizer.stop(cancel)
    }

    private fun canRecognizing(): Boolean {
        // ASR은 IDLE이나 BUSY 상태일 경우에만 가능하다.
        return !state.isRecognizing() || state == ASRAgentInterface.State.BUSY || state == ASRAgentInterface.State.EXPECTING_SPEECH
    }

    private fun sendListenFailed() {
        JsonObject().apply {
            expectSpeechPayload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_LISTEN_FAILED, this)
        }
    }

    private fun sendListenTimeout() {
        JsonObject().apply {
            expectSpeechPayload?.let {
                addProperty(PAYLOAD_PLAY_SERVICE_ID, it.playServiceId)
            }
        }.apply {
            sendEvent(NAME_LISTEN_TIMEOUT, this)
        }
    }

    private fun sendEvent(name: String, payload: JsonObject) {
        messageSender.sendMessage(
            EventMessageRequest.Builder(
                contextManager.getContextWithoutUpdate(
                    namespaceAndName
                ), NAMESPACE, name, VERSION
            ).payload(payload.toString()).build()
        )
    }

    private var currentSessionId: String? = null

    override fun onSessionOpened(
        sessionId: String,
        property: String?,
        domainTypes: Array<String>?,
        playServiceId: String?
    ) {
        currentSessionId = sessionId
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(true)
            }
        }
    }

    override fun onSessionClosed(sessionId: String) {
        if (currentSessionId != sessionId) {
            Logger.e(TAG, "[onSessionClosed] current: $currentSessionId, closed: $sessionId")
        }
        currentSessionId = null
        executor.submit {
            onMultiturnListeners.forEach {
                it.onMultiturnStateChanged(false)
            }
        }
    }
}