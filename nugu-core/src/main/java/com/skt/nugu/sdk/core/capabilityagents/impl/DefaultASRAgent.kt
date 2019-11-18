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
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.capabilityagents.asr.*
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentFactory
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.network.event.AsrNotifyResultPayload
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.HashMap
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DefaultASRAgent {
    private const val TAG = "DefaultASRAgent"

    const val NAME_EXPECT_SPEECH = "ExpectSpeech"
    const val NAME_RECOGNIZE = "Recognize"
    private const val NAME_NOTIFY_RESULT = "NotifyResult"

    private const val NAME_LISTEN_TIMEOUT = "ListenTimeout"
    private const val NAME_LISTEN_FAILED = "ListenFailed"
    private const val NAME_RESPONSE_TIMEOUT = "ResponseTimeout"

    const val EVENT_STOP_RECOGNIZE = "StopRecognize"

    val EXPECT_SPEECH = NamespaceAndName(
        AbstractASRAgent.NAMESPACE,
        NAME_EXPECT_SPEECH
    )
    val RECOGNIZE = NamespaceAndName(
        AbstractASRAgent.NAMESPACE,
        NAME_RECOGNIZE
    )
    val NOTIFY_RESULT = NamespaceAndName(
        AbstractASRAgent.NAMESPACE,
        NAME_NOTIFY_RESULT
    )

    private const val PAYLOAD_PLAY_SERVICE_ID = "playServiceId"

    val FACTORY = object : ASRAgentFactory {
        override fun create(
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
        ): AbstractASRAgent {
            return Impl(
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
            )
        }
    }

    internal class Impl constructor(
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
    ), SpeechProcessorInterface.OnStateChangeListener, ChannelObserver {
        private val onStateChangeListeners = HashSet<ASRAgentInterface.OnStateChangeListener>()
        private val onResultListeners = HashSet<ASRAgentInterface.OnResultListener>()
        private val onMultiturnListeners = HashSet<ASRAgentInterface.OnMultiturnListener>()

        private val executor = Executors.newSingleThreadExecutor()
        private var state = ASRAgentInterface.State.IDLE

        private var focusState = FocusState.NONE

        private var audioInputStream: SharedDataStream? = null
        private var audioFormat: AudioFormat? = null
        private var wakewordStartPosition: Long? = null
        private var wakewordEndPosition: Long? = null
        private var expectSpeechPayload: ExpectSpeechPayload? = null

        private var currentContext: String? = null

        private var initialDialogUXStateReceived: Boolean = false

        private var currentAudioProvider: AudioProvider? = null
        private val speechProcessorLock = ReentrantLock()

        private var currentSpeechProcessor: SpeechProcessorInterface
        private var nextStartSpeechProcessor: SpeechProcessorInterface
        private val serverEpdSpeechProcessor: SpeechProcessorInterface
        private val clientEpdSpeechProcessor: SpeechProcessorInterface?

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
                    Logger.w(TAG, "[onError]")
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
            serverEpdSpeechProcessor = DefaultServerSideSpeechProcessor(
                audioProvider,
                speechToTextConverterEventObserver,
                audioEncoder,
                messageSender,
                defaultEpdTimeoutMillis
            )

            clientEpdSpeechProcessor = if (endPointDetector != null) {
                DefaultClientSideSpeechProcessor(
                    audioProvider,
                    speechToTextConverterEventObserver,
                    audioEncoder,
                    messageSender,
                    endPointDetector,
                    defaultEpdTimeoutMillis
                )
            } else {
                null
            }

            val initialSpeechProcessor = clientEpdSpeechProcessor ?: serverEpdSpeechProcessor
            currentSpeechProcessor = initialSpeechProcessor
            nextStartSpeechProcessor = initialSpeechProcessor

            initialSpeechProcessor.addListener(this)
            if (initialSpeechProcessor is AbstractSpeechProcessor) {
                initialSpeechProcessor.inputProcessor = this
            }
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
                nextStartSpeechProcessor
            }

            if (speechProcessorToStart != currentSpeechProcessor) {
                currentSpeechProcessor.removeListener(this)
                currentSpeechProcessor = speechProcessorToStart
                currentSpeechProcessor.addListener(this)
                if (speechProcessorToStart is AbstractSpeechProcessor) {
                    speechProcessorToStart.inputProcessor = this
                }
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

            val audioProvider = currentSpeechProcessor.defaultAudioProvider
            currentAudioProvider = audioProvider
            if (audioProvider == null) {
                Logger.w(
                    TAG,
                    "[executeHandleExpectSpeechDirective] defaultAudioProvider is null"
                )
                setHandlingExpectSpeechFailed(
                    info,
                    "[executeHandleExpectSpeechDirective] defaultAudioProvider is null"
                )
                return
            }

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
            executeStartRecognition(audioInputStream, audioFormat, null, null, expectSpeechPayload)
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
                currentSpeechProcessor.notifyResult(state.name, result)
                setHandlingCompleted(info)
            }
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            Logger.d(TAG, "[executeSetHandlingCompleted] info: $info")
            info.result?.setCompleted()
            removeDirective(info)
        }

        private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
            Logger.d(TAG, "[executeSetHandlingFailed] info: $info")
            info.result?.setFailed(msg)
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
                        NAMESPACE, null
                    )
                ) {
                    Logger.e(
                        TAG,
                        "[executeStartRecognitionOnContextAvailable] Unable to acquire channel"
                    )
                    return false
                }
            }

            currentContext = jsonContext

            if (focusState == FocusState.FOREGROUND) {
                executeInternalStartRecognition()
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
                FocusState.BACKGROUND -> focusManager.releaseChannel(channelName, this)
                FocusState.NONE -> executeStopRecognition()
            }

            if (newFocus != FocusState.FOREGROUND) {
                Logger.d(TAG, "[executeOnFocusChanged] lost focus")
                return
            }

            if (state == ASRAgentInterface.State.RECOGNIZING) {
                return
            }

            executeInternalStartRecognition()
        }

        private fun executeInternalStartRecognition() {
            Logger.d(TAG, "[executeInternalStartRecognition]")
            executeSelectSpeechProcessor()
            currentSpeechProcessor.startProcessor(
                audioInputStream,
                audioFormat,
                currentContext,
                wakewordStartPosition,
                wakewordEndPosition,
                expectSpeechPayload
            )

            clearPreHandledExpectSpeech()
        }

        private fun removeDirective(info: DirectiveInfo) {
            if (info.directive != null && info.result != null) {
                removeDirective(info.directive.getMessageId())
            }
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

        override fun onDialogUXStateChanged(
            newState: DialogUXStateAggregatorInterface.DialogUXState,
            dialogMode: Boolean
        ) {
            Logger.d(TAG, "[onDialogUXStateChanged] newState: $newState")
            executor.submit {
                executeOnDialogUXStateChanged(newState)
            }
        }

        private fun executeOnDialogUXStateChanged(newState: DialogUXStateAggregatorInterface.DialogUXState) {
            Logger.d(TAG, "[executeOnDialogUXStateChanged] newState: $newState")

            if (initialDialogUXStateReceived) {
                initialDialogUXStateReceived = true
                return
            }

            if (newState != DialogUXStateAggregatorInterface.DialogUXState.IDLE) {
                return
            }

            if (focusState != FocusState.NONE) {
                focusManager.releaseChannel(channelName, this)
                focusState = FocusState.NONE
            }

            setState(ASRAgentInterface.State.IDLE)
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

        override fun onStateChanged(state: SpeechProcessorInterface.State) {
            Logger.d(TAG, "[SpeechProcessorInterface] state: $state")
            executor.submit {
                val aipState = when (state) {
                    SpeechProcessorInterface.State.EXPECTING_SPEECH -> {
                        ASRAgentInterface.State.LISTENING
                    }
                    SpeechProcessorInterface.State.SPEECH_START -> ASRAgentInterface.State.RECOGNIZING
                    SpeechProcessorInterface.State.SPEECH_END -> {
                        currentAudioProvider?.releaseAudioInputStream(this)
                        currentAudioProvider = null
                        ASRAgentInterface.State.BUSY
                    }
                    SpeechProcessorInterface.State.TIMEOUT -> {
                        currentAudioProvider?.releaseAudioInputStream(this)
                        currentAudioProvider = null
                        onResultListeners.forEach {
                            it.onError(ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT)
                        }
                        sendListenTimeout()
                        ASRAgentInterface.State.IDLE
                    }
                    SpeechProcessorInterface.State.STOP -> {
                        currentAudioProvider?.releaseAudioInputStream(this)
                        currentAudioProvider = null
                        onResultListeners.forEach {
                            it.onCancel()
                        }
                        ASRAgentInterface.State.IDLE
                    }
                }
                setState(aipState)
            }
        }

        override fun startRecognition(
            audioInputStream: SharedDataStream?,
            audioFormat: AudioFormat?,
            wakewordStartPosition: Long?,
            wakewordEndPosition: Long?
        ): Future<Boolean> {
            Logger.d(TAG, "[startRecognition] audioInputStream: $audioInputStream")
            return executor.submit(Callable<Boolean> {
                if (audioInputStream != null && audioFormat != null) {
                    executeStartRecognition(
                        audioInputStream,
                        audioFormat,
                        wakewordStartPosition,
                        wakewordEndPosition,
                        expectSpeechPayload
                    )
                } else {
                    val audioProvider = currentSpeechProcessor.defaultAudioProvider
                    currentAudioProvider = audioProvider
                    if (audioProvider == null) {
                        Logger.w(
                            TAG,
                            "[startRecognition] defaultAudioProvider is null"
                        )
                        return@Callable false
                    }

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
                        null,
                        expectSpeechPayload
                    )
                }
            })
        }

        override fun stopRecognition() {
            Logger.d(TAG, "[stopRecognition]")
            executor.submit {
                executeStopRecognition()
            }
        }

        fun setSpeechProcessor(speechProcessor: SpeechProcessorInterface) {
            speechProcessorLock.withLock {
                nextStartSpeechProcessor = speechProcessor
            }
        }

        fun getSpeechProcessor() = speechProcessorLock.withLock {
            nextStartSpeechProcessor
        }

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
            wakewordStartPosition: Long?,
            wakewordEndPosition: Long?,
            payload: ExpectSpeechPayload?
        ): Boolean {
            Logger.d(TAG, "[executeStartRecognition] state: $state")
            if (!canRecognizing()) {
                Logger.w(
                    TAG,
                    "[executeStartRecognition] StartRecognizing allowed in IDLE or BUSY state."
                )
                return false
            }

            this.wakewordStartPosition = wakewordStartPosition
            this.wakewordEndPosition = wakewordEndPosition
            this.audioInputStream = audioInputStream
            this.audioFormat = audioFormat
            this.expectSpeechPayload = payload


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

        private fun executeStopRecognition() {
            currentSpeechProcessor.stopProcessor()
        }

        private fun canRecognizing(): Boolean {
            // ASR은 IDLE이나 BUSY 상태일 경우에만 가능하다.
            return !state.isRecognizing() || state == ASRAgentInterface.State.BUSY || state == ASRAgentInterface.State.EXPECTING_SPEECH
        }

        override fun onSendEventFinished(dialogRequestId: String) {
            inputProcessorManager.onRequested(this, dialogRequestId)
        }

        override fun onReceiveResponse(dialogRequestId: String, header: Header) {
            if (header.namespace != NAMESPACE) {
                Logger.d(TAG, "[onReceiveResponse] $header")
                executor.submit {
                    // BUSY 상태를 해제한다.
                    if (state == ASRAgentInterface.State.BUSY) {
                        setState(ASRAgentInterface.State.IDLE)
                    }
                }
            }
        }

        override fun onResponseTimeout(dialogRequestId: String) {
            executor.submit {
                onResultListeners.forEach {
                    it.onError(ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT)
                }

                // BUSY 상태를 해제한다.
                if (state == ASRAgentInterface.State.BUSY) {
                    setState(ASRAgentInterface.State.IDLE)
                }
                sendResponseTimeoutEvent()
            }
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

        private fun sendResponseTimeoutEvent() {
            Logger.d(TAG, "[sendResponseTimeoutEvent]")
            executor.submit {
                currentSpeechProcessor.notifyError("Response Timeout")
                sendEvent(NAME_RESPONSE_TIMEOUT, JsonObject())
            }
        }

        private fun sendEvent(name: String, payload: JsonObject) {
            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    messageSender.sendMessage(createMessage(jsonContext))
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                    currentContext?.let {
                        messageSender.sendMessage(createMessage(it))
                    }
                }

                private fun createMessage(jsonContext: String): EventMessageRequest =
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION).payload(payload.toString()).build()
            }, namespaceAndName)
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
}