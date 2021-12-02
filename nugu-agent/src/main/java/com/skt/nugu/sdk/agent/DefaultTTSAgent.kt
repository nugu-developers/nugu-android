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
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.common.tts.TTSPlayContextProvider
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerControlInterface
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.mediaplayer.SourceId
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.tts.handler.StopDirectiveHandler
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.display.LayerType
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class DefaultTTSAgent(
    private val speechPlayer: MediaPlayerInterface,
    private val messageSender: MessageSender,
    private val focusManager: SeamlessFocusManagerInterface,
    private val contextManager: ContextManagerInterface,
    private val playSynchronizer: PlaySynchronizerInterface,
    private val interLayerDisplayPolicyManager: InterLayerDisplayPolicyManager,
    private val cancelPolicyOnStopTTS: DirectiveHandlerResult.CancelPolicy,
    private val channelName: String
) : AbstractCapabilityAgent(NAMESPACE)
    , TTSAgentInterface
    , MediaPlayerControlInterface.PlaybackEventListener
    , PlayStackManagerInterface.PlayContextProvider
    , StopDirectiveHandler.Controller
    , InterLayerDisplayPolicyManager.Listener
{

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

        const val NAMESPACE = "TTS"
        private val VERSION = Version(1, 3)

        private const val NAME_SPEAK = "Speak"

        val SPEAK = NamespaceAndName(
            NAMESPACE,
            NAME_SPEAK
        )

        private const val EVENT_SPEECH_STARTED = "SpeechStarted"
        private const val EVENT_SPEECH_FINISHED = "SpeechFinished"
        private const val EVENT_SPEECH_STOPPED = "SpeechStopped"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"

        const val NAME_SPEECH_PLAY = "SpeechPlay"
    }

    internal interface OnSpeakDirectiveFinishListener {
        fun onFinish()
    }

    internal inner class SpeakDirectiveInfo(
        info: DirectiveInfo,
        val payload: SpeakPayload
    ) : DirectiveInfo by info, SeamlessFocusManagerInterface.Requester {
        private val finishLock = ReentrantLock()
        private var isResultHandled = false
        private val finishListeners = HashSet<OnSpeakDirectiveFinishListener>()

        var sourceId: SourceId = SourceId.ERROR()
        var isPlaybackInitiated = false
        var isDelayedCancel = false
        var isHandled = false
        var cancelByStop = false
        var state = TTSAgentInterface.State.IDLE

        val focusChannel: SeamlessFocusManagerInterface.Channel = SeamlessFocusManagerInterface.Channel(channelName, object: ChannelObserver {
            override fun onFocusChanged(newFocus: FocusState) {
                try {
                    Logger.d(TAG, "[onFocusChanged] newFocus: $newFocus, state: $state")
                    executor.submit {
                        when (newFocus) {
                            FocusState.FOREGROUND -> {
                                val countDownLatch = CountDownLatch(1)

                                playSynchronizer.startSync(playSyncObject,
                                    object : PlaySynchronizerInterface.OnRequestSyncListener {
                                        override fun onGranted() {
                                            val text = payload.text
                                            interLayerDisplayPolicyManager.onPlayStarted(layerForDisplayPolicy)
                                            listeners.forEach {listener->
                                                listener.onReceiveTTSText(text, directive.getDialogRequestId())
                                            }

                                            isPlaybackInitiated = true
                                            startPlaying()
                                            countDownLatch.countDown()
                                        }

                                        override fun onDenied() {
                                            countDownLatch.countDown()
                                        }
                                    })
                                countDownLatch.await()
                            }
                            FocusState.BACKGROUND,
                            FocusState.NONE -> {
                                onFocusLost()
                            }
                        }

                        if (isDelayedCancel) {
                            executeCancelCurrentSpeakInfo()
                        }
                    }.get(100, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    Logger.w(TAG, "[onFocusChanged] newFocus: $newFocus", e)
                }
            }

            private fun onFocusLost() {
                if (state == TTSAgentInterface.State.STOPPED || state == TTSAgentInterface.State.FINISHED) {
                    return
                }

                if (isResultHandled) {
                    return
                }

                if (isPlaybackInitiated) {
                    stopPlaying(this@SpeakDirectiveInfo)
                    waitUntilNotPlaying()
                } else {
                    setHandlingCompleted()
                    executeReleaseSyncImmediately(this@SpeakDirectiveInfo)
                    executeTryReleaseFocus(this@SpeakDirectiveInfo)
                }
            }

            private fun waitUntilNotPlaying() {
                if(state != TTSAgentInterface.State.PLAYING) {
                    Logger.d(TAG, "[waitUntilNotPlaying] no need to wait")
                    return
                }

                val startTime = System.currentTimeMillis()
                while(System.currentTimeMillis() - startTime < 200L) {
                    if(state != TTSAgentInterface.State.PLAYING) {
                        Logger.d(TAG, "[waitUntilNotPlaying] PLAYING -> ${state}")
                        return
                    }
                    Thread.sleep(5)
                }
            }
        }, "${NAMESPACE}-${directive.header.messageId}")

        val layerForDisplayPolicy = object : InterLayerDisplayPolicyManager.PlayLayer {
            override fun getPushPlayServiceId(): String? =
                payload.playStackControl?.getPushPlayServiceId()
            override fun getLayerType(): LayerType = LayerType.INFO
            override fun getDialogRequestId(): String = directive.getDialogRequestId()
        }

        val playSyncObject = object: PlaySynchronizerInterface.SynchronizeObject {
            override val playServiceId: String? = payload.playServiceId
            override val dialogRequestId: String = directive.getDialogRequestId()

            override fun requestReleaseSync() {
                Logger.d(TAG, "[requestReleaseSync]")
                executor.submit {
                    executeCancel(this@SpeakDirectiveInfo)
                }
            }
        }

        private fun startPlaying() {
            directive.getAttachmentReader()?.let {
                with(speechPlayer.setSource(it)) {
                    sourceId = this
                    Logger.d(TAG, "[startPlaying] sourceId: $this, info: $this@SpeakDirectiveInfo")
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


        fun clear() {
            directive.getAttachmentReader()
            directive.destroy()
        }

        fun setHandlingCompleted() {
            finishLock.withLock {
                if (isResultHandled) {
                    return
                }
                isResultHandled = true
                result.setCompleted()
                finishListeners.forEach {
                    it.onFinish()
                }
            }
        }

        fun setHandlingFailed(description: String, cancelPolicy: DirectiveHandlerResult.CancelPolicy? = null) {
            finishLock.withLock {
                if (isResultHandled) {
                    return
                }

                isResultHandled = true
                if (cancelPolicy == null) {
                    result.setFailed(description)
                } else {
                    result.setFailed(description, cancelPolicy)
                }
                finishListeners.forEach {
                    it.onFinish()
                }
            }
        }

        fun addOnFinishListener(listener: OnSpeakDirectiveFinishListener): Boolean {
            finishLock.withLock {
                if(isResultHandled) {
                    return false
                }

                return finishListeners.add(listener)
            }
        }

        fun removeOnFinishListener(listener: OnSpeakDirectiveFinishListener) {
            finishLock.withLock {
                finishListeners.remove(listener)
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = HashSet<TTSAgentInterface.Listener>()
    private val requestListenerMap =
        ConcurrentHashMap<String, TTSAgentInterface.OnPlaybackListener>()

    private var preparedSpeakInfo: SpeakDirectiveInfo? = null
    private var currentInfo: SpeakDirectiveInfo? = null
    private var lastImplicitStoppedInfo: SpeakDirectiveInfo? = null

    //    @GuardedBy("stateLock")
    private var currentState = TTSAgentInterface.State.IDLE
    private var currentToken: String? = null

    //    @GuardedBy("stateLock")
    private var isAlreadyStopping = false
    private var isAlreadyPausing = false

    private val playContextManager = TTSPlayContextProvider()

    init {
        Logger.d(TAG, "[init] $this")
        speechPlayer.setPlaybackEventListener(this)
        contextManager.setStateProvider(namespaceAndName, this)
        interLayerDisplayPolicyManager.addListener(this)
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
        Logger.d(TAG, "[preHandleDirective] start - info: $info")
        val speakInfo = createValidateSpeakInfo(info)

        if (speakInfo == null) {
            setHandlingInvalidSpeakDirectiveReceived(info)
            return
        }

        playSynchronizer.prepareSync(speakInfo.playSyncObject)
        focusManager.prepare(speakInfo)

        val waitCountDownListener = CountDownLatch(1)
        val waitStop: Boolean = currentInfo?.addOnFinishListener(object : OnSpeakDirectiveFinishListener {
            override fun onFinish() {
                waitCountDownListener.countDown()
            }
        }) ?: false

        if(!waitStop) {
            waitCountDownListener.countDown()
        }

        executor.submit {
            executePreHandleSpeakDirective(speakInfo)
        }

        waitCountDownListener.await()
        Logger.d(TAG, "[preHandleDirective] end - info: $info")
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

    private fun executePreHandleSpeakDirective(info: SpeakDirectiveInfo) {
        Logger.d(TAG, "[executePreHandleSpeakDirective] info: $info")
        executePrepareSpeakInfo(info)
    }

    private fun setHandlingInvalidSpeakDirectiveReceived(info: DirectiveInfo) {
        Logger.d(TAG, "[setHandlingInvalidSpeakDirectiveReceived] info: $info")
        info.result.setFailed("Invalid Speak Directive")
        info.directive.getDialogRequestId().let {
            requestListenerMap.remove(it)?.onError(it)
        }
    }

    private fun executeHandle(info: DirectiveInfo) {
        Logger.d(TAG, "[executeHandle] info: $info")
        executeHandleSpeakDirective(info)
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

        lastImplicitStoppedInfo = null
        nextSpeakInfo.isHandled = true

        if (currentInfo == null) {
            executePreparePlaySpeakInfo()
        }
    }

    override fun stop(payload: StopDirectiveHandler.Payload) {
        executor.submit {
            val current = currentInfo
            Logger.d(TAG, "[stop] at executor, payload: $payload, current: $current")
            if (current != null) {
                Logger.d(TAG, "[stop] stop current")
                executeCancelCurrentSpeakInfo()
/*
                if (current.payload.token == payload.token) {
                    Logger.d(TAG, "[stop] stop current")
                    executeCancelCurrentSpeakInfo()
                } else {
                    Logger.d(
                        TAG,
                        "[stop] not matched with current token (${current.payload.token}/${payload.token})"
                    )
                }
                Logger.d(TAG, "[stop] current is null, so skip")
 */
            } else {
                val lastStopped = lastImplicitStoppedInfo
                if(lastStopped != null) {
                    Logger.d(TAG, "[stop] stop lastStopped")
                    lastImplicitStoppedInfo = null
                    object : PlaySynchronizerInterface.SynchronizeObject {
                        override val playServiceId: String? = lastStopped.payload.playServiceId
                        override val dialogRequestId: String = lastStopped.directive.getDialogRequestId()
                    }.apply {
                        playSynchronizer.prepareSync(this)
                        playSynchronizer.releaseSyncImmediately(this)
                    }
                    /*
                    if (lastStopped.payload.token == payload.token) {
                        Logger.d(TAG, "[stop] stop lastStopped")
                        object : PlaySynchronizerInterface.SynchronizeObject {
                            override fun getDialogRequestId(): String = lastStopped.getDialogRequestId()

                            override fun requestReleaseSync(immediate: Boolean) {
                                // ignore.
                            }
                        }.apply {
                            playSynchronizer.prepareSync(this)
                            playSynchronizer.releaseSyncImmediately(this, object: PlaySynchronizerInterface.OnRequestSyncListener{
                                override fun onGranted() {
                                    // ignore
                                }

                                override fun onDenied() {
                                    // ignore
                                }
                            })
                        }
                    } else {
                        Logger.d(
                            TAG,
                            "[stop] not matched with lastStopped token (${lastStopped.payload.token}/${payload.token})"
                        )
                    }
                     */
                } else {
                    Logger.d(TAG, "[stop] lastImplicitStoppednfo is null, so skip")
                }
            }
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

    private fun setCurrentStateAndToken(state: TTSAgentInterface.State, token: String) {
        Logger.d(TAG, "[setCurrentState] state: $state")
        currentState = state
        currentToken = token
        currentInfo?.directive?.getDialogRequestId()?.let {
            when (state) {
                TTSAgentInterface.State.IDLE -> {
                    // no-op
                }
                TTSAgentInterface.State.PLAYING -> {
                    requestListenerMap[it]?.onStart(it)
                }
                TTSAgentInterface.State.STOPPED -> {
                    requestListenerMap.remove(it)?.onStop(it)
                }
                TTSAgentInterface.State.FINISHED -> {
                    requestListenerMap.remove(it)?.onFinish(it)
                }
            }

            notifyObservers(state, it)
        }
    }

    private fun notifyObservers(state: TTSAgentInterface.State, dialogRequestId: String) {
        for (observer in listeners) {
            observer.onStateChanged(state, dialogRequestId)
        }
    }

    private fun executePrepareSpeakInfo(speakInfo: SpeakDirectiveInfo) {
        executeCancelPreparedSpeakInfo()
        executeCancelCurrentSpeakInfo(false)

        with(speakInfo) {
            preparedSpeakInfo = this
        }
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
            setHandlingFailed("Canceled by the other speak directive.")
            executeReleaseSyncImmediately(this)
            executeTryReleaseFocus(info)
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
                if (state != TTSAgentInterface.State.FINISHED && state != TTSAgentInterface.State.STOPPED) {
                    stopPlaying(info)
                }
            } else {
                isDelayedCancel = true
            }
        }
    }

    private fun executePreparePlaySpeakInfo() {
        val speakInfo = preparedSpeakInfo
        Logger.d(TAG, "[executePreparePlaySpeakInfo] $speakInfo")
        if(speakInfo == null) {
            Logger.d(TAG, "[executePreparePlaySpeakInfo] no prepared")
            return
        }

        if(!speakInfo.isHandled) {
            Logger.d(TAG, "[executePreparePlaySpeakInfo] not handled yet.")
            return
        }

        preparedSpeakInfo = null
        currentInfo = speakInfo

        if (!focusManager.acquire(speakInfo, speakInfo.focusChannel)
        ) {
            Logger.e(TAG, "[executePreparePlaySpeakInfo] not registered channel!")
        }
    }

    private fun stopPlaying(info: SpeakDirectiveInfo) {
        Logger.d(TAG, "[stopPlaying]")

        when {
            info.sourceId.isError() -> {
            }
            isAlreadyStopping -> {
            }
            !speechPlayer.stop(info.sourceId) -> {
            }
            else -> {
                isAlreadyStopping = true
            }
        }
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

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[SPEAK] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_AUDIO,
            BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }

    private fun executeReleaseSync(info: SpeakDirectiveInfo) {
        playSynchronizer.releaseSync(info.playSyncObject)
        executeAfterExecuteRelease(info)
    }

    private fun executeReleaseSyncImmediately(info: SpeakDirectiveInfo) {
        playSynchronizer.releaseSyncImmediately(info.playSyncObject)
        executeAfterExecuteRelease(info)
    }

    private fun executeAfterExecuteRelease(info: SpeakDirectiveInfo) {
        interLayerDisplayPolicyManager.onPlayFinished(info.layerForDisplayPolicy)
        info.clear()

        if(info != currentInfo) {
            Logger.d(TAG, "[onReleased] this is not current info")
            return
        }

        Logger.d(TAG, "[onReleased] this is currentInfo")

        currentInfo = null
        executePreparePlaySpeakInfo()
    }

    private fun executeTryReleaseFocus(info: SpeakDirectiveInfo) {
        focusManager.release(info, info.focusChannel)
    }
    
    internal data class StateContext(
        private val state: TTSAgentInterface.State,
        private val token: String?
    ): BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            addProperty(
                "ttsActivity", when (state) {
                    TTSAgentInterface.State.PLAYING -> TTSAgentInterface.State.PLAYING.name
                    TTSAgentInterface.State.STOPPED -> TTSAgentInterface.State.STOPPED.name
                    else -> TTSAgentInterface.State.FINISHED.name
                }
            )
            token?.let {
                addProperty("token", it)
            }
        }.toString()
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")

        executor.submit {
            if(contextType == ContextType.COMPACT) {
                contextSetter.setState(
                    namespaceAndName,
                    StateContext.CompactContextState,
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            } else {
                val info = currentInfo
                if (currentState == TTSAgentInterface.State.PLAYING) {
                    // just log error
                    // context always updated if requested.
                    if (info == null) {
                        Logger.e(TAG, "[provideState] failed: currentInfo is null")
                    } else if (info.sourceId.isError()) {
                        Logger.e(TAG, "[provideState] failed: sourceId is error")
                    }
                }

                contextSetter.setState(
                    namespaceAndName,
                    StateContext(currentState, currentToken),
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    override fun onPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStarted] id: $id")
        currentInfo?.let {
            if (id.id == it.sourceId.id) {
                it.state = TTSAgentInterface.State.PLAYING
            }
        }

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
        currentInfo?.let {
            if (id.id == it.sourceId.id) {
                it.state = TTSAgentInterface.State.STOPPED
            }
        }

        executeOnPlaybackEvent(id) {
            executePlaybackStopped()
        }
    }

    override fun onPlaybackFinished(id: SourceId) {
        Logger.d(TAG, "[onPlaybackFinished] id: $id")
        currentInfo?.let {
            if (id.id == it.sourceId.id) {
                it.state = TTSAgentInterface.State.FINISHED
            }
        }

        executeOnPlaybackEvent(id) {
            executePlaybackFinished()
        }
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.e(TAG, "[onPlaybackError] id: $id, type: $type, error: $error")
        currentInfo?.let {
            if (id.id == it.sourceId.id) {
                it.state = TTSAgentInterface.State.STOPPED
            }
        }

        executor.submit {
            executePlaybackError(type, error)
        }
    }

    private fun executeOnPlaybackEvent(id: SourceId, event: () -> Unit) {
        if (id.id != currentInfo?.sourceId?.id) {
            executor.submit {
                executePlaybackError(
                    ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                    "Invalid Source ID"
                )
            }
        } else {
            executor.submit {
                event.invoke()
            }
        }
    }

    private fun executePlaybackStarted() {
        Logger.d(TAG, "[executePlaybackStarted] currentInfo: $currentInfo")
        val info = currentInfo ?: return
        playContextManager.onPlaybackStarted(info.payload.playStackControl?.getPushPlayServiceId())
        setCurrentStateAndToken(TTSAgentInterface.State.PLAYING, info.payload.token)
        info.state = TTSAgentInterface.State.PLAYING
        sendPlaybackEvent(EVENT_SPEECH_STARTED, info)
    }

    private fun sendPlaybackEvent(name: String, info: SpeakDirectiveInfo, callback: MessageSender.Callback? = null) {
        sendEventWithToken(
            NAMESPACE,
            name,
            info.payload.playServiceId,
            info.payload.token,
            info.directive.header.dialogRequestId,
            callback
        )
    }

    private fun executePlaybackStopped() {
        Logger.d(TAG, "[executePlaybackStopped] currentState: $currentState, currentInfo: $currentInfo")
        if (currentState == TTSAgentInterface.State.STOPPED) {
            return
        }
        val info = currentInfo ?: return
        playContextManager.onPlaybackStopped()
        setCurrentStateAndToken(TTSAgentInterface.State.STOPPED, info.payload.token)
        info.state = TTSAgentInterface.State.STOPPED

        val cancelByStop = info.cancelByStop

        with(info) {
            if (cancelByStop) {
                lastImplicitStoppedInfo = null
                executeReleaseSyncImmediately(this)
            } else {
                lastImplicitStoppedInfo = info
                executeReleaseSync(this)
            }
        }

        sendPlaybackEvent(EVENT_SPEECH_STOPPED, info, object: MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                executor.submit {
                    executePlaybackStoppedCompleted(info)
                }
            }

            override fun onSuccess(request: MessageRequest) {
            }

            override fun onResponseStart(request: MessageRequest) {
                executor.submit {
                    executePlaybackStoppedCompleted(info)
                }
            }
        })
    }

    private fun executePlaybackStoppedCompleted(info: SpeakDirectiveInfo) {
        with(info) {
            executeTryReleaseFocus(this)
            if (cancelByStop) {
                setHandlingFailed("playback canceled", DirectiveHandlerResult.POLICY_CANCEL_ALL)
            } else {
                setHandlingFailed("playback stopped", cancelPolicyOnStopTTS)
            }
        }
    }

    private fun executePlaybackFinished() {
        if (currentState == TTSAgentInterface.State.FINISHED) {
            return
        }

        Logger.d(TAG, "[executePlaybackFinished] $currentInfo")

        val info = currentInfo ?: return
        playContextManager.onPlaybackFinished()
        setCurrentStateAndToken(TTSAgentInterface.State.FINISHED, info.payload.token)
        info.state = TTSAgentInterface.State.FINISHED

        executeReleaseSync(info)

        sendPlaybackEvent(EVENT_SPEECH_FINISHED, info, object: MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                executor.submit {
                    executeTryReleaseFocus(info)
                    info.setHandlingCompleted()
                }
            }

            override fun onSuccess(request: MessageRequest) {}
            override fun onResponseStart(request: MessageRequest) {
                executor.submit {
                    executeTryReleaseFocus(info)
                    info.setHandlingCompleted()
                }
            }
        })
    }

    private fun executePlaybackError(type: ErrorType, error: String) {
        Logger.e(TAG, "[executePlaybackError] type: $type, error: $error, currentInfo: $currentInfo")
        val info = currentInfo ?: return

        listeners.forEach {
            it.onError(info.directive.getDialogRequestId())
        }

        if (currentState == TTSAgentInterface.State.STOPPED) {
            return
        }

        playContextManager.onPlaybackStopped()
        setCurrentStateAndToken(TTSAgentInterface.State.STOPPED, info.payload.token)
        info.state = TTSAgentInterface.State.STOPPED
        with(info) {
            result.setFailed("Playback Error (type: $type, error: $error)")
        }

        executeReleaseSync(info)
        executeTryReleaseFocus(info)
    }

    private fun sendEventWithToken(
        namespace: String,
        name: String,
        playServiceId: String?,
        token: String,
        referrerDialogRequestId: String,
        callback: MessageSender.Callback? = null
    ) {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()
        val messageId = UUIDGeneration.timeUUID().toString()

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val messageRequest =
                    EventMessageRequest.Builder(jsonContext, namespace, name, VERSION.toString())
                        .dialogRequestId(dialogRequestId)
                        .messageId(messageId)
                        .payload(
                            JsonObject().apply {
                                playServiceId?.let {
                                    addProperty(KEY_PLAY_SERVICE_ID, it)
                                }
                                addProperty(KEY_TOKEN, token)
                            }.toString()
                        )
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                messageSender.newCall(
                    messageRequest
                ).enqueue(callback)

                Logger.d(TAG, "[sendEventWithToken] $messageRequest")
            }
        }, namespaceAndName)
    }

    override fun requestTTS(
            text: String,
            format: TTSAgentInterface.Format,
            playServiceId: String?,
            listener: TTSAgentInterface.OnPlaybackListener?
    ): String {
        val dialogRequestId = UUIDGeneration.timeUUID().toString()
        val messageId = UUIDGeneration.timeUUID().toString()

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val messageRequest =
                        EventMessageRequest.Builder(
                                jsonContext,
                                NAMESPACE,
                                NAME_SPEECH_PLAY,
                                VERSION.toString()
                        )
                                .dialogRequestId(dialogRequestId)
                                .messageId(messageId)
                                .payload(JsonObject().apply {
                                    addProperty("format", format.name)
                                    addProperty("text", text)
                                    playServiceId?.let {
                                        addProperty("playServiceId", it)
                                    }
                                    addProperty("token", UUIDGeneration.timeUUID().toString())
                                }.toString())
                                .build()
                val call = messageSender.newCall(
                        messageRequest
                )
                listener?.let {
                    requestListenerMap[dialogRequestId] = it
                }
                call.enqueue(object : MessageSender.Callback {
                    override fun onFailure(request: MessageRequest, status: Status) {
                        requestListenerMap.remove(dialogRequestId)?.onError(dialogRequestId)
                    }

                    override fun onSuccess(request: MessageRequest) {

                    }

                    override fun onResponseStart(request: MessageRequest) {
                    }
                })
            }
        }, namespaceAndName)

        return dialogRequestId
    }

    override fun setVolume(volume: Float) = speechPlayer.setVolume(volume)

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? {
        val playContext = playContextManager.getPlayContext()
        Logger.d(TAG, "[getPlayContext] $playContext")
        return playContext
    }

    override fun onDisplayLayerRendered(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        // no-op
    }

    override fun onDisplayLayerCleared(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        if(currentState == TTSAgentInterface.State.FINISHED && playContextManager.getPlayContext() != null) {
            Logger.d(TAG, "[onDisplayLayerCleared] $layer")
            playContextManager.onPlaybackStopped()
        }
    }
}