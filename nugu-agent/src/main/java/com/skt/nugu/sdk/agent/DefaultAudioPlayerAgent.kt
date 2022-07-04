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
import com.skt.nugu.sdk.agent.audioplayer.AudioItem
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerPlaybackInfoProvider
import com.skt.nugu.sdk.agent.audioplayer.ProgressTimer
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.audioplayer.playback.AudioPlayerRequestPlayCommandDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.playback.AudioPlayerRequestPlaybackCommandDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.playback.PlaybackDirectiveHandler
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.AudioPlayerDisplayInterface
import com.skt.nugu.sdk.agent.display.AudioPlayerTemplateHandler
import com.skt.nugu.sdk.agent.display.DisplayInterface
import com.skt.nugu.sdk.agent.mediaplayer.*
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackHandler
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.net.URI
import java.util.concurrent.*

class DefaultAudioPlayerAgent(
    private val mediaPlayer: MediaPlayerInterface,
    private val messageSender: MessageSender,
    private val focusManager: SeamlessFocusManagerInterface,
    private val contextManager: ContextManagerInterface,
    private val playbackRouter: PlaybackRouter,
    private val playSynchronizer: PlaySynchronizerInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val channelName: String,
    enableDisplayLifeCycleManagement: Boolean,
    private val audioPlayerTemplateHandler: AudioPlayerTemplateHandler?
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , ChannelObserver
    , AudioPlayerAgentInterface
    , PlaybackHandler
    , MediaPlayerControlInterface.PlaybackEventListener
    , AudioPlayerLyricsDirectiveHandler.VisibilityController
    , AudioPlayerLyricsDirectiveHandler.PagingController
    , PlayStackManagerInterface.PlayContextProvider {

    enum class SourceType(val value: String) {
        @SerializedName("URL")
        URL("URL"),

        @SerializedName("ATTACHMENT")
        ATTACHMENT("ATTACHMENT")
    }

    data class PlayPayload(
        @SerializedName("sourceType")
        val sourceType: SourceType?,
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("cacheKey")
        val cacheKey: String?,
        @SerializedName("audioItem")
        val audioItem: AudioItem,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    companion object {
        private const val TAG = "AudioPlayerAgent"

        const val NAMESPACE = "AudioPlayer"
        val VERSION = Version(1,6)

        const val EVENT_NAME_PLAYBACK_STARTED = "PlaybackStarted"
        const val EVENT_NAME_PLAYBACK_FINISHED = "PlaybackFinished"
        const val EVENT_NAME_PLAYBACK_STOPPED = "PlaybackStopped"
        const val EVENT_NAME_PLAYBACK_PAUSED = "PlaybackPaused"
        const val EVENT_NAME_PLAYBACK_RESUMED = "PlaybackResumed"
        const val EVENT_NAME_PLAYBACK_FAILED = "PlaybackFailed"

        const val EVENT_NAME_PROGRESS_REPORT_DELAY_ELAPSED = "ProgressReportDelayElapsed"
        const val EVENT_NAME_PROGRESS_REPORT_INTERVAL_ELAPSED = "ProgressReportIntervalElapsed"

        private const val NAME_NEXT_COMMAND_ISSUED = "NextCommandIssued"
        private const val NAME_PREVIOUS_COMMAND_ISSUED = "PreviousCommandIssued"
        private const val NAME_PLAY_COMMAND_ISSUED = "PlayCommandIssued"
        private const val NAME_PAUSE_COMMAND_ISSUED = "PauseCommandIssued"
        private const val NAME_STOP_COMMAND_ISSUED = "StopCommandIssued"
        private const val NAME_FAVORITE_COMMAND_ISSUED = "FavoriteCommandIssued"
        private const val NAME_REPEAT_COMMAND_ISSUED = "RepeatCommandIssued"
        private const val NAME_SHUFFLE_COMMAND_ISSUED = "ShuffleCommandIssued"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"
        private const val KEY_TOKEN = "token"

        const val NAME_PLAY = "Play"
        const val NAME_STOP = "Stop"
        const val NAME_PAUSE = "Pause"

        val PLAY = NamespaceAndName(
            NAMESPACE,
            NAME_PLAY
        )
        val STOP = NamespaceAndName(
            NAMESPACE,
            NAME_STOP
        )
        val PAUSE = NamespaceAndName(
            NAMESPACE,
            NAME_PAUSE
        )
    }

    enum class PauseReason {
        BY_PAUSE_DIRECTIVE,
        BY_PLAY_DIRECTIVE,
        INTERNAL_LOGIC,
    }

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    private val lifeCycleScheduler: LifeCycleScheduler? =
        if (enableDisplayLifeCycleManagement) LifeCycleScheduler() else null

    private val activityListeners =
        LinkedHashSet<AudioPlayerAgentInterface.Listener>()
    private val durationListeners = LinkedHashSet<AudioPlayerAgentInterface.OnDurationListener>()
    private val playbackListeners = LinkedHashSet<AudioPlayerAgentInterface.OnPlaybackListener>()

    private val executor = Executors.newSingleThreadExecutor()

    private var currentActivity: AudioPlayerAgentInterface.State =
        AudioPlayerAgentInterface.State.IDLE
    private var currentFocus: FocusState = FocusState.NONE

    private var currentItem: AudioInfo? = null

    private var token: String = ""
    private var playServiceId: String? = null
    private var sourceId: SourceId = SourceId.ERROR()
    private var offset: Long = 0L
    private var duration: Long? = null
    private var canGetOffsetFromMediaPlayer: Boolean = false
        set(value) {
            field = value
            Logger.d(TAG, "canGetOffsetFromMediaPlayer: $value")
        }

    private var playCalled = false
    private var stopCalled = false
    private var stopReason: AudioPlayerAgentInterface.StopReason? = null
    private var pauseReason: PauseReason? = null
    private var progressTimer =
        ProgressTimer()
    private val progressProvider = object :
        ProgressTimer.ProgressProvider {
        override fun getProgress(): Long = getOffsetInMilliseconds()
    }

    private val progressListener = object :
        ProgressTimer.ProgressListener {
        override fun onProgressReportDelay(request: Long, actual: Long) {
            Logger.d(TAG, "[onProgressReportDelay] request: $request / actual: $actual")
            sendProgressReportDelay(actual)
        }

        override fun onProgressReportInterval(request: Long, actual: Long) {
            Logger.d(TAG, "[onProgressReportInterval] request: $request / actual: $actual")
            sendProgressReportInterval(actual)
        }
    }

    private val playbackInfoProvider = object : AudioPlayerPlaybackInfoProvider {
        override fun getToken(): String? {
            val current = currentItem ?: return null
            return current.payload.audioItem.stream.token
        }

        override fun getOffsetInMilliseconds(): Long? =
            this@DefaultAudioPlayerAgent.getOffsetInMilliseconds()

        override fun getPlayServiceId(): String? = currentItem?.playServiceId
    }

    private val focusRequester = object: SeamlessFocusManagerInterface.Requester {}
    private val onFocusFinishListener = object : FocusManagerInterface.OnFinishListener {
        val listeners = CopyOnWriteArraySet<FocusManagerInterface.OnFinishListener>()

        override fun onFinish() {
            listeners.forEach { it.onFinish() }
        }
    }

    private val focusChannel = SeamlessFocusManagerInterface.Channel(channelName, this, NAMESPACE, onFocusFinishListener)

    inner class AudioInfo(
        val payload: PlayPayload,
        val directive: Directive,
        override val playServiceId: String
    ) : PlaySynchronizerInterface.SynchronizeObject {
        override val dialogRequestId: String =  directive.getDialogRequestId()
        var referrerDialogRequestId: String = dialogRequestId
        var sourceAudioInfo:AudioInfo? = this
        var isFetched = false
        var isCanceled = false

        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                executor.submit {
                    Logger.d(TAG, "[onGranted] onReleased: ${directive.getMessageId()} , ${this@AudioInfo}, $currentFocus")
                    if (currentItem == this@AudioInfo) {
                        currentItem = null
                        if (!playDirectiveController.willBeHandle()) {
                            if(currentFocus != FocusState.NONE) {
                                focusManager.release(focusRequester, focusChannel)
                            } else {
                                focusManager.cancel(focusRequester)
                            }
                        }
                    }

                    directive.getAttachmentReader()
                    directive.destroy()
                }
            }
        }

        var playContext: PlayStackManagerInterface.PlayContext? = null

        override fun requestReleaseSync() {
            executor.submit {
                when {
                    currentItem == this -> {
                        if(playDirectiveController.executeOnCancel(directive)) {
                            Logger.d(TAG, "[requestReleaseSync] execute cancel on PlayDirectiveController")
                            return@submit
                        }

                        if(playDirectiveController.willBeHandle()) {
                            Logger.d(TAG, "[requestReleaseSync] try cancel current item but skip (handle it by play directive)")
                            return@submit
                        }

                        Logger.d(TAG, "[requestReleaseSync] cancel current item")
                        if(!executeStop()) {
                            notifyOnReleaseAudioInfo(this, true)
                        }
                    }
                    else -> {
                        Logger.d(TAG, "[requestReleaseSync] cancel outdated item")
                        notifyOnReleaseAudioInfo(this, true)
                    }
                }
            }
        }

        fun getCacheKey(): CacheKey? = payload.cacheKey?.let {
            CacheKey(playServiceId, it)
        }
    }

    private val pauseDirectiveController = object : PlaybackDirectiveHandler.Controller {
        private val willBeHandleDirectives = ConcurrentHashMap<String, Directive>()

        override fun onReceive(directive: Directive) {
            Logger.d(TAG, "[onReceivePause] ${directive.getMessageId()}")
            willBeHandleDirectives[directive.getMessageId()] = directive
        }

        override fun onPreExecute(directive: Directive): Boolean = true

        override fun onExecute(directive: Directive) {
            Logger.d(TAG, "[onExecutePause] ${directive.getMessageId()}")
            executor.submit {
                if (willBeHandleDirectives.remove(directive.getMessageId()) != null) {
                    executePause(PauseReason.BY_PAUSE_DIRECTIVE)
                }
            }
        }

        override fun onCancel(directive: Directive) {
            Logger.d(TAG, "[onCancelPause] ${directive.getMessageId()}")
            willBeHandleDirectives.remove(directive.getMessageId())
        }

        fun willBeHandle() = willBeHandleDirectives.isNotEmpty()
    }

    private val stopDirectiveController = object : PlaybackDirectiveHandler.Controller {
        private val willBeHandleDirectives = ConcurrentHashMap<String, Directive>()

        override fun onReceive(directive: Directive) {
            Logger.d(TAG, "[onReceiveStop] ${directive.getMessageId()}")
            willBeHandleDirectives[directive.getMessageId()] = directive
        }

        override fun onPreExecute(directive: Directive): Boolean = true

        override fun onExecute(directive: Directive) {
            Logger.d(TAG, "[onExecuteStop] ${directive.getMessageId()}")
            executor.submit {
                if (willBeHandleDirectives.remove(directive.getMessageId()) != null) {
                    executeStop()
                }
            }
        }

        override fun onCancel(directive: Directive) {
            Logger.d(TAG, "[onCancelStop] ${directive.getMessageId()}")
            willBeHandleDirectives.remove(directive.getMessageId())
        }

        fun willBeHandle() = willBeHandleDirectives.isNotEmpty()
    }

    private val playDirectiveController = object : PlaybackDirectiveHandler.Controller {
        private val INNER_TAG = "PlayDirectiveController"
        private val willBeHandleDirectives = ConcurrentHashMap<String, Directive>()
        private var waitFinishPreExecuteInfo: AudioInfo? = null
        private var waitPlayExecuteInfo: AudioInfo? = null

        override fun onReceive(directive: Directive) {
            Logger.d(TAG, "[onReceive::$INNER_TAG] ${directive.getMessageId()}")
            willBeHandleDirectives[directive.getMessageId()] = directive
        }

        override fun onPreExecute(directive: Directive): Boolean {
            Logger.d(TAG, "[onPreExecute::$INNER_TAG] ${directive.getMessageId()}")
            val playPayload = MessageFactory.create(directive.payload, PlayPayload::class.java) ?: return false

            val playServiceId = playPayload.playServiceId
            if (playServiceId.isBlank()) {
                Logger.w(TAG, "[onPreExecute::$INNER_TAG] empty playServiceId")
                return false
            }

            val nextAudioInfo = AudioInfo(playPayload, directive, playServiceId).apply {
                playSynchronizer.prepareSync(this)
            }

            if(currentFocus == FocusState.NONE) {
                focusManager.prepare(focusRequester)
            }

            executor.submit {
                waitFinishPreExecuteInfo?.let {
                    Logger.d(TAG, "[onPreExecute::$INNER_TAG] cancel waitFinishPreExecuteInfo($it)")
                    executeOnCancel(it.directive)
                }
                waitFinishPreExecuteInfo = nextAudioInfo

                val currentAudioInfo = currentItem
                if(!executeShouldResumeNextItem(currentAudioInfo, nextAudioInfo)) {
                    Logger.d(TAG, "[onPreExecute::$INNER_TAG] in executor - play new item: ${directive.getMessageId()}")
                    audioPlayerTemplateHandler?.shouldBeRender(nextAudioInfo.directive)
                    // stop current if play new item.
                    val currentPlayServiceId = currentAudioInfo?.playServiceId
                    val nextPlayServiceId = nextAudioInfo.playServiceId
                    val stopReason = if(currentPlayServiceId == nextPlayServiceId) {
                        AudioPlayerAgentInterface.StopReason.PLAY_ANOTHER
                    } else {
                        AudioPlayerAgentInterface.StopReason.STOP
                    }

                    if(executeStop(stopReason)) {
                        // should wait until stopped (onPlayerStopped will be called)
                        waitPlayExecuteInfo?.let {
                            if (currentAudioInfo == it) {
                                // If currentItem == waitPlayExecuteInfo, clear waitPlayExecuteInfo
                                // currentItem will be released soon.
                                willBeHandleDirectives.remove(it.directive.getMessageId())
                                waitPlayExecuteInfo = null
                            }
                        }
                    } else {
                        // fetch now
                        if(executeFetchItem(nextAudioInfo)) {
                            waitPlayExecuteInfo = nextAudioInfo
                        }
                        waitFinishPreExecuteInfo = null
                    }
                } else {
                    Logger.d(TAG, "[onPreExecute::$INNER_TAG] in executor - will be resume: ${directive.getMessageId()}")
                    audioPlayerTemplateHandler?.shouldBeUpdate(nextAudioInfo.directive)
                    currentItem = nextAudioInfo
                    currentAudioInfo?.let {
                        playSynchronizer.releaseSync(it, null)
                        nextAudioInfo.referrerDialogRequestId = it.referrerDialogRequestId
                        nextAudioInfo.sourceAudioInfo = it.sourceAudioInfo
                        nextAudioInfo.playContext = it.playContext
                    }

                    with(nextAudioInfo.directive) {
                        // consume
                        if(nextAudioInfo.payload.sourceType == SourceType.ATTACHMENT) {
                            getAttachmentReader()
                        }
                        // destroy
                        destroy()
                    }

                    // fetch only offset
                    duration?.let {
                        executeFetchOffset(nextAudioInfo.payload.audioItem.stream.offsetInMilliseconds)
                    }

                    waitPlayExecuteInfo = nextAudioInfo
                    // finish preExecute
                    waitFinishPreExecuteInfo = null
                }
            }
            return true
        }

        private fun executeFetchItem(item: AudioInfo): Boolean {
            Logger.d(TAG, "[executeFetchItem::$INNER_TAG] item: $item")
            progressTimer.stop()
            currentItem = item
            playServiceId = item.playServiceId
            token = item.payload.audioItem.stream.token
            item.playContext = item.payload.playStackControl?.getPushPlayServiceId()?.let {
                PlayStackManagerInterface.PlayContext(it, System.currentTimeMillis(), true)
            }

            progressTimer.init(
                item.payload.audioItem.stream.progressReport?.progressReportDelayInMilliseconds
                    ?: ProgressTimer.NO_DELAY,
                item.payload.audioItem.stream.progressReport?.progressReportIntervalInMilliseconds
                    ?: ProgressTimer.NO_INTERVAL, progressListener, progressProvider
            )

            return if(executeFetchSource(item)) {
                item.isFetched = true
                canGetOffsetFromMediaPlayer = true
                true
            } else {
                false
            }
        }

        private fun executeFetchSource(item: AudioInfo): Boolean {
            sourceId = when (item.payload.sourceType) {
                SourceType.ATTACHMENT -> item.directive.getAttachmentReader()?.let { reader ->
                    mediaPlayer.setSource(reader)
                } ?: SourceId.ERROR()
                else -> {
                    try {
                        mediaPlayer.setSource(URI.create(item.payload.audioItem.stream.url.trim()), item.getCacheKey())
                    } catch (th: Throwable) {
                        Logger.w(TAG, "[executeFetchSource::$INNER_TAG] failed to create uri", th)
                        SourceId.ERROR()
                    }
                }
            }

            if (sourceId.isError()) {
                Logger.w(TAG, "[executeFetchSource::$INNER_TAG] failed to setSource")
                executeOnPlaybackError(
                    sourceId,
                    ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                    "failed to setSource"
                )
                return false
            }
            return true
        }

        override fun onExecute(directive: Directive) {
            Logger.d(TAG, "[onExecute::$INNER_TAG] ${directive.getMessageId()}")

            executor.submit {
                if(willBeHandleDirectives.remove(directive.getMessageId()) != null) {
                    waitPlayExecuteInfo?.let {
                        if(it.directive.getMessageId() == directive.getMessageId()) {
                            waitPlayExecuteInfo = null
                            // preExecute finished (source fetched)
                            executeHandlePlayDirective()
                        } else {
                            Logger.d(TAG, "[onExecute::$INNER_TAG] miss matched: ${directive.getMessageId()} : ${it.directive.getMessageId()}")
                        }
                    }
                }
            }.get()
        }

        override fun onCancel(directive: Directive) {
            Logger.d(TAG, "[onCancel::$INNER_TAG] ${directive.getMessageId()}")
            executor.submit {
                executeOnCancel(directive)
            }
        }

        fun executeOnCancel(directive: Directive): Boolean {
            if(willBeHandleDirectives.remove(directive.getMessageId()) != null) {
                waitFinishPreExecuteInfo?.let {
                    if (directive.getMessageId() == it.directive.getMessageId()) {
                        notifyOnReleaseAudioInfo(it, true)
                        waitFinishPreExecuteInfo = null
                        return true
                    }
                }

                waitPlayExecuteInfo?.let {
                    if (directive.getMessageId() == it.directive.getMessageId()) {
                        if(!executeStop()) {
                            notifyOnReleaseAudioInfo(it, true)
                        }
                        waitPlayExecuteInfo = null
                        return true
                    }
                }
            }

            return false
        }

        private fun executeHandlePlayDirective() {
            Logger.d(
                TAG,
                "[executeHandlePlayDirective] currentActivity:$currentActivity, focus: $currentFocus"
            )

            if(currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
                pauseReason = PauseReason.BY_PLAY_DIRECTIVE
            }

            if (FocusState.FOREGROUND != currentFocus) {
                val countDownLatch = CountDownLatch(1)
                val listener = object : FocusManagerInterface.OnFinishListener {
                    override fun onFinish() {
                        Logger.d(TAG, "[executeHandlePlayDirective] acquire focus finished.")
                        onFocusFinishListener.listeners.remove(this)
                        countDownLatch.countDown()
                    }
                }

                onFocusFinishListener.listeners.add(listener)
                if (!focusManager.acquire(focusRequester, focusChannel)
                ) {
                    onFocusFinishListener.listeners.remove(listener)
                    countDownLatch.countDown()
                    progressTimer.stop()
                    sendPlaybackFailedEvent(
                        ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "Could not acquire $channelName for $NAMESPACE"
                    )
                }
                countDownLatch.await()
            }  else {
                executeOnForegroundFocus()
            }
        }

        private fun executeShouldResumeNextItem(prev: AudioInfo?, next: AudioInfo): Boolean {
            if(prev == null || !currentActivity.isActive() || sourceId.isError()) {
                return false
            } else {
                if(prev.playServiceId != next.playServiceId) {
                    return false
                }

                if(prev.payload.sourceType != next.payload.sourceType) {
                    return false
                }

                if(next.payload.sourceType == SourceType.URL) {
                    return prev.payload.audioItem.stream.url == next.payload.audioItem.stream.url
                }

                if(next.payload.sourceType == SourceType.ATTACHMENT) {
                    return prev.payload.audioItem.stream.token == next.payload.audioItem.stream.token
                }

                return false
            }
        }

        fun onPlayerStopped() {
            executor.submit {
                Logger.d(TAG, "[onPlayerStopped] waitFinishPreExecuteInfo: $waitFinishPreExecuteInfo, waitPlayExecuteInfo: $waitPlayExecuteInfo")
                waitPlayExecuteInfo?.let {
                    if(it.isCanceled) {
                        willBeHandleDirectives.remove(it.directive.getMessageId())
                        waitPlayExecuteInfo = null
                    }
                }

                waitFinishPreExecuteInfo?.let {

                    // remove waitPlayExecuteInfo if exist
                    waitPlayExecuteInfo?.let { info->
                        willBeHandleDirectives.remove(info.directive.getMessageId())
                        waitPlayExecuteInfo = null
                    }

                    waitFinishPreExecuteInfo = null
                    if(executeFetchItem(it)) {
                        waitPlayExecuteInfo = it
                    }
                }
                waitPlayExecuteInfo?.let {
                    Logger.d(TAG, "[onPlayerStopped] $willBeHandleDirectives, ${it.directive.getMessageId()}")
                    if(willBeHandleDirectives.containsKey(it.directive.getMessageId())) {
                        Logger.d(TAG, "[onPlayerStopped] waitPlayExecuteInfo is not handled yet, handled at onExecute()")
                    } else {
                        waitPlayExecuteInfo = null
                        executeHandlePlayDirective()
                    }
                }
            }
        }

        fun willBeHandle() = willBeHandleDirectives.isNotEmpty() || waitFinishPreExecuteInfo!= null || waitPlayExecuteInfo != null
    }

    private val audioPlayerRequestPlaybackCommandDirectiveHandler =
        AudioPlayerRequestPlaybackCommandDirectiveHandler(
            messageSender,
            contextManager,
            playbackInfoProvider
        ).apply {
            directiveSequencer.addDirectiveHandler(this)
        }

    private val audioPlayerRequestPlayCommandDirectiveHandler =
        AudioPlayerRequestPlayCommandDirectiveHandler(
            messageSender,
            contextManager
        ).apply {
            directiveSequencer.addDirectiveHandler(this)
        }

    init {
        Logger.d(
            TAG,
            "[init] channelName: $channelName, enableDisplayLifeCycleManagement: $enableDisplayLifeCycleManagement"
        )
        mediaPlayer.setPlaybackEventListener(this)
        mediaPlayer.setOnDurationListener(object : MediaPlayerControlInterface.OnDurationListener {
            override fun onRetrieved(id: SourceId, duration: Long?) {
                executor.submit {
                    Logger.d(TAG, "[onRetrieved] sourceId: $sourceId, id: $id, duration: $duration")
                    if(sourceId == id) {
                        this@DefaultAudioPlayerAgent.duration = duration

                        // should fetch offset
                        if(duration != null) {
                            currentItem?.let {
                                //
                                if(!it.isCanceled) {
                                    executeFetchOffset(it.payload.audioItem.stream.offsetInMilliseconds)
                                }
                            }
                        }

                        createAudioInfoContext()?.let { context->
                            durationListeners.forEach { listener ->
                                listener.onRetrieved(duration, context)
                            }
                        }
                    }
                }
            }
        })
        contextManager.setStateProvider(namespaceAndName, this)

        // pause directive handler
        PlaybackDirectiveHandler(
            pauseDirectiveController,
            Pair(PAUSE, BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO))
        ).apply {
            directiveGroupProcessor.addListener(this)
            directiveSequencer.addDirectiveHandler(this)
        }

        // stop directive handler
        PlaybackDirectiveHandler(
            stopDirectiveController,
            Pair(STOP, BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO))
        ).apply {
            directiveGroupProcessor.addListener(this)
            directiveSequencer.addDirectiveHandler(this)
        }

        // play directive handler
        PlaybackDirectiveHandler(
            playDirectiveController,
            Pair(PLAY, BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO))
        ).apply {
            directiveGroupProcessor.addListener(this)
            directiveSequencer.addDirectiveHandler(this)
        }
    }

    private fun notifyOnReleaseAudioInfo(info: AudioInfo, immediately: Boolean) {
        Logger.d(TAG, "[notifyOnReleaseAudioInfo] $info")
        with(info) {
            if (immediately) {
                playSynchronizer.releaseSyncImmediately(this, onReleaseCallback)
            } else {
                playSynchronizer.releaseSync(this, this.onReleaseCallback)
            }
        }
    }

    private fun executeFetchOffset(offsetInMilliseconds: Long) {
        Logger.d(TAG, "[executeFetchOffset] $offsetInMilliseconds")
        if (mediaPlayer.getOffset(sourceId) != offsetInMilliseconds) {
            mediaPlayer.seekTo(
                sourceId,
                offsetInMilliseconds
            )
        }
    }

    private fun executeResumeByButton() {
        if (currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
            when (currentFocus) {
                FocusState.FOREGROUND -> {
                    if (!mediaPlayer.resume(sourceId)) {
                        Logger.w(TAG, "[executeResume] failed to resume: $sourceId")
                    }
                }
                FocusState.BACKGROUND -> {
                    // The behavior should be same with when sent event.
                    if(focusManager.acquire(focusRequester, focusChannel)) {
                        pauseReason = null
                    }
                }
                FocusState.NONE -> {
                    // no-op
                    Logger.e(TAG, "[executeResume] nothing to do (must not be happen)")
                }
            }
        } else {
            Logger.d(TAG, "[executeResume] skip, not paused state.")
        }
    }

    private fun executeStop(reason: AudioPlayerAgentInterface.StopReason = AudioPlayerAgentInterface.StopReason.STOP): Boolean {
        Logger.d(
            TAG,
            "[executeStop] currentActivity: $currentActivity, playCalled: $playCalled, currentItem: $currentItem, reason: $reason"
        )
        currentItem?.isCanceled = true

        when (currentActivity) {
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> {
                // call stop first, if need.
                if (playCalled || currentItem?.isFetched == true) {
                    if (mediaPlayer.stop(sourceId)) {
                        stopReason = reason
                        stopCalled = true
                        return true
                    }
                }

                // if already stopped, check stop logic after finished.
                if (currentActivity == AudioPlayerAgentInterface.State.FINISHED) {
                    val scheduler = lifeCycleScheduler

                    if (scheduler != null) {
                        scheduler.onStoppedAfterFinished()
                    } else {
                        currentItem?.let {
                            notifyOnReleaseAudioInfo(it, true)
                        }
                    }
                }
            }
            AudioPlayerAgentInterface.State.PLAYING,
            AudioPlayerAgentInterface.State.PAUSED -> {
                updateCurrentOffsetIfAvailable()
                if (mediaPlayer.stop(sourceId)) {
                    stopReason = reason
                    stopCalled = true
                    return true
                }
            }
        }

        return false
    }

    private fun executePause(reason: PauseReason) {
        Logger.d(TAG, "[executePause] currentActivity: $currentActivity")
        when (currentActivity) {
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> return
            AudioPlayerAgentInterface.State.PAUSED -> {
                if (pauseReason == null) {
                    sendPlaybackPausedEvent()
                }
                pauseReason = reason
            }
            AudioPlayerAgentInterface.State.PLAYING -> {
                updateCurrentOffsetIfAvailable()
                if (!mediaPlayer.pause(sourceId)) {

                } else {
                    pauseReason = reason
                }
            }
        }
    }

    override fun addListener(listener: AudioPlayerAgentInterface.Listener, requestCurrentState: Boolean) {
        executor.submit {
            activityListeners.add(listener)
            if (requestCurrentState) {
                listener.onStateChanged(currentActivity, createAudioInfoContext() ?: return@submit)
            }
        }
    }

    override fun removeListener(listener: AudioPlayerAgentInterface.Listener) {
        executor.submit {
            activityListeners.remove(listener)
        }
    }

    override fun addOnPlaybackListener(listener: AudioPlayerAgentInterface.OnPlaybackListener) {
        executor.submit {
            playbackListeners.add(listener)
        }
    }

    override fun removeOnPlaybackListener(listener: AudioPlayerAgentInterface.OnPlaybackListener) {
        executor.submit {
            playbackListeners.remove(listener)
        }
    }

    override fun addOnDurationListener(listener: AudioPlayerAgentInterface.OnDurationListener, requestCurrentState: Boolean) {
        executor.submit {
            durationListeners.add(listener)
            if (requestCurrentState){
                listener.onRetrieved(duration, createAudioInfoContext() ?: return@submit)
            }
        }
    }

    override fun removeOnDurationListener(listener: AudioPlayerAgentInterface.OnDurationListener) {
        executor.submit {
            durationListeners.remove(listener)
        }
    }

    override fun play() {
        onButtonPressed(PlaybackButton.PLAY)
    }

    override fun stop() {
        onButtonPressed(PlaybackButton.STOP)
    }

    override fun next() {
        onButtonPressed(PlaybackButton.NEXT)
    }

    override fun prev() {
        onButtonPressed(PlaybackButton.PREVIOUS)
    }

    override fun pause() {
        onButtonPressed(PlaybackButton.PAUSE)
    }

    override fun seek(offsetInMilliseconds: Long) {
        executor.submit {
            if (!sourceId.isError()) {
                mediaPlayer.seekTo(sourceId, offsetInMilliseconds)
            }
        }
    }

    override fun getOffset(unit: com.skt.nugu.sdk.agent.util.TimeUnit): Long {
        return when(unit) {
            com.skt.nugu.sdk.agent.util.TimeUnit.MILLISECONDS -> {
                getOffsetInMilliseconds()
            }
            com.skt.nugu.sdk.agent.util.TimeUnit.SECONDS -> {
                getOffsetInMilliseconds() / 1000L
            }
        }
    }

    private fun getOffsetInMilliseconds(): Long {
        if (!sourceId.isError() && canGetOffsetFromMediaPlayer) {
            val offset = mediaPlayer.getOffset(sourceId)
            if (offset != MEDIA_PLAYER_INVALID_OFFSET) {
                this.offset = offset
            }
        }

        return offset
    }

    private fun updateCurrentOffsetIfAvailable() {
        if(canGetOffsetFromMediaPlayer) {
            getOffsetInMilliseconds()
        }
    }

    override fun requestFavoriteCommand(current: Boolean) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_FAVORITE_COMMAND_ISSUED,
                            VERSION.toString()
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("favorite", current)
                            }.toString()
                        ).referrerDialogRequestId(referrerDialogRequestId).build()

                        messageSender.newCall(messageRequest).enqueue(null)
                    }
                }
            }
        }, namespaceAndName)
    }

    override fun requestRepeatCommand(current: AudioPlayerAgentInterface.RepeatMode) {
        contextManager.getContext(object: IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_REPEAT_COMMAND_ISSUED,
                            VERSION.toString()
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("repeat", current.name)
                            }.toString()
                        ).referrerDialogRequestId(referrerDialogRequestId).build()

                        messageSender.newCall(messageRequest).enqueue(null)
                    }
                }
            }
        }, namespaceAndName)
    }

    override fun requestShuffleCommand(current: Boolean) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_SHUFFLE_COMMAND_ISSUED,
                            VERSION.toString()
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("shuffle", current)
                            }.toString()
                        ).referrerDialogRequestId(referrerDialogRequestId).build()

                        messageSender.newCall(messageRequest).enqueue(null)
                    }
                }
            }
        }, namespaceAndName)
    }

    override fun onPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStarted] id : $id")
        executor.submit {
            executeOnPlaybackStarted(id)
        }
    }

    private fun notifyOnActivityChanged(context: AudioPlayerAgentInterface.Context) {
        activityListeners.forEach { listener ->
            listener.onStateChanged(currentActivity, context)
        }
    }

    private fun createAudioInfoContext(): AudioPlayerAgentInterface.Context? {
        currentItem?.let {current ->
            current.sourceAudioInfo?.let { source ->
                return AudioPlayerAgentInterface.Context(
                    source.payload.playServiceId+";"+source.payload.audioItem.stream.token,
                    source.dialogRequestId,
                    current.payload.audioItem.metaData?.template?.toString(),
                    getOffsetInMilliseconds(),
                    current.dialogRequestId
                )
            }
        }

        return null
    }

    private fun changeActivity(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context?) {
        Logger.d(TAG, "[changeActivity] $currentActivity/$activity")
        currentActivity = activity
        executeProvideState(contextManager, namespaceAndName, ContextType.FULL, ContextSetterInterface.FORCE_SET_TOKEN)
        context?.let {
            notifyOnActivityChanged(it)
        }
    }

    override fun onPlaybackFinished(id: SourceId) {
        Logger.d(TAG, "[onPlaybackFinished] id : $id")
        executor.submit {
            if(currentItem?.isCanceled == true) {
                executeOnPlaybackStopped(id)
            } else {
                executeOnPlaybackFinished(id)
            }
        }
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.d(TAG, "[onPlaybackError] id : $id")
        executor.submit {
            executeOnPlaybackError(id, type, error)
        }
    }

    override fun onPlaybackPaused(id: SourceId) {
        Logger.d(TAG, "[onPlaybackPaused] id : $id")
        executor.submit {
            executeOnPlaybackPaused(id)
        }
    }

    override fun onPlaybackResumed(id: SourceId) {
        Logger.d(TAG, "[onPlaybackResumed] id : $id")
        executor.submit {
            executeOnPlaybackResumed(id)
        }
    }

    override fun onPlaybackStopped(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStopped] id : $id")
        canGetOffsetFromMediaPlayer = false

        executor.submit {
            executeOnPlaybackStopped(id)
        }
    }

    override fun onFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[onFocusChanged] newFocus : $newFocus")

        val wait = executor.submit {
            executeOnFocusChanged(newFocus)
        }

        // we have to stop playing player on background or none focus
        // So wait until player stopped.
        // We start playing on foreground, so don't need to wait.
        if (newFocus != FocusState.FOREGROUND
            // check currentActivity is playing or not to avoid focus deadlock
            && currentActivity == AudioPlayerAgentInterface.State.PLAYING
        ) {
            // to avoid deadlock, apply 200ms timeout
            try {
                wait.get(200, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Logger.w(TAG, "[onFocusChanged] occur exception ", e)
            }
        }
    }

    private fun executeOnPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[executeOnPlaybackStarted] id: $id, focus: $currentFocus")
        progressTimer.start()
        sendPlaybackStartedEvent()
        executeOnPlaybackPlayingInternal(id, false)
    }

    private fun executeOnPlaybackResumed(id: SourceId) {
        Logger.d(TAG, "[executeOnPlaybackResumed] id: $id, focus: $currentFocus")
        progressTimer.resume()
        pauseReason?.let {
            sendPlaybackResumedEvent()
        }
        executeOnPlaybackPlayingInternal(id, true)
    }

    private fun executeOnPlaybackPlayingInternal(id: SourceId ,isResume: Boolean) {
        if (id.id != sourceId.id) {
            return
        }

        playCalled = false

        // check focus state due to focus can be change after mediaPlayer.start().
        when (currentFocus) {
            FocusState.FOREGROUND -> {
            }
            FocusState.BACKGROUND -> {
                executeOnPlaybackPlayingOnBackgroundFocus()
            }
            FocusState.NONE -> {
                executeOnPlaybackPlayingOnLostFocus()
            }
        }

        pauseReason = null
        playbackRouter.setHandler(this)
        lifeCycleScheduler?.onPlaying()

        createAudioInfoContext()?.let { context->
            playbackListeners.forEach {
                if(isResume) {
                    it.onPlaybackResumed(context)
                } else {
                    it.onPlaybackStarted(context)
                }
            }
            changeActivity(AudioPlayerAgentInterface.State.PLAYING, context)
        }
    }

    private fun executeOnPlaybackPlayingOnBackgroundFocus() {
        // save current offset before pause.
        updateCurrentOffsetIfAvailable()

        if (!mediaPlayer.pause(sourceId)) {
            Logger.e(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause failed")
        } else {
            Logger.d(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause Succeeded")
        }
    }

    private fun executeOnPlaybackPlayingOnLostFocus() {
        if(stopCalled) {
            Logger.e(TAG, "[executeOnPlaybackPlayingOnLostFocus] stop already called")
            return
        }

        // save current offset before stop.
        updateCurrentOffsetIfAvailable()

        if (!mediaPlayer.stop(sourceId)) {
            Logger.e(TAG, "[executeOnPlaybackPlayingOnLostFocus] stop failed")
        } else {
            Logger.d(TAG, "[executeOnPlaybackPlayingOnLostFocus] stop Succeeded")
        }
    }

    private fun executeOnPlaybackPaused(id: SourceId) {
        if (id.id != sourceId.id) {
            return
        }

        lifeCycleScheduler?.onPaused(id)
        
        if(pauseReason == null && currentFocus == FocusState.FOREGROUND) {
            // if implicit pause status & foreground state, try resume.
            mediaPlayer.resume(id)
        }

        progressTimer.pause()

        pauseReason?.let {
            sendPlaybackPausedEvent()
        }

        val audioInfoContext = createAudioInfoContext()

        audioInfoContext?.let { context->
            playbackListeners.forEach { it.onPlaybackPaused(context) }
        }
        changeActivity(AudioPlayerAgentInterface.State.PAUSED, audioInfoContext)
    }

    private fun executeOnPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.d(TAG, "[executeOnPlaybackError]")
        if (id.id != sourceId.id) {
            return
        }

        progressTimer.stop()
        sendPlaybackFailedEvent(type, error)

        stopCalled = false
        pauseReason = null
        when (currentActivity) {
            AudioPlayerAgentInterface.State.PLAYING,
            AudioPlayerAgentInterface.State.PAUSED -> {
                val audioInfoContext = createAudioInfoContext()
                audioInfoContext?.let { context->
                    playbackListeners.forEach { it.onPlaybackError(context, type, error) }
                }
                changeActivity(AudioPlayerAgentInterface.State.STOPPED, audioInfoContext)
            }
            else -> {
                // no-op
            }
        }

        playDirectiveController.onPlayerStopped()

        handlePlaybackCompleted(true)
        stopReason = null
    }

    private fun executeOnPlaybackStopped(id: SourceId) {
        Logger.d(TAG, "[executeOnPlaybackStopped] id: $id / currentId: $sourceId")
        if (id.id != sourceId.id) {
            return
        }

        stopCalled = false
        pauseReason = null
        when (currentActivity) {
            AudioPlayerAgentInterface.State.PLAYING,
            AudioPlayerAgentInterface.State.PAUSED -> {
                progressTimer.stop()
                val reason = stopReason ?: AudioPlayerAgentInterface.StopReason.STOP
                sendPlaybackStoppedEvent(reason)

                val audioInfoContext = createAudioInfoContext()
                audioInfoContext?.let { context->
                    playbackListeners.forEach { it.onPlaybackStopped(context, reason) }
                }
                changeActivity(AudioPlayerAgentInterface.State.STOPPED, audioInfoContext)
            }
            else -> {
                // no-op
            }
        }

        playDirectiveController.onPlayerStopped()

        handlePlaybackCompleted(true)
        stopReason = null
    }

    private fun executeOnPlaybackFinished(id: SourceId) {
        Logger.d(
            TAG,
            "[executeOnPlaybackFinished] id: $id , currentActivity: ${currentActivity.name}"
        )
        if (id.id != sourceId.id) {
            Logger.e(
                TAG,
                "[executeOnPlaybackFinished] failed: invalidSourceId / $id, $sourceId"
            )
            return
        }

        pauseReason = null
        when (currentActivity) {
            AudioPlayerAgentInterface.State.PLAYING -> {
                sendPlaybackFinishedEvent()
                progressTimer.finish()

                val audioInfoContext = createAudioInfoContext()
                audioInfoContext?.let { context->
                    playbackListeners.forEach { it.onPlaybackFinished(context) }
                }
                changeActivity(AudioPlayerAgentInterface.State.FINISHED, audioInfoContext)

                handlePlaybackCompleted(false)
            }
            else -> {

            }
        }
    }

    private fun handlePlaybackCompleted(byStop: Boolean) {
        Logger.d(TAG, "[handlePlaybackCompleted] byStop: $byStop")
        progressTimer.stop()

        currentItem?.sourceAudioInfo = null
        val dialogRequestId = currentItem?.directive?.getDialogRequestId()
        if (dialogRequestId.isNullOrBlank()) {
            return
        }

        val syncObject = currentItem ?: return

        if (byStop) {
            notifyOnReleaseAudioInfo(syncObject, true)
        } else {
            lifeCycleScheduler?.onFinished(syncObject)
        }
    }

    private fun executeOnFocusChanged(newFocus: FocusState) {
        Logger.d(
            TAG,
            "[executeOnFocusChanged] focus: $newFocus, currentActivity: $currentActivity"
        )
        if (currentFocus == newFocus) {
            return
        }

        currentFocus = newFocus

        when (newFocus) {
            FocusState.FOREGROUND -> {
                executeOnForegroundFocus()
            }
            FocusState.BACKGROUND -> {
                executeOnBackgroundFocus()
            }
            FocusState.NONE -> {
                executeOnNoneFocus()
            }
        }
    }

    private fun executeOnForegroundFocus() {
        Logger.d(
            TAG,
            "[executeOnForegroundFocus] currentActivity :$currentActivity."
        )
        when (currentActivity) {
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> {
                if(!playDirectiveController.willBeHandle()) {
                    executeTryPlayCurrentItemIfReady()
                } else {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip. will be play directive handled."
                    )
                }
                return
            }
            AudioPlayerAgentInterface.State.PAUSED -> {
                if (pauseDirectiveController.willBeHandle()) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip. will be pause directive handled."
                    )
                    return
                }

                if (stopDirectiveController.willBeHandle()) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip. will be stop directive handled."
                    )
                    return
                }

                if (playDirectiveController.willBeHandle()) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip. will be play directive handled."
                    )
                    return
                }

                if (pauseReason == PauseReason.BY_PAUSE_DIRECTIVE) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip resume, because player has been paused :$pauseReason."
                    )
                    return
                }

                if (pauseReason == PauseReason.BY_PLAY_DIRECTIVE) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] will be play"
                    )
                    executeTryPlayCurrentItemIfReady()
                    return
                }

                if (!mediaPlayer.resume(sourceId)) {
                    focusManager.release(focusRequester, focusChannel)
                    return
                }
                return
            }
            else -> {
            }
        }
    }


    private fun executeOnBackgroundFocus() {
        when (currentActivity) {
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED,
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.PAUSED -> {
                return
            }

            AudioPlayerAgentInterface.State.PLAYING -> {
                if (!sourceId.isError()) {
                    updateCurrentOffsetIfAvailable()
                    mediaPlayer.pause(sourceId)
                }
                return
            }
        }
    }

    private fun executeOnNoneFocus() {
        executeStop()
    }


    private fun executeTryPlayCurrentItemIfReady() {
        val item = currentItem
        val sourceItem = currentItem?.sourceAudioInfo
        if(item == null || sourceItem == null) {
            Logger.w(TAG, "[executeTryPlayCurrentItemIfReady] no item to try play")
            return
        }
        Logger.d(TAG, "[executeTryPlayCurrentItemIfReady] $item")

        if (!mediaPlayer.play(sourceId)) {
            Logger.w(TAG, "[executePlayNextItem] playFailed")
            executeOnPlaybackError(
                sourceId,
                ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                "playFailed"
            )
            return
        }

        playCalled = true

        playSynchronizer.startSync(item)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        executor.submit {
            executeProvideState(contextSetter, namespaceAndName, contextType, stateRequestToken)
        }
    }

    internal data class StateContext(
        val playerActivity: AudioPlayerAgentInterface.State,
        val playServiceId: String?,
        val token: String,
        val offsetInMilliseconds: Long,
        val durationInMilliseconds: Long?,
        val lyricsVisible: Boolean?
    ) : BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            addProperty("playerActivity", playerActivity.name)

            if(playerActivity != AudioPlayerAgentInterface.State.IDLE) {
                if(token.isNotBlank()) {
                    addProperty("token", token)
                }

                playServiceId?.let {
                    addProperty("playServiceId", it)
                }

                addProperty("offsetInMilliseconds", offsetInMilliseconds)

                if (durationInMilliseconds != null && durationInMilliseconds != MEDIA_PLAYER_INVALID_OFFSET) {
                    addProperty("durationInMilliseconds", durationInMilliseconds)
                }
            }

            lyricsVisible?.let {
                addProperty("lyricsVisible", it)
            }
        }.toString()
    }

    private fun executeProvideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        if (contextType == ContextType.COMPACT) {
            contextSetter.setState(
                namespaceAndName,
                StateContext.CompactContextState,
                StateRefreshPolicy.ALWAYS,
                contextType,
                stateRequestToken
            )
        } else {
            val playerActivity =
                if (currentActivity == AudioPlayerAgentInterface.State.PAUSED && pauseReason == null) {
                    AudioPlayerAgentInterface.State.PLAYING
                } else {
                    currentActivity
                }

            contextSetter.setState(
                namespaceAndName, StateContext(
                    playerActivity,
                    playServiceId,
                    token,
                    getOffsetInMilliseconds(),
                    duration,
                    lyricsPresenter?.getVisibility()
                ), StateRefreshPolicy.ALWAYS, contextType, stateRequestToken
            )
        }
    }

    private fun sendPlaybackStartedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_STARTED, offset)
    }

    private fun sendPlaybackFinishedEvent() {
        sendEventWithOffset(name = EVENT_NAME_PLAYBACK_FINISHED, fullContext = true)
    }

    private fun sendPlaybackStoppedEvent(stopReason: AudioPlayerAgentInterface.StopReason) {
        val offset = getOffsetInMilliseconds()

        currentItem?.apply {
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val token = payload.audioItem.stream.token
                    val messageRequest = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        EVENT_NAME_PLAYBACK_STOPPED,
                        VERSION.toString()
                    ).payload(
                        JsonObject().apply {
                            addProperty("playServiceId", payload.playServiceId)
                            addProperty("token", token)
                            addProperty("offsetInMilliseconds", offset)
                            addProperty("reason", stopReason.name)
                        }.toString()
                    ).referrerDialogRequestId(referrerDialogRequestId).build()

                    messageSender.newCall(messageRequest).enqueue(null)
                    Logger.d(TAG, "[sendEvent] $messageRequest")
                }
            }, namespaceAndName)
        }
    }

    private fun sendPlaybackPausedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_PAUSED)
    }

    private fun sendPlaybackResumedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_RESUMED)
    }

    private fun sendEventWithOffset(
        name: String,
        offset: Long = getOffsetInMilliseconds(),
        condition: () -> Boolean = { true },
        fullContext: Boolean = false
    ) {
        sendEvent(name, offset, condition, fullContext)
    }

    private fun sendPlaybackFailedEvent(type: ErrorType, errorMsg: String) {
        val item = currentItem
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                item?.apply {
                    val token = payload.audioItem.stream.token
                    val messageRequest = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        EVENT_NAME_PLAYBACK_FAILED,
                        VERSION.toString()
                    )
                        .payload(JsonObject().apply {
                            addProperty(KEY_PLAY_SERVICE_ID, payload.playServiceId)
                            addProperty(KEY_TOKEN, token)
                            addProperty("offsetInMilliseconds", offset)

                            val error = JsonObject()
                            add("error", error)
                            with(error) {
                                addProperty("type", type.name)
                                addProperty("message", errorMsg)
                            }

                            val currentPlaybackState = JsonObject()
                            add("currentPlaybackState", currentPlaybackState)
                            with(currentPlaybackState) {
                                addProperty("token", token)
                                addProperty("offsetInMilliseconds", offset)
                                addProperty("playActivity", currentActivity.name)
                            }
                        }.toString()).build()

                    messageSender.newCall(messageRequest).enqueue(null)
                }
            }
        }, namespaceAndName)
    }

    private fun sendProgressReportDelay(actual: Long) {
        sendEvent(EVENT_NAME_PROGRESS_REPORT_DELAY_ELAPSED, actual, { true }, false)
    }

    private fun sendProgressReportInterval(actual: Long) {
        sendEvent(EVENT_NAME_PROGRESS_REPORT_INTERVAL_ELAPSED, actual, { true }, false)
    }

    private fun sendNextCommandIssued() {
        sendEventWithOffset(
            name = NAME_NEXT_COMMAND_ISSUED,
            condition = { true }, fullContext = true
        )
    }

    private fun sendPreviousCommandIssued() {
        sendEventWithOffset(
            name = NAME_PREVIOUS_COMMAND_ISSUED,
            condition = { true }, fullContext = true
        )
    }

    private fun sendPlayCommandIssued() {
        sendEventWithOffset(
            name = NAME_PLAY_COMMAND_ISSUED,
            condition = { currentActivity.isActive() }, fullContext = true)
    }

    private fun sendPauseCommandIssued() {
        sendEventWithOffset(
            name = NAME_PAUSE_COMMAND_ISSUED,
            condition = { currentActivity.isActive() }, fullContext = true)
    }

    private fun sendStopCommandIssued() {
        sendEventWithOffset(
            name = NAME_STOP_COMMAND_ISSUED,
            condition = { currentActivity.isActive() }, fullContext = true)
    }

    private fun sendEvent(eventName: String, offset: Long, condition: () -> Boolean, fullContext: Boolean) {
        currentItem?.apply {
            val dialogRequestId = UUIDGeneration.timeUUID().toString()
            val messageId = UUIDGeneration.timeUUID().toString()

            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val token = payload.audioItem.stream.token
                    val messageRequest = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        eventName,
                        VERSION.toString()
                    )
                        .dialogRequestId(dialogRequestId)
                        .messageId(messageId)
                        .payload(
                            JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("token", token)
                                addProperty("offsetInMilliseconds", offset)
                            }.toString()
                        )
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()

                    if (condition.invoke()) {
                        messageSender.newCall(messageRequest).enqueue(object :
                            MessageSender.Callback {
                            override fun onFailure(request: MessageRequest, status: Status) {
                            }

                            override fun onSuccess(request: MessageRequest) {
                            }

                            override fun onResponseStart(request: MessageRequest) {
                            }
                        })
                        Logger.d(TAG, "[sendEvent] $messageRequest")
                    } else {
                        Logger.w(TAG, "[sendEvent] unsatisfied condition, so skip send.")
                    }
                }
            }, if (fullContext) null else namespaceAndName)
        }
    }

    override fun onButtonPressed(button: PlaybackButton) {
        executor.submit {
            Logger.w(TAG, "[onButtonPressed] button: $button, state : $currentActivity")
            when (button) {
                PlaybackButton.PLAY -> executeResumeByButton()
                //sendPlayCommandIssued()
                PlaybackButton.PAUSE -> executePause(PauseReason.BY_PAUSE_DIRECTIVE)
                //sendPauseCommandIssued()
                PlaybackButton.STOP -> executeStop()
                //sendStopCommandIssued()
                PlaybackButton.NEXT -> sendNextCommandIssued()
                PlaybackButton.PREVIOUS -> sendPreviousCommandIssued()
            }
        }
    }

    fun shutdown() {
        executor.submit {
            executeStop()
        }
    }
//    override fun onTogglePressed(toggle: PlaybackToggle, action: Boolean) {
//        Logger.w(TAG, "[onTogglePressed] not supported - $toggle, $action")
//    }

    @Throws(IllegalStateException::class)
    override fun setElementSelected(
        templateId: String,
        token: String,
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String = audioPlayerTemplateHandler?.setElementSelected(templateId, token, postback, callback)
        ?: throw IllegalStateException("Not allowed call for audio player's setElementSelected")

    override fun notifyUserInteractionOnDisplay(templateId: String) {
        lifeCycleScheduler?.refreshSchedule()
    }

    override fun displayCardRendered(
        templateId: String,
        controller: AudioPlayerDisplayInterface.Controller?
    ) {
        audioPlayerTemplateHandler?.displayCardRendered(templateId, controller)
    }

    override fun displayCardRenderFailed(templateId: String) {
        audioPlayerTemplateHandler?.displayCardRenderFailed(templateId)
    }

    override fun displayCardCleared(templateId: String) {
        audioPlayerTemplateHandler?.displayCardCleared(templateId)
    }

    override fun setRenderer(renderer: AudioPlayerDisplayInterface.Renderer?) {
        audioPlayerTemplateHandler?.setRenderer(renderer)
    }

    var lyricsPresenter: LyricsPresenter? = null
        private set

    override fun setLyricsPresenter(presenter: LyricsPresenter?) {
        lyricsPresenter = presenter
    }

    override fun show(playServiceId: String): Boolean {
        return if (currentItem?.playServiceId == playServiceId) {
            lyricsPresenter?.show() ?: false
        } else {
            false
        }
    }

    override fun hide(playServiceId: String): Boolean {
        return if (currentItem?.playServiceId == playServiceId) {
            lyricsPresenter?.hide() ?: false
        } else {
            false
        }
    }

    override fun controlPage(
        playServiceId: String,
        direction: Direction
    ): Boolean {
        return if (currentItem?.playServiceId == playServiceId) {
            lyricsPresenter?.controlPage(direction) ?: false
        } else {
            false
        }
    }

    /**
     * AudioPlayer's release rule:
     * - After 10min of pausing, player released.
     * - After 7sec of finish, player released.
     */
    inner class LifeCycleScheduler {
        private val CLASS_TAG = "LifeCycleController"
        private var pausedStopFuture: Pair<SourceId, ScheduledFuture<*>>? = null
        private var delayedReleaseAudioInfoFuture: Pair<AudioInfo, ScheduledFuture<*>>? = null
        private val scheduleExecutor = ScheduledThreadPoolExecutor(1)

        private val stopDelayForPausedSourceAtMinutes: Long = 10L
        private val finishDelayAtMilliseconds: Long = 7000L

        fun onPlaying() {
            Logger.d(TAG, "[$CLASS_TAG.onPlaying]")
            pausedStopFuture?.second?.cancel(true)
            pausedStopFuture = null
        }

        fun onPaused(id: SourceId) {
            Logger.d(TAG, "[$CLASS_TAG.onPaused] id: $id")
            executeScheduleStopForPausedSource(id)
        }

        fun onFinished(audioInfo: AudioInfo) {
            Logger.d(TAG, "[$CLASS_TAG.onFinished]")
            delayNotifyOnReleaseAudioInfo(audioInfo, finishDelayAtMilliseconds)
        }

        fun onStoppedAfterFinished() {
            Logger.d(TAG, "[$CLASS_TAG.onStoppedAfterFinished]")
            delayedReleaseAudioInfoFuture?.let {
                delayedReleaseAudioInfoFuture = null
                it.second.cancel(true)
                notifyOnReleaseAudioInfo(it.first, true)
            }
        }

        fun refreshSchedule() {
            Logger.d(TAG, "[$CLASS_TAG.refreshSchedule]")
            executor.submit {
//                refreshPausedStopFutureIfRunning()
                refreshFinishDelayFutureIfRunning()
            }
        }

        private fun refreshPausedStopFutureIfRunning() {
            Logger.d(TAG, "[$CLASS_TAG.refreshPausedStopFutureIfRunning] $pausedStopFuture")
            val copyPausedStopFuture = pausedStopFuture
            pausedStopFuture = null
            if (copyPausedStopFuture != null) {
                executeScheduleStopForPausedSource(copyPausedStopFuture.first)
            }
        }

        private fun refreshFinishDelayFutureIfRunning() {
            Logger.d(
                TAG,
                "[$CLASS_TAG.refreshFinishDelayFutureIfRunning] $delayedReleaseAudioInfoFuture"
            )
            val copyDelayedReleaseAudioInfoFuture = delayedReleaseAudioInfoFuture
            delayedReleaseAudioInfoFuture = null
            if (copyDelayedReleaseAudioInfoFuture != null) {
                copyDelayedReleaseAudioInfoFuture.second.cancel(true)
                delayNotifyOnReleaseAudioInfo(
                    copyDelayedReleaseAudioInfoFuture.first,
                    finishDelayAtMilliseconds
                )
            }
        }

        private fun executeScheduleStopForPausedSource(id: SourceId) {
            Logger.d(TAG, "[$CLASS_TAG.executeScheduleStopForPausedSource] id: $id")
            pausedStopFuture?.second?.cancel(true)
            pausedStopFuture = Pair(id, scheduleExecutor.schedule(Callable {
                executor.submit {
                    if (id.id != sourceId.id) {
                        return@submit
                    }

                    if (currentActivity != AudioPlayerAgentInterface.State.PAUSED) {
                        return@submit
                    }
                    executeStop()
                }
            }, stopDelayForPausedSourceAtMinutes, TimeUnit.MINUTES))
        }

        private fun delayNotifyOnReleaseAudioInfo(
            delayedObject: AudioInfo,
            delay: Long
        ) {
            Logger.d(TAG, "[$CLASS_TAG.delayNotifyOnReleaseAudioInfo] $delayedObject, $delay")
            delayedReleaseAudioInfoFuture?.let {
                delayedReleaseAudioInfoFuture = null
                it.second.cancel(true)
                notifyOnReleaseAudioInfo(it.first, true)
            }
            delayedReleaseAudioInfoFuture = Pair(delayedObject, scheduleExecutor.schedule(Callable {
                executor.submit {
                    delayedReleaseAudioInfoFuture = null
                    notifyOnReleaseAudioInfo(delayedObject, false)
                }
            }, delay, TimeUnit.MILLISECONDS))
        }
    }

    override fun getPlayContext(): PlayStackManagerInterface.PlayContext? = currentItem?.playContext

    override fun setRequestCommandHandler(handler: AudioPlayerAgentInterface.RequestCommandHandler) {
        audioPlayerRequestPlaybackCommandDirectiveHandler.setRequestCommandHandler(handler)
        audioPlayerRequestPlayCommandDirectiveHandler.setRequestCommandHandler(handler)
    }
}