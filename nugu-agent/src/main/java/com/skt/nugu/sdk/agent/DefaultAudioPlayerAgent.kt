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
import com.skt.nugu.sdk.agent.audioplayer.ProgressTimer
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.lyrics.AudioPlayerLyricsDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.AudioPlayerDisplayInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.agent.display.DisplayInterface
import com.skt.nugu.sdk.agent.mediaplayer.*
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import java.net.URI
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class DefaultAudioPlayerAgent(
    mediaPlayer: MediaPlayerInterface,
    messageSender: MessageSender,
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    playbackRouter: PlaybackRouter,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    channelName: String
) : AbstractAudioPlayerAgent(
    mediaPlayer,
    messageSender,
    focusManager,
    contextManager,
    playbackRouter,
    playSynchronizer,
    playStackManager,
    channelName
), MediaPlayerControlInterface.PlaybackEventListener
    , AudioPlayerLyricsDirectiveHandler.VisibilityController
    , AudioPlayerLyricsDirectiveHandler.PagingController {

    internal data class PlayPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("audioItem")
        val audioItem: AudioItem,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    companion object {
        private const val TAG = "AudioPlayerAgent"

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
    }

    enum class PauseReason {
        BY_PAUSE_DIRECTIVE,
        BY_PLAY_DIRECTIVE_FOR_RESUME,
        BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY,
        INTERNAL_LOGIC,
    }

    private val activityListeners =
        HashSet<AudioPlayerAgentInterface.Listener>()
    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    private val executor = Executors.newSingleThreadExecutor()
    private var pausedStopFuture: ScheduledFuture<*>? = null
    private val pausedStopExecutor = ScheduledThreadPoolExecutor(1)
    private val stopDelayForPausedSourceAtMinutes: Long = 10L

    private var currentActivity: AudioPlayerAgentInterface.State =
        AudioPlayerAgentInterface.State.IDLE
    private var focus: FocusState = FocusState.NONE

    private var currentItem: AudioInfo? = null
    private var nextItem: AudioInfo? = null

    private var token: String = ""
    private var sourceId: SourceId = SourceId.ERROR()
    private var offset: Long = 0L
    private var duration: Long =
        MEDIA_PLAYER_INVALID_OFFSET
    private var playNextItemAfterStopped: Boolean = false
    private var playCalled = false
    private var stopCalled = false
    private var pauseCalled = false
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

    private val willBeHandleDirectiveLock = ReentrantLock()
    private var willBeHandlePauseDirectiveInfo: DirectiveInfo? = null
    private var willBeHandleStopDirectiveInfo: DirectiveInfo? = null

    private inner class AudioInfo(
        val payload: PlayPayload,
        val directive: Directive,
        val playServiceId: String
    ) : PlaySynchronizerInterface.SynchronizeObject {
        val onReleaseCallback = object : PlaySynchronizerInterface.OnRequestSyncListener {
            override fun onGranted() {
                executor.submit {
                    if (focus != FocusState.NONE) {
                        if (currentItem == this@AudioInfo) {
                            focusManager.releaseChannel(channelName, this@DefaultAudioPlayerAgent)
                        }
                    }

                    directive.destroy()
                }
            }

            override fun onDenied() {
            }
        }

        var playContext = payload.playStackControl?.getPushPlayServiceId()?.let {
            PlayStackManagerInterface.PlayContext(it, 300)
        }

        override fun getDialogRequestId(): String = directive.getDialogRequestId()

        override fun requestReleaseSync(immediate: Boolean) {
            executor.submit {
                executeCancelAudioInfo(this)
            }
        }
    }

    init {
        mediaPlayer.setPlaybackEventListener(this)
        contextManager.setStateProvider(namespaceAndName, this)
    }

    private fun executeCancelAudioInfo(audioInfo: AudioInfo) {
        when {
            nextItem == audioInfo -> {
                Logger.d(TAG, "[executeCancelAudioInfo] cancel next item")
                executeCancelNextItem()
            }
            currentItem == audioInfo -> {
                Logger.d(TAG, "[executeCancelAudioInfo] cancel current item")
                executeStop()
            }
            else -> {
                Logger.d(TAG, "[executeCancelAudioInfo] cancel outdated item")
                notifyOnReleaseAudioInfo(audioInfo)
            }
        }
    }

    private fun notifyOnReleaseAudioInfo(info: AudioInfo, delay: Long? = null) {
        Logger.d(TAG, "[notifyOnReleaseAudioInfo] $info")
        with(info) {
            if (delay == null) {
                playSynchronizer.releaseSyncImmediately(this, onReleaseCallback)
                playContext?.let {
                    playStackManager.remove(it)
                }
            } else {
                playSynchronizer.releaseSync(this, this.onReleaseCallback)
                playContext?.let {
                    playStackManager.removeDelayed(it, delay)
                }
            }
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
        when (info.directive.getNamespaceAndName()) {
            PAUSE -> preHandlePauseDirective(info)
            STOP -> preHandleStopDirective(info)
            PLAY -> preHandlePlayDirective(info)
        }
    }

    private fun preHandlePauseDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandlePauseDirective] info: $info")
        willBeHandleDirectiveLock.withLock {
            willBeHandlePauseDirectiveInfo = info
        }
    }

    private fun preHandleStopDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandleStopDirective] info: $info")
        willBeHandleDirectiveLock.withLock {
            willBeHandleStopDirectiveInfo = info
        }
    }

    private fun preHandlePlayDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandlePlayDirective] info : $info")
        val playPayload = MessageFactory.create(info.directive.payload, PlayPayload::class.java)
        if (playPayload == null) {
            Logger.w(TAG, "[preHandlePlayDirective] invalid payload")
            setHandlingFailed(info, "[preHandlePlayDirective] invalid payload")
            return
        }

        val playServiceId = playPayload.playServiceId
        if (playServiceId.isBlank()) {
            Logger.w(TAG, "[preHandlePlayDirective] playServiceId is empty")
            setHandlingFailed(info, "[preHandlePlayDirective] playServiceId is empty")
            return
        }

        val audioInfo = AudioInfo(playPayload, info.directive, playServiceId).apply {
            playSynchronizer.prepareSync(this)
        }

        executor.submit {
            executeCancelNextItem()
            nextItem = audioInfo
        }
    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getNamespaceAndName()) {
            PLAY -> handlePlayDirective(info)
            STOP -> handleStopDirective(info)
            PAUSE -> handlePauseDirective(info)
            else -> handleUnknownDirective(info)
        }
    }

    private fun handlePlayDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePlayDirective] info : $info")

        setHandlingCompleted(info)
        executor.submit {
            executeHandlePlayDirective(info)
        }
    }

    private fun handleStopDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleStopDirective] info : $info")
        setHandlingCompleted(info)
        executor.submit {
            willBeHandleDirectiveLock.withLock {
                if (info == willBeHandlePauseDirectiveInfo) {
                    willBeHandleStopDirectiveInfo = null
                }
            }
            executeCancelNextItem()
            executeStop()
        }
    }

    private fun executeCancelNextItem() {
        val item = nextItem
        nextItem = null

        if (item == null) {
            Logger.d(TAG, "[executeCancelNextItem] no next item.")
            return
        }
        Logger.d(TAG, "[executeCancelNextItem] cancel next item : $item")
        notifyOnReleaseAudioInfo(item)
    }

    private fun handlePauseDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handlePauseDirective] info : $info")
        setHandlingCompleted(info)
        executor.submit {
            willBeHandleDirectiveLock.withLock {
                if (info == willBeHandlePauseDirectiveInfo) {
                    willBeHandlePauseDirectiveInfo = null
                }
            }

            executePause(PauseReason.BY_PAUSE_DIRECTIVE)
        }
    }

    private fun handleUnknownDirective(info: DirectiveInfo) {
        Logger.w(TAG, "[handleUnknownDirective] info: $info")
        removeDirective(info)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[cancelDirective] info: $info")
        cancelSync(info)
        removeDirective(info)
    }

    private fun cancelSync(info: DirectiveInfo) {
        val item = nextItem ?: return

        if (info.directive.getMessageId() == item.directive.getMessageId()) {
            notifyOnReleaseAudioInfo(item)
        }
    }

    private fun executeHandlePlayDirective(info: DirectiveInfo) {
        Logger.d(
            TAG,
            "[executeHandlePlayDirective] currentActivity:$currentActivity, focus: $focus"
        )
        if (!checkIfNextItemMatchWithInfo(info)) {
            Logger.d(TAG, "[executeHandlePlayDirective] skip")
            return
        }

        val hasSameToken = hasSameToken(currentItem, nextItem)

        if (currentItem != null && currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
            pauseReason = if (hasSameToken) {
                PauseReason.BY_PLAY_DIRECTIVE_FOR_RESUME
            } else {
                PauseReason.BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY
            }
        }

        if (FocusState.FOREGROUND != focus) {
            if (!focusManager.acquireChannel(
                    channelName,
                    this,
                    NAMESPACE
                )
            ) {
                progressTimer.stop()
                sendPlaybackFailedEvent(
                    ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                    "Could not acquire $channelName for $NAMESPACE"
                )
            }
        } else if (currentActivity == AudioPlayerAgentInterface.State.PLAYING) {
            if (hasSameToken) {
                executePlayNextItem()
            } else {
                executeStop(true)
            }
        } else {
            executeOnForegroundFocus()
        }
    }

    private fun checkIfNextItemMatchWithInfo(info: DirectiveInfo): Boolean {
        val cacheNextItem = nextItem
        if (cacheNextItem == null) {
            Logger.e(TAG, "[checkIfNextItemMatchWithInfo] nextItem is null. maybe canceled")
            return false
        }

        Logger.d(
            TAG,
            "[checkIfNextItemMatchWithInfo] item message id: ${cacheNextItem.directive.getMessageId()}, directive message id : ${info.directive.getMessageId()}"
        )
        return cacheNextItem.directive.getMessageId() == info.directive.getMessageId()
    }

    private fun hasSameToken(
        currentItem: AudioInfo?,
        nextItem: AudioInfo?
    ): Boolean {
        if (currentItem == null || nextItem == null) {
            return false
        }

        return currentItem.payload.audioItem.stream.token == nextItem.payload.audioItem.stream.token
    }

    private fun executeResume() {
        Logger.d(TAG, "[executeResume] currentActivity: $currentActivity")
        if (currentActivity == AudioPlayerAgentInterface.State.PAUSED && focus == FocusState.FOREGROUND) {
            if (!mediaPlayer.resume(sourceId)) {
            } else {
            }
        }
    }

    private fun executeStop(startNextSong: Boolean = false) {
        Logger.d(
            TAG,
            "[executeStop] currentActivity: $currentActivity, startNextSong: $startNextSong"
        )
        when (currentActivity) {
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> {
                if (playCalled) {
                    if (mediaPlayer.stop(sourceId)) {
                        stopCalled = true
                    }
                }
                return
            }
            AudioPlayerAgentInterface.State.PLAYING,
            AudioPlayerAgentInterface.State.PAUSED -> {
                getOffsetInMilliseconds()
                playNextItemAfterStopped = startNextSong
                if (!mediaPlayer.stop(sourceId)) {

                } else {
                    stopCalled = true
                }
            }
        }
    }

    private fun executePause(reason: PauseReason) {
        Logger.d(TAG, "[executePause] currentActivity: $currentActivity")
        when (currentActivity) {
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> return
            AudioPlayerAgentInterface.State.PAUSED -> {
                pauseReason = reason
            }
            AudioPlayerAgentInterface.State.PLAYING -> {
                getOffsetInMilliseconds()
                if (!mediaPlayer.pause(sourceId)) {

                } else {
                    pauseCalled = true
                    pauseReason = reason
                }
            }
        }
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val audioNonBlockingPolicy = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            false
        )

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[PLAY] = audioNonBlockingPolicy
        configuration[PAUSE] = audioNonBlockingPolicy
        configuration[STOP] = audioNonBlockingPolicy

        return configuration
    }

    override fun addListener(listener: AudioPlayerAgentInterface.Listener) {
        executor.submit {
            activityListeners.add(listener)
        }
    }

    override fun removeListener(listener: AudioPlayerAgentInterface.Listener) {
        executor.submit {
            activityListeners.remove(listener)
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

    override fun seek(millis: Long) {
        executor.submit {
            if (!sourceId.isError()) {
                mediaPlayer.seekTo(sourceId, millis)
            }
        }
    }

    override fun getOffset(): Long = getOffsetInMilliseconds() / 1000L

    override fun getDuration(): Long = getDurationInMilliseconds() / 1000L

    private fun getOffsetInMilliseconds(): Long {
        if (!sourceId.isError()) {
            val offset = mediaPlayer.getOffset(sourceId)
            if (offset != MEDIA_PLAYER_INVALID_OFFSET) {
                this.offset = offset
            }
        }

        return offset
    }

    private fun getDurationInMilliseconds(): Long {
        if (!sourceId.isError()) {
            val temp = mediaPlayer.getDuration(sourceId)
            if (temp != MEDIA_PLAYER_INVALID_OFFSET) {
                duration = temp
            }
        }

        return duration
    }

    override fun setFavorite(favorite: Boolean) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_FAVORITE_COMMAND_ISSUED,
                            VERSION
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                                addProperty("favorite", favorite)
                            }.toString()
                        ).build()

                        messageSender.sendMessage(messageRequest)
                    }
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    override fun setRepeatMode(mode: AudioPlayerAgentInterface.RepeatMode) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_REPEAT_COMMAND_ISSUED,
                            VERSION
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                                addProperty("repeat", mode.name)
                            }.toString()
                        ).build()

                        messageSender.sendMessage(messageRequest)
                    }
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    override fun setShuffle(shuffle: Boolean) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                executor.submit {
                    currentItem?.apply {
                        val messageRequest = EventMessageRequest.Builder(
                            jsonContext,
                            NAMESPACE,
                            NAME_SHUFFLE_COMMAND_ISSUED,
                            VERSION
                        ).payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                                addProperty("shuffle", shuffle)
                            }.toString()
                        ).build()

                        messageSender.sendMessage(messageRequest)
                    }
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    override fun onPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStarted] id : $id")
        executor.submit {
            executeOnPlaybackStarted(id)
        }
    }

    private fun notifyOnActivityChanged() {
        currentItem?.let {
            val context = AudioPlayerAgentInterface.Context(
                it.payload.audioItem.stream.token,
                it.payload.audioItem.metaData?.template?.toString(),
                getOffsetInMilliseconds()
            )
            activityListeners.forEach { listener ->
                listener.onStateChanged(currentActivity, context)
            }
        }
    }

    private fun changeActivity(activity: AudioPlayerAgentInterface.State) {
        Logger.d(TAG, "[changeActivity] $currentActivity/$activity")
        currentActivity = activity
        executeProvideState(contextManager, namespaceAndName, 0, false)
        notifyOnActivityChanged()
    }

    override fun onPlaybackFinished(id: SourceId) {
        Logger.d(TAG, "[onPlaybackFinished] id : $id")
        executor.submit {
            executeOnPlaybackFinished(id)
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
        if (newFocus != FocusState.FOREGROUND) {
            wait.get()
        }
    }

    private fun executeOnPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[executeOnPlaybackStarted] id: $id, focus: $focus")
        playCalled = false
        progressTimer.start()
        sendPlaybackStartedEvent()
        executeOnPlaybackPlayingInternal(id)
    }

    private fun executeOnPlaybackResumed(id: SourceId) {
        Logger.d(TAG, "[executeOnPlaybackResumed] id: $id, focus: $focus")
        progressTimer.resume()
        sendPlaybackResumedEvent()
        executeOnPlaybackPlayingInternal(id)
    }

    private fun executeOnPlaybackPlayingInternal(id: SourceId) {
        if (id.id != sourceId.id) {
            return
        }

        // check focus state due to focus can be change after mediaPlayer.start().
        when (focus) {
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
        changeActivity(AudioPlayerAgentInterface.State.PLAYING)
    }

    private fun executeOnPlaybackPlayingOnBackgroundFocus() {
        if (!mediaPlayer.pause(sourceId)) {
            Logger.e(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause failed")
        } else {
            Logger.d(TAG, "[executeOnPlaybackPlayingOnBackgroundFocus] pause Succeeded")
        }
    }

    private fun executeOnPlaybackPlayingOnLostFocus() {
        if (!mediaPlayer.stop(sourceId)) {
            Logger.e(TAG, "[executeOnPlaybackPlayingOnLostFocus] stop failed")
        } else {
            Logger.d(TAG, "[executeOnPlaybackPlayingOnLostFocus] pause Succeeded")
        }
    }

    private fun executeOnPlaybackPaused(id: SourceId) {
        if (id.id != sourceId.id) {
            return
        }

        scheduleStopForPausedSource(id)

        pauseCalled = false
        progressTimer.pause()
        sendPlaybackPausedEvent()
        changeActivity(AudioPlayerAgentInterface.State.PAUSED)
    }

    private fun scheduleStopForPausedSource(id: SourceId) {
        pausedStopFuture?.cancel(true)
        pausedStopFuture = pausedStopExecutor.schedule(Callable {
            executor.submit {
                if (id.id != sourceId.id) {
                    return@submit
                }

                if (currentActivity != AudioPlayerAgentInterface.State.PAUSED) {
                    return@submit
                }
                executeStop(false)
            }
        }, stopDelayForPausedSourceAtMinutes, TimeUnit.MINUTES)
    }

    private fun executeOnPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.d(TAG, "[executeOnPlaybackError]")
        if (id.id != sourceId.id) {
            return
        }

        progressTimer.stop()
        sendPlaybackFailedEvent(type, error)
        executeOnPlaybackStopped(sourceId, true)
    }

    private fun executeOnPlaybackStopped(id: SourceId, isError: Boolean = false) {
        Logger.d(TAG, "[executeOnPlaybackStopped] nextItem : $nextItem, isError: $isError")
        if (id.id != sourceId.id) {
            Logger.e(TAG, "[executeOnPlaybackStopped] nextItem : $nextItem")
            return
        }

        stopCalled = false
        pauseReason = null
        when (currentActivity) {
            AudioPlayerAgentInterface.State.PLAYING,
            AudioPlayerAgentInterface.State.PAUSED -> {
                progressTimer.stop()
                if (!isError) {
                    sendPlaybackStoppedEvent()
                }
                changeActivity(AudioPlayerAgentInterface.State.STOPPED)

                if (playNextItemAfterStopped) {
                    if (focus == FocusState.FOREGROUND && nextItem != null) {
                        executePlayNextItem()
                    }
                } else {
                    if (nextItem == null) {
                        handlePlaybackCompleted(true)
                    }
                }
            }
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.STOPPED,
            AudioPlayerAgentInterface.State.FINISHED -> {
                if (focus != FocusState.NONE) {
                    handlePlaybackCompleted(true)
                }
            }
        }
    }

    private fun executeOnPlaybackFinished(id: SourceId) {
        Logger.d(
            TAG,
            "[executeOnPlaybackFinished] id: $id , currentActivity: ${currentActivity.name}, nextItem: $nextItem"
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
                progressTimer.stop()
                changeActivity(AudioPlayerAgentInterface.State.FINISHED)
                if (nextItem == null) {
                    handlePlaybackCompleted(false)
                } else {
                    executePlayNextItem()
                }
            }
            else -> {

            }
        }
    }

    private fun handlePlaybackCompleted(byStop: Boolean) {
        Logger.d(TAG, "[handlePlaybackCompleted]")
        progressTimer.stop()

        val dialogRequestId = currentItem?.directive?.getDialogRequestId()
        if (dialogRequestId.isNullOrBlank()) {
            return
        }

        val syncObject = currentItem ?: return

        if (byStop) {
            notifyOnReleaseAudioInfo(syncObject)
        } else {
            notifyOnReleaseAudioInfo(syncObject, 7000L)
        }
    }

    private fun executeOnFocusChanged(newFocus: FocusState) {
        Logger.d(
            TAG,
            "[executeOnFocusChanged] focus: $newFocus, currentActivity: $currentActivity"
        )
        if (focus == newFocus) {
            return
        }

        focus = newFocus

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
                if (nextItem != null) {
                    executePlayNextItem()
                }
                return
            }
            AudioPlayerAgentInterface.State.PAUSED -> {
                willBeHandleDirectiveLock.withLock {
                    if (willBeHandlePauseDirectiveInfo != null || willBeHandleStopDirectiveInfo != null) {
                        Logger.d(
                            TAG,
                            "[executeOnForegroundFocus] skip. will be pause or stop directive handled."
                        )
                        return
                    }
                }

                if (pauseReason == PauseReason.BY_PAUSE_DIRECTIVE) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] skip resume, because player has been paused :$pauseReason."
                    )
                    return
                }

                if (pauseReason == PauseReason.BY_PLAY_DIRECTIVE_FOR_NEXT_PLAY) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] will be start next item after stop current item completely."
                    )
                    executeStop(true)
                    return
                }

                if (pauseReason == PauseReason.BY_PLAY_DIRECTIVE_FOR_RESUME) {
                    Logger.d(
                        TAG,
                        "[executeOnForegroundFocus] will be resume by next item"
                    )
                    executePlayNextItem()
                    return
                }

                if (!mediaPlayer.resume(sourceId)) {
                    focusManager.releaseChannel(channelName, this)
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
            AudioPlayerAgentInterface.State.STOPPED -> {
                if (playNextItemAfterStopped && nextItem != null) {
                    playNextItemAfterStopped = false
                    return
                }
            }
            AudioPlayerAgentInterface.State.FINISHED,
            AudioPlayerAgentInterface.State.IDLE,
            AudioPlayerAgentInterface.State.PAUSED,
            AudioPlayerAgentInterface.State.PLAYING -> {
                if (!sourceId.isError()) {
                    mediaPlayer.pause(sourceId)
                }
                return
            }
        }
    }

    private fun executeOnNoneFocus() {
        if (currentActivity.isActive()) {
            executeCancelNextItem()
            executeStop()
        }
    }

    private fun executePlayNextItem() {
        progressTimer.stop()
        Logger.d(
            TAG,
            "[executePlayNextItem] nextItem: $nextItem, currentActivity: $currentActivity"
        )
        val currentPlayItem = nextItem
        if (currentPlayItem == null) {
            executeStop()
            return
        }

        currentPlayItem.let {
            currentItem?.let { info ->
                notifyOnReleaseAudioInfo(info)
            }
            currentItem = it
            nextItem = null
            val audioItem = it.payload.audioItem
            if (!executeShouldResumeNextItem(token, audioItem.stream.token)) {
                token = audioItem.stream.token
                sourceId = when (audioItem.sourceType) {
                    AudioItem.SourceType.URL -> mediaPlayer.setSource(URI.create(audioItem.stream.url))
                    AudioItem.SourceType.ATTACHMENT -> it.directive.getAttachmentReader()?.let { reader ->
                        mediaPlayer.setSource(reader)
                    } ?: SourceId.ERROR()
                }
                if (sourceId.isError()) {
                    Logger.w(TAG, "[executePlayNextItem] failed to setSource")
                    executeOnPlaybackError(
                        sourceId,
                        ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "failed to setSource"
                    )
                    return
                }

                if (mediaPlayer.getOffset(sourceId) != audioItem.stream.offsetInMilliseconds) {
                    mediaPlayer.seekTo(
                        sourceId,
                        audioItem.stream.offsetInMilliseconds
                    )
                }

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

                playSynchronizer.startSync(
                    it,
                    object : PlaySynchronizerInterface.OnRequestSyncListener {
                        override fun onGranted() {
                        }

                        override fun onDenied() {
                        }
                    })
                it.playContext?.let { playContext ->
                    playStackManager.add(playContext)
                }

                progressTimer.init(
                    audioItem.stream.progressReport?.progressReportDelayInMilliseconds
                        ?: ProgressTimer.NO_DELAY,
                    audioItem.stream.progressReport?.progressReportIntervalInMilliseconds
                        ?: ProgressTimer.NO_INTERVAL, progressListener, progressProvider
                )
            } else {
                // Resume or Seek cases
                if (mediaPlayer.getOffset(sourceId) != audioItem.stream.offsetInMilliseconds) {
                    mediaPlayer.seekTo(
                        sourceId,
                        audioItem.stream.offsetInMilliseconds
                    )
                }

                if (currentActivity == AudioPlayerAgentInterface.State.PAUSED) {
                    if (!mediaPlayer.resume(sourceId)) {
                        Logger.w(TAG, "[executePlayNextItem] resumeFailed")
                        executeOnPlaybackError(
                            sourceId,
                            ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                            "resumeFailed"
                        )
                        return
                    } else {
                        Logger.d(TAG, "[executePlayNextItem] resumeSucceeded")
                        playSynchronizer.startSync(
                            it,
                            object : PlaySynchronizerInterface.OnRequestSyncListener {
                                override fun onGranted() {
                                }

                                override fun onDenied() {
                                }
                            })
                        it.playContext?.let { playContext ->
                            playStackManager.add(playContext)
                        }
                    }
                }
            }
        }
    }

    private fun executeShouldResumeNextItem(currentToken: String, nextToken: String): Boolean {
        return currentToken == nextToken && !sourceId.isError() && currentActivity.isActive()
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info)
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
        removeDirective(info)
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            executeProvideState(contextSetter, namespaceAndName, stateRequestToken, true)
        }
    }

    private fun executeProvideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int,
        sendToken: Boolean
    ) {
        val policy = if (currentActivity == AudioPlayerAgentInterface.State.PLAYING) {
            StateRefreshPolicy.ALWAYS
        } else {
            StateRefreshPolicy.NEVER
        }

        contextSetter.setState(namespaceAndName, JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("playerActivity", currentActivity.name)
            if (token.isNotBlank() && currentActivity != AudioPlayerAgentInterface.State.IDLE) {
                addProperty("token", token)
            }
            addProperty("offsetInMilliseconds", getOffsetInMilliseconds())
            if (getDurationInMilliseconds() != MEDIA_PLAYER_INVALID_OFFSET) {
                addProperty("durationInMilliseconds", getDurationInMilliseconds())
            }
            lyricsPresenter?.getVisibility()?.let {
                addProperty("lyricsVisible", it)
            }
        }.toString(), policy, stateRequestToken)
    }

    private fun sendPlaybackStartedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_STARTED, offset)
    }

    private fun sendPlaybackFinishedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_FINISHED)
    }

    private fun sendPlaybackStoppedEvent() {
        sendEventWithOffset(EVENT_NAME_PLAYBACK_STOPPED)
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
        condition: () -> Boolean = { true }
    ) {
        sendEvent(name, offset, condition)
    }

    private fun sendPlaybackFailedEvent(type: ErrorType, errorMsg: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                currentItem?.apply {
                    val token = payload.audioItem.stream.token
                    val messageRequest = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        EVENT_NAME_PLAYBACK_FAILED,
                        VERSION
                    )
                        .payload(JsonObject().apply {
                            addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
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

                    messageSender.sendMessage(messageRequest)
                }
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    private fun sendProgressReportDelay(actual: Long) {
        sendEvent(EVENT_NAME_PROGRESS_REPORT_DELAY_ELAPSED, actual) { true }
    }

    private fun sendProgressReportInterval(actual: Long) {
        sendEvent(EVENT_NAME_PROGRESS_REPORT_INTERVAL_ELAPSED, actual) { true }
    }

    private fun sendNextCommandIssued() {
        sendEventWithOffset(
            name = NAME_NEXT_COMMAND_ISSUED,
            condition = { currentActivity.isActive() })
    }

    private fun sendPreviousCommandIssued() {
        sendEventWithOffset(
            name = NAME_PREVIOUS_COMMAND_ISSUED,
            condition = { currentActivity.isActive() })
    }

    private fun sendPlayCommandIssued() {
        sendEventWithOffset(
            name = NAME_PLAY_COMMAND_ISSUED,
            condition = { currentActivity.isActive() })
    }

    private fun sendPauseCommandIssued() {
        sendEventWithOffset(
            name = NAME_PAUSE_COMMAND_ISSUED,
            condition = { currentActivity.isActive() })
    }

    private fun sendStopCommandIssued() {
        sendEventWithOffset(
            name = NAME_STOP_COMMAND_ISSUED,
            condition = { currentActivity.isActive() })
    }

    private fun sendEvent(eventName: String, offset: Long, condition: () -> Boolean) {
        currentItem?.apply {
            val token = payload.audioItem.stream.token
            val messageRequest = EventMessageRequest.Builder(
                contextManager.getContextWithoutUpdate(namespaceAndName),
                NAMESPACE,
                eventName,
                VERSION
            ).payload(
                JsonObject().apply {
                    addProperty("playServiceId", playServiceId)
                    addProperty("token", token)
                    addProperty("offsetInMilliseconds", offset)
                }.toString()
            ).build()

            if (condition.invoke()) {
                messageSender.sendMessage(messageRequest)
                Logger.d(TAG, "[sendEvent] $messageRequest")
            } else {
                Logger.w(TAG, "[sendEvent] unsatisfied condition, so skip send.")
            }
        }
    }

    override fun onButtonPressed(button: PlaybackButton) {
        executor.submit {
            Logger.w(TAG, "[onButtonPressed] button: $button, state : $currentActivity")
            when (button) {
                PlaybackButton.PLAY -> {
                    executeResume()
//                    sendPlayCommandIssued()
                }
                PlaybackButton.PAUSE -> {
                    executePause(PauseReason.BY_PAUSE_DIRECTIVE)
//                    sendPauseCommandIssued()
                }
                PlaybackButton.STOP -> sendStopCommandIssued()
                PlaybackButton.NEXT -> sendNextCommandIssued()
                PlaybackButton.PREVIOUS -> sendPreviousCommandIssued()
            }
        }
    }

    override fun shutdown() {
        executor.submit {
            executeStop()
        }
    }
//    override fun onTogglePressed(toggle: PlaybackToggle, action: Boolean) {
//        Logger.w(TAG, "[onTogglePressed] not supported - $toggle, $action")
//    }

    private var displayDelegate: AudioPlayerDisplayInterface? = null

    override fun setElementSelected(
        templateId: String,
        token: String,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String = displayDelegate?.setElementSelected(templateId, token, callback)
        ?: throw IllegalStateException("Not allowed call for audio player's setElementSelected")

    override fun displayCardRendered(
        templateId: String,
        controller: AudioPlayerDisplayInterface.Controller?
    ) {
        displayDelegate?.displayCardRendered(templateId, controller)
    }

    override fun displayCardCleared(templateId: String) {
        displayDelegate?.displayCardCleared(templateId)
    }

    override fun setRenderer(renderer: AudioPlayerDisplayInterface.Renderer?) {
        displayDelegate?.setRenderer(renderer)
    }

    override fun stopRenderingTimer(templateId: String) {
        displayDelegate?.stopRenderingTimer(templateId)
    }

    fun setDisplay(display: AudioPlayerDisplayInterface?) {
        this.displayDelegate = display
    }

    private var lyricsPresenter: LyricsPresenter? = null

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
}