/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.common.tts

import com.skt.nugu.sdk.agent.dialog.FocusHolderManager
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerControlInterface
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.mediaplayer.SourceId
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class TTSScenarioPlayer(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val focusManager: FocusManagerInterface,
    private val focusChannelName: String,
    private val focusHolderManager: FocusHolderManager,
    private val player: MediaPlayerInterface,
    audioPlayStackManager: PlayStackManagerInterface
) : MediaPlayerControlInterface.PlaybackEventListener
    , ChannelObserver
    , FocusHolderManager.OnStateChangeListener
{
    companion object {
        private const val TAG = "TTSScenarioPlayer"
        private val DUMMY_PLAY_SYNC_CALLBACK =
            object : PlaySynchronizerInterface.OnRequestSyncListener {
                override fun onGranted() {
                }

                override fun onDenied() {
                }
            }
    }

    interface Listener {
        fun onPlaybackStarted(source: Source)
        fun onPlaybackFinished(source: Source)
        fun onPlaybackStopped(source: Source)
        fun onPlaybackError(source: Source, type: ErrorType, error: String)
    }

    abstract class Source
        : PlaySynchronizerInterface.SynchronizeObject
        , FocusHolderManager.FocusHolder {
        var isCancelRequested = false
        internal var stopCalled = false

        abstract fun getReader(): Attachment.Reader?
        abstract fun onCanceled()
        abstract fun getPushPlayServiceId(): String?
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = LinkedHashSet<Listener>()

    private var lastImplicitStoppedSource: Source? = null
    private var preparedSource: Source? = null
    private var currentSource: Source? = null
    private var currentSourceId: SourceId = SourceId.ERROR()
    private val idAndSourceMap = HashMap<SourceId, Source>()
    private val ttsPlayContextProvider = TTSPlayContextProvider()

    private var focusState: FocusState = FocusState.NONE

    init {
        player.setPlaybackEventListener(this)
        focusHolderManager.addOnStateChangeListener(this)
        audioPlayStackManager.addPlayContextProvider(ttsPlayContextProvider)
    }

    fun addListener(listener: Listener) {
        executor.submit {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        executor.submit {
            listeners.remove(listener)
        }
    }

    fun prepare(source: Source) {
        Logger.d(TAG, "[prepare] source: $source")
        playSynchronizer.prepareSync(source)
        executor.submit {
            Logger.d(TAG, "[prepare] execute source: $source")
            // clear lastImplicitStoppedSource
            lastImplicitStoppedSource = null

            // cancel prepared
            preparedSource?.let {
                it.onCanceled()
                playSynchronizer.releaseSyncImmediately(it, DUMMY_PLAY_SYNC_CALLBACK)
                focusHolderManager.abandon(it)
            }
            // update prepared source
            preparedSource = source
            // cancel current
            executeStopPlayer()
        }
        focusHolderManager.request(source)
    }

    fun start(source: Source) {
        Logger.d(TAG, "[start] source: $source")
        executor.submit {
            Logger.d(TAG, "[start] execute source: $source, focus: $focusState")
            if (preparedSource != source) {
                Logger.d(TAG, "[start] source not match with preparedSource")
                return@submit
            }

            if(currentSource != null) {
                Logger.d(TAG, "[start] will be started on available.")
                return@submit
            }

            executeStartPreparedSourceIfExist()

        }
    }

    private fun executeStartPreparedSourceIfExist() {
        Logger.d(TAG, "[executeStartPreparedSource] preparedSource: $preparedSource")
        if (preparedSource == null) {
            return
        }

        if (focusState == FocusState.FOREGROUND) {
            executeStartPreparedSourceOnForeground()
        } else {
            if (!focusManager.acquireChannel(focusChannelName, this, TAG)) {
                Logger.e(TAG, "[executePlaySpeakInfo] not registered channel!")
            }
        }
    }

    private fun executeStartPreparedSourceOnForeground() {
        Logger.d(TAG, "[executeStartPreparedSourceOnForeground] $preparedSource")
        val source = preparedSource ?: return
        preparedSource = null
        currentSource = source
        focusHolderManager.request(source)
        playSynchronizer.startSync(source, DUMMY_PLAY_SYNC_CALLBACK)
        startPlayer(source)
    }

    private fun startPlayer(source: Source) {
        source.getReader()?.let {
            val sourceId = player.setSource(it)
            Logger.d(TAG, "[startPlayer] sourceId: $sourceId, source: $source")

            if (sourceId.isError()) {
                listeners.forEach { listener ->
                    listener.onPlaybackError(
                        source, ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "setSource failed"
                    )
                }
                playSynchronizer.releaseSync(source, DUMMY_PLAY_SYNC_CALLBACK)
                focusHolderManager.abandon(source)
                return
            }

            if (!player.play(sourceId)) {
                listeners.forEach { listener ->
                    listener.onPlaybackError(
                        source, ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "playFailed"
                    )
                }
                playSynchronizer.releaseSync(source, DUMMY_PLAY_SYNC_CALLBACK)
                focusHolderManager.abandon(source)
                return
            }
            idAndSourceMap[sourceId] = source
            currentSourceId = sourceId
        }
    }

    fun cancel(source: Source) {
        executor.submit {
            if (preparedSource == source) {
                preparedSource?.let {
                    it.onCanceled()
                    playSynchronizer.releaseSyncImmediately(it, DUMMY_PLAY_SYNC_CALLBACK)
                }
                preparedSource = null
            }

            if (currentSource == source) {
                executeStopPlayer()
            }
        }
    }

    private fun executeStopPlayer() {
        val source = idAndSourceMap[currentSourceId] ?: return

        if (!currentSourceId.isError() && !source.stopCalled) {
            if (player.stop(currentSourceId)) {
                source.stopCalled = true
            }
        }
    }

    override fun onFocusChanged(newFocus: FocusState) {
        Logger.d(TAG, "[onFocusChanged] current: $focusState, newFocus: $newFocus")
        executor.submit {
            Logger.d(TAG, "[onFocusChanged] execute - current: $focusState, newFocus: $newFocus")

            if (focusState == newFocus) {
                return@submit
            }
            focusState = newFocus

            when (newFocus) {
                FocusState.FOREGROUND -> {
                    executeStartPreparedSourceOnForeground()
                }
                else -> {
                    executeStopPlayer()
                }
            }
        }
    }

    override fun onPlaybackStarted(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStarted] id: $id")
        executor.submit {
            Logger.d(TAG, "[onPlaybackStarted] execute id: $id")
            if (currentSourceId == id) {
                currentSource?.let {
                    it.getPushPlayServiceId()?.let { playServiceId ->
                        ttsPlayContextProvider.onPlaybackStarted(playServiceId)
                    }
                    listeners.forEach { listener ->
                        listener.onPlaybackStarted(it)
                    }
                }
            }
        }
    }

    override fun onPlaybackFinished(id: SourceId) {
        Logger.d(TAG, "[onPlaybackFinished] id: $id")
        executor.submit {
            Logger.d(TAG, "[onPlaybackFinished] execute id: $id, current: $currentSourceId")

            if (currentSourceId == id) {
                currentSourceId = SourceId.ERROR()
                idAndSourceMap.remove(id)?.let {
                    if(it == currentSource) {
                        currentSource = null
                    }
                    ttsPlayContextProvider.onPlaybackFinished()
                    listeners.forEach { listener ->
                        listener.onPlaybackFinished(it)
                    }
                    playSynchronizer.releaseSync(it, DUMMY_PLAY_SYNC_CALLBACK)
                    focusHolderManager.abandon(it)
                }
                executeStartPreparedSourceIfExist()
            }
        }
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        Logger.d(TAG, "[onPlaybackError] id: $id, type: $type, error: $error")
        executor.submit {
            Logger.d(TAG, "[onPlaybackError] execute id: $id, type: $type, error: $error, current: $currentSourceId")

            if (currentSourceId == id) {
                currentSourceId = SourceId.ERROR()
                idAndSourceMap.remove(id)?.let {
                    if(it == currentSource) {
                        currentSource = null
                    }
                    ttsPlayContextProvider.onPlaybackStopped()
                    listeners.forEach { listener ->
                        listener.onPlaybackError(it, type, error)
                    }
                    playSynchronizer.releaseSync(it, DUMMY_PLAY_SYNC_CALLBACK)
                    focusHolderManager.abandon(it)
                }
                executeStartPreparedSourceIfExist()
            }
        }
    }

    override fun onPlaybackPaused(id: SourceId) {
        Logger.e(TAG, "[onPlaybackPaused] id: $id - never called")
    }

    override fun onPlaybackResumed(id: SourceId) {
        Logger.e(TAG, "[onPlaybackResumed] id: $id")
    }

    override fun onPlaybackStopped(id: SourceId) {
        Logger.d(TAG, "[onPlaybackStopped] id: $id")
        executor.submit {
            Logger.d(TAG, "[onPlaybackStopped] execute id: $id, current: $currentSourceId")

            if (currentSourceId == id) {
                currentSourceId = SourceId.ERROR()
                idAndSourceMap.remove(id)?.let {
                    if(it == currentSource) {
                        currentSource = null
                    }
                    ttsPlayContextProvider.onPlaybackStopped()
                    listeners.forEach { listener ->
                        listener.onPlaybackStopped(it)
                    }
                    if (it.isCancelRequested) {
                        playSynchronizer.releaseSyncImmediately(it, DUMMY_PLAY_SYNC_CALLBACK)
                    } else {
                        playSynchronizer.releaseSync(it, DUMMY_PLAY_SYNC_CALLBACK)
                    }
                    focusHolderManager.abandon(it)
                }

                executeStartPreparedSourceIfExist()
            }
        }
    }

    override fun onStateChanged(state: FocusHolderManager.State) {
        executor.submit {
            Logger.d(TAG, "[onStateChanged-FocusHolder] $state, $focusState, $preparedSource, $currentSource")

            if(state == FocusHolderManager.State.HOLD) {
                return@submit
            }

            if(focusState != FocusState.NONE && preparedSource == null && currentSource == null) {
                focusManager.releaseChannel(focusChannelName, this)
                focusState = FocusState.NONE
            }
        }
    }
}