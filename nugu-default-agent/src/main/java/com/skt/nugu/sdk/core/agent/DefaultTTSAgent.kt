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
package com.skt.nugu.sdk.core.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.agent.payload.PlayStackControl
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.mediaplayer.ErrorType
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.SourceId
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.utils.Logger
import com.skt.nugu.sdk.core.interfaces.utils.UUIDGeneration
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerControlInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.util.TimeoutCondition
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
//import javax.annotation.concurrent.GuardedBy
//import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.withLock

class DefaultTTSAgent(
    speechPlayer: MediaPlayerInterface,
    messageSender: MessageSender,
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface,
    channelName: String
) : AbstractTTSAgent(
    speechPlayer,
    messageSender,
    focusManager,
    contextManager,
    playSynchronizer,
    playStackManager,
    inputProcessorManager,
    channelName
), MediaPlayerControlInterface.PlaybackEventListener {

    internal data class SpeakPayload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("text")
        val text: String,
        @SerializedName("token")
        val token: String,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    //@ThreadSafe
    companion object {
        private const val TAG = "DefaultTTSAgent"

        const val NAME_SPEAK = "Speak"
        const val NAME_STOP = "Stop"

        val SPEAK = NamespaceAndName(
            NAMESPACE,
            NAME_SPEAK
        )
        val STOP = NamespaceAndName(
            NAMESPACE,
            NAME_STOP
        )

        const val EVENT_SPEECH_STARTED = "SpeechStarted"
        const val EVENT_SPEECH_FINISHED = "SpeechFinished"
        const val EVENT_SPEECH_STOPPED = "SpeechStopped"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"

        const val NAME_SPEECH_PLAY = "SpeechPlay"

        enum class Format {
            TEXT,
            SKML
        }
    }

    internal inner class SpeakDirectiveInfo(
        info: DirectiveInfo,
        val payload: SpeakPayload
    ) : PlaySynchronizerInterface.SynchronizeObject
        , DirectiveInfo by info {
        var isPlaybackInitiated = false
        var isDelayedCancel = false
        var cancelByStop = false

        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                executor.submit {
                    removeDirective(directive.getMessageId())
                    clear()

                    if (this@SpeakDirectiveInfo == currentInfo) {
                        Logger.d(TAG, "[onReleased] this is currentInfo")

                        currentInfo = null
                        sourceId = SourceId.ERROR()

                        val nextInfo = preparedSpeakInfo
                        if (nextInfo != null) {
                            preparedSpeakInfo = null
                            executePlaySpeakInfo(nextInfo)
                        } else {
                            releaseForegroundFocus()
                        }
                    } else {
                        Logger.d(TAG, "[onReleased] (focus: $currentFocus)")
                    }
                }
            }

            override fun onDenied() {
                // nothing to do
            }
        }

        var playContext = payload.playStackControl?.getPushPlayServiceId()?.let {
            PlayStackManagerInterface.PlayContext(it, 100)
        }

        fun clear() {
            directive.destroy()
        }

        override fun getDialogRequestId(): String = directive.getDialogRequestId()

        fun getPlayServiceId(): String? = payload.playServiceId

        override fun requestReleaseSync(immediate: Boolean) {
            Logger.d(TAG, "[requestReleaseSync] immediate: $immediate")
            executor.submit {
                executeCancel(this)
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = HashSet<TTSAgentInterface.Listener>()
    private val requestListenerMap =
        ConcurrentHashMap<String, TTSAgentInterface.OnPlaybackListener>()

    private var preparedSpeakInfo: SpeakDirectiveInfo? = null
    private var currentInfo: SpeakDirectiveInfo? = null

    private val stateLock = ReentrantLock()
    //    @GuardedBy("stateLock")
    private var currentState = TTSAgentInterface.State.IDLE
    //    @GuardedBy("stateLock")
    private var desireState = TTSAgentInterface.State.IDLE

    private var sourceId: SourceId = SourceId.ERROR()

    private var isAlreadyStopping = false
    private var isAlreadyPausing = false

    private var initialDialogUXStateReceived = false

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    init {
        Logger.d(TAG, "[init]")
        speechPlayer.setPlaybackEventListener(this)
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun addListener(listener: TTSAgentInterface.Listener) {
        Logger.d(TAG, "[addListener] listener: $listener")
        executor.submit {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: TTSAgentInterface.Listener) {
        Logger.d(TAG, "[removeListener] listener: $listener")
        executor.submit {
            listeners.remove(listener)
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandleDirective] info: $info")
        if (info.directive.getNamespaceAndName() == SPEAK) {
            executor.submit {
                executePreHandleSpeakDirective(info)
            }
        }
    }

    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleDirective] info: $info")
        executor.submit {
            executeHandle(info)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[cancelDirective] info: $info")
        executor.submit {
            executeCancel(info)
        }
    }

    private fun executePreHandleSpeakDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[executePreHandleSpeakDirective] info: $info")
        val speakInfo = createValidateSpeakInfo(info)

        if (speakInfo == null) {
            setHandlingInvalidSpeakDirectiveReceived(info)
            return
        }

        executePrepareSpeakInfo(speakInfo)
    }

    private fun setHandlingInvalidSpeakDirectiveReceived(info: DirectiveInfo) {
        Logger.d(TAG, "[setHandlingInvalidSpeakDirectiveReceived] info: $info")
        info.result.setFailed("Invalid Speak Directive")
        requestListenerMap.remove(info.directive.getDialogRequestId())?.onError()
    }

    private fun executeHandle(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandle] info: $info")

        when (info.directive.getNamespaceAndName()) {
            SPEAK -> {
                executeHandleSpeakDirective(info)
            }
            STOP -> executeHandleStopDirective(info)
        }
    }

    private fun executeHandleSpeakDirective(info: DirectiveInfo) {
        val nextSpeakInfo = preparedSpeakInfo
        if (nextSpeakInfo == null) {
            Logger.d(TAG, "[executeHandleSpeakDirective] The preparedSpeakInfo is null")
            return
        }

        if (nextSpeakInfo.directive.getMessageId() != info.directive.getMessageId()) {
            Logger.e(TAG, "[executeHandleSpeakDirective] Not matched info.")
            return
        }

        if (currentInfo == null) {
            preparedSpeakInfo = null
            executePlaySpeakInfo(nextSpeakInfo)
        }
    }

    private fun executeHandleStopDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandleStopDirective] info: $info")

        if (currentInfo != null) {
            //            if(info.directive.getMessageId() == it.directive.getMessageId()) {
            executeCancelCurrentSpeakInfo()
//            }
        } else {
            Logger.d(TAG, "[executeHandleStopDirective] ignore : currentInfo is null")
        }
    }

    private fun executeCancel(info: DirectiveInfo) {
        Logger.d(TAG, "[executeCancel] info: $info")
        when (info.directive.getMessageId()) {
            preparedSpeakInfo?.directive?.getMessageId() -> {
                executeCancelPreparedSpeakInfo()
            }
            currentInfo?.directive?.getMessageId() -> {
                executeCancelCurrentSpeakInfo()
            }
            else -> {
                Logger.d(TAG, "[executeCancel] skip cancel. (not valid info)")
            }
        }
    }

    override fun stopTTS(cancelAssociation: Boolean) {
        executor.submit {
            Logger.d(TAG, "[stopTTS] cancelAssociation: $cancelAssociation")
            executeCancelCurrentSpeakInfo(cancelAssociation)
        }
    }

    //    @GuardedBy("stateLock")
    private fun setDesireState(newFocus: FocusState) {
        desireState = when (newFocus) {
            FocusState.FOREGROUND -> TTSAgentInterface.State.PLAYING
            else -> TTSAgentInterface.State.STOPPED
        }
    }

    private fun setDesireState(state: TTSAgentInterface.State) {
        stateLock.withLock {
            desireState = state
        }
    }

    private fun setCurrentState(state: TTSAgentInterface.State) {
        stateLock.withLock {
            Logger.d(TAG, "[setCurrentState] state: $state")
            currentState = state
            currentInfo?.directive?.let {
                if (state == TTSAgentInterface.State.PLAYING) {
                    requestListenerMap[it.getDialogRequestId()]?.onStart()
                } else if (state == TTSAgentInterface.State.FINISHED || state == TTSAgentInterface.State.STOPPED) {
                    requestListenerMap.remove(it.getDialogRequestId())?.onFinish()
                }

                notifyObservers(state, it.getDialogRequestId())
            }
        }
    }

    private fun notifyObservers(state: TTSAgentInterface.State, dialogRequestId: String) {
        for (observer in listeners) {
            observer.onStateChanged(state, dialogRequestId)
        }
    }

    private fun executePrepareSpeakInfo(speakInfo: SpeakDirectiveInfo) {
        executeCancelAllSpeakInfo()

        with(speakInfo) {
            playSynchronizer.prepareSync(this)
            preparedSpeakInfo = this
        }
    }

    private fun executeCancelAllSpeakInfo() {
        executeCancelPreparedSpeakInfo()
        executeCancelCurrentSpeakInfo()
    }

    private fun executeCancelPreparedSpeakInfo() {
        val info = preparedSpeakInfo
        preparedSpeakInfo = null

        if (info == null) {
            Logger.d(TAG, "[executeCancelPreparedSpeakInfo] preparedSpeakInfo is null.")
            return
        }
        Logger.d(TAG, "[executeCancelPreparedSpeakInfo] cancel preparedSpeakInfo : $info")

        with(info) {
            result.setFailed("Canceled by the other speak directive.")
            removeDirective(directive.getMessageId())
            releaseSyncImmediately(this)
        }
    }

    private fun executeCancelCurrentSpeakInfo(cancelAssociation: Boolean = true) {
        val info = currentInfo

        if (info == null) {
            Logger.d(TAG, "[executeCancelCurrentSpeakInfo] currentSpeakInfo is null.")
            return
        }
        Logger.d(TAG, "[executeCancelCurrentSpeakInfo] cancel currentSpeakInfo : $currentInfo")

        with(info) {
            cancelByStop = cancelAssociation
            if (isPlaybackInitiated) {
                setDesireState(TTSAgentInterface.State.STOPPED)
                stopPlaying()
            } else {
                isDelayedCancel = true
            }
        }
    }

    private fun executePlaySpeakInfo(speakInfo: SpeakDirectiveInfo) {
        Logger.d(TAG, "[executePlaySpeakInfo] $speakInfo")
        currentInfo = speakInfo

        val text = speakInfo.payload.text
        listeners.forEach {
            it.onReceiveTTSText(text, speakInfo.getDialogRequestId())
        }

        if (currentFocus == FocusState.FOREGROUND) {
            onFocusChanged(currentFocus)
        } else {
            if (!focusManager.acquireChannel(
                    channelName,
                    this,
                    NAMESPACE
                )
            ) {
                Logger.e(TAG, "[executePlaySpeakInfo] not registered channel!")
            }
        }
    }

    private var currentFocus: FocusState = FocusState.NONE

    override fun onFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[onFocusChanged] newFocus: $newFocus")

        currentFocus = newFocus
        setDesireState(newFocus)
        if (currentState == desireState) {
            return
        }

        when (newFocus) {
            FocusState.FOREGROUND -> {
                executor.submit {
                    currentInfo?.let {
                        startSync(it)
                    }
                }
            }
            FocusState.BACKGROUND -> {
                transitState()
            }
            FocusState.NONE -> {
                transitState()
            }
        }
    }

    private fun startSync(info: SpeakDirectiveInfo) {
        playSynchronizer.startSync(info,
            object : PlaySynchronizerInterface.OnRequestSyncListener {
                override fun onGranted() {
                    transitState()
                }

                override fun onDenied() {
                }
            })
        info.playContext?.let {
            playStackManager.add(it)
        }
    }

    private fun executeStateChange() {
        val newState = stateLock.withLock {
            desireState
        }
        Logger.d(TAG, "[executeStateChange] newState: $newState")

        when (newState) {
            TTSAgentInterface.State.PLAYING -> {
                currentInfo?.apply {
                    isPlaybackInitiated = true
                    startPlaying(this)
                }
            }

            TTSAgentInterface.State.STOPPED,
            TTSAgentInterface.State.FINISHED -> {
                currentInfo?.apply {
                    result.setCompleted()
                    if (isPlaybackInitiated) {
                        stopPlaying()
                    } else {
                        releaseSyncImmediately(this)
                    }
                }
            }
            else -> {
            }
        }
    }

    private fun startPlaying(info: SpeakDirectiveInfo) {
        info.directive.getAttachmentReader()?.let {
            with(speechPlayer.setSource(it)) {
                sourceId = this
                Logger.d(TAG, "[startPlaying] sourceId: $sourceId, info: $info")
                when {
                    isError() -> executePlaybackError(
                        ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "setSource failed"
                    )
                    !speechPlayer.play(this) -> executePlaybackError(
                        ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "playFailed"
                    )
                    else -> {
                        isAlreadyPausing = false
                        isAlreadyStopping = false
                    }
                }
            }
        }
    }

    private fun stopPlaying() {
        Logger.d(TAG, "[stopPlaying]")

        when {
            sourceId.isError() -> {
            }
            isAlreadyStopping -> {
            }
            !speechPlayer.stop(sourceId) -> {
            }
            else -> {
                isAlreadyStopping = true
            }
        }
    }

    private fun releaseForegroundFocus() {
        Logger.d(TAG, "[releaseForegroundFocus]")
        stateLock.withLock {
            currentFocus = FocusState.NONE
        }
        focusManager.releaseChannel(channelName, this)
    }

    private fun createValidateSpeakInfo(
        info: DirectiveInfo
    ): SpeakDirectiveInfo? {
        Logger.d(TAG, "[createValidateSpeakInfo]")
        if (info.directive.getName() != NAME_SPEAK) {
            Logger.d(TAG, "[createValidateSpeakInfo] is not speak directive")
            return null
        }

        val payload =
            MessageFactory.create(info.directive.payload, SpeakPayload::class.java)
        if (payload == null) {
            Logger.w(
                TAG,
                "[createValidateSpeakInfo] invalid payload: ${info.directive.payload}"
            )
            return null
        }

        return SpeakDirectiveInfo(info, payload)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[SPEAK] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )
        configuration[STOP] = BlockingPolicy()

        return configuration
    }

    private fun releaseSync(info: SpeakDirectiveInfo) {
        playSynchronizer.releaseSync(info, info.onReleaseCallback)
        info.playContext?.let {
            playStackManager.removeDelayed(it, 7000L)
        }
    }

    private fun releaseSyncImmediately(info: SpeakDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info, info.onReleaseCallback)
        info.playContext?.let {
            playStackManager.remove(it)
        }
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            if (currentState == TTSAgentInterface.State.PLAYING) {
                if (currentInfo == null) {
                    Logger.e(TAG, "[provideState] failed: currentInfo is null")
                    return@submit
                }

                if (sourceId.isError()) {
                    Logger.e(TAG, "[provideState] failed: sourceId is error")
                    return@submit
                }
            }

            contextSetter.setState(
                namespaceAndName,
                buildContext().toString(),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    private fun buildContext(): JsonObject = JsonObject().apply {
        addProperty("version", VERSION)
        addProperty(
            "ttsActivity", when (currentState) {
                TTSAgentInterface.State.PLAYING -> TTSAgentInterface.State.PLAYING.name
                TTSAgentInterface.State.STOPPED -> TTSAgentInterface.State.STOPPED.name
                else -> TTSAgentInterface.State.FINISHED.name
            }
        )
    }

    private fun transitState() {
        val futureExecuteStateChange = executor.submit {
            executeStateChange()
        }

        futureExecuteStateChange.get() // wait

        object : TimeoutCondition<Boolean>(100L, { currentState == desireState }) {
            override fun onCondition(): Boolean {
                return true
            }

            override fun onTimeout(): Boolean {
                return false
            }
        }.get()

        executor.submit {
            val speakInfo = currentInfo
            if (speakInfo != null && speakInfo.isDelayedCancel) {
                executeCancelCurrentSpeakInfo()
            }
        }
    }

    override fun onPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStarted] id: $id")
        executeOnPlaybackEvent(id) {
            executePlaybackStarted()
        }
    }

    override fun onPlaybackPaused(id: SourceId) {
        Logger.w(TAG, "[onPlaybackPaused] id: $id")
    }

    override fun onPlaybackResumed(id: SourceId) {
        Logger.w(TAG, "[onPlaybackResumed] id: $id")
    }

    override fun onPlaybackStopped(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStopped] id: $id")
        executeOnPlaybackEvent(id) {
            executePlaybackStopped()
        }
    }

    override fun onPlaybackFinished(id: SourceId) {
        Logger.d(TAG, "[onPlaybackFinished] id: $id")
        executeOnPlaybackEvent(id) {
            executePlaybackFinished()
        }
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.e(TAG, "[onPlaybackError] id: $id, type: $type, error: $error")
        executor.submit {
            executePlaybackError(type, error)
        }
    }

    private fun executeOnPlaybackEvent(id: SourceId, event: () -> Unit) {
        if (id.id != sourceId.id) {
            executor.submit {
                executePlaybackError(
                    ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                    event.toString()
                )
            }
        } else {
            executor.submit {
                event.invoke()
            }
        }
    }

    private fun executePlaybackStarted() {
        Logger.d(TAG, "[executePlaybackStarted] $currentInfo")
        if (currentInfo == null) {
            return
        }
        setCurrentState(TTSAgentInterface.State.PLAYING)
        sendEventWithToken(
            NAMESPACE,
            EVENT_SPEECH_STARTED
        )
    }

    private fun executePlaybackStopped() {
        stateLock.withLock {
            if (currentState == TTSAgentInterface.State.STOPPED) {
                return
            }
        }
        Logger.d(TAG, "[executePlaybackStopped] $currentInfo")
        val info = currentInfo ?: return
        setCurrentState(TTSAgentInterface.State.STOPPED)
        sendEventWithToken(
            NAMESPACE,
            EVENT_SPEECH_STOPPED
        )

        with(info) {
            if (cancelByStop) {
                result.setFailed("playback stopped")
                releaseSyncImmediately(this)
            } else {
                result.setCompleted()
                releaseSync(this)
            }
        }
    }

    private fun executePlaybackFinished() {
        stateLock.withLock {
            if (currentState == TTSAgentInterface.State.FINISHED) {
                return
            }
        }

        Logger.d(TAG, "[executePlaybackFinished] $currentInfo")

        val info = currentInfo ?: return
        setCurrentState(TTSAgentInterface.State.FINISHED)
        sendEventWithToken(
            NAMESPACE,
            EVENT_SPEECH_FINISHED
        )

        setHandlingCompleted()
        releaseSync(info)
    }

    private fun executePlaybackError(type: ErrorType, error: String) {
        stateLock.withLock {
            if (currentState == TTSAgentInterface.State.STOPPED) {
                return
            }
        }

        Logger.e(TAG, "[executePlaybackError] type: $type, error: $error")
        val info = currentInfo ?: return

        setCurrentState(TTSAgentInterface.State.STOPPED)
        with(info) {
            result.setFailed("Playback Error (type: $type, error: $error)")
        }

        releaseSync(info)
    }

    private fun sendEventWithToken(namespace: String, name: String) {
        val info = currentInfo
        info?.getPlayServiceId()?.let {
            if (it.isNotBlank()) {
                contextManager.getContext(object :
                    ContextRequester {
                    override fun onContextAvailable(jsonContext: String) {
                        val messageRequest =
                            EventMessageRequest.Builder(jsonContext, namespace, name, VERSION)
                                .payload(
                                    JsonObject().apply {
                                        addProperty(KEY_PLAY_SERVICE_ID, it)
                                    }.toString()
                                )
                                .referrerDialogRequestId(info.getDialogRequestId())
                                .build()
                        messageSender.sendMessage(messageRequest)

                        Logger.d(TAG, "[sendEventWithToken] $messageRequest")
                    }

                    override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                    }
                }, namespaceAndName)
            }
        }
    }

    private fun setHandlingCompleted(info: SpeakDirectiveInfo? = currentInfo) {
        info?.result?.setCompleted()
    }

    override fun requestTTS(
        text: String,
        playServiceId: String,
        listener: TTSAgentInterface.OnPlaybackListener?
    ) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                val dialogRequestId = UUIDGeneration.timeUUID().toString()
                val messageRequest =
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        NAME_SPEECH_PLAY,
                        VERSION
                    ).dialogRequestId(dialogRequestId)
                        .payload(JsonObject().apply {
                            addProperty("format", Format.TEXT.name)
                            addProperty("text", text)
                            addProperty("playServiceId", playServiceId)
                            addProperty("token", UUIDGeneration.timeUUID().toString())
                        }.toString())
                        .build()

                listener?.let {
                    requestListenerMap[dialogRequestId] = it
                }
                messageSender.sendMessage(messageRequest)
                onSendEventFinished(messageRequest.dialogRequestId)
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
                listener?.onError()
            }
        }, namespaceAndName)
    }

    override fun onSendEventFinished(dialogRequestId: String) {
        inputProcessorManager.onRequested(this, dialogRequestId)
    }

    override fun onReceiveResponse(dialogRequestId: String, header: Header) {

    }

    override fun onResponseTimeout(dialogRequestId: String) {
        requestListenerMap.remove(dialogRequestId)?.onError()
    }
}