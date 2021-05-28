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

import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerControlInterface
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.agent.mediaplayer.SourceId
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class TTSScenarioPlayer(
    private val playSynchronizer: PlaySynchronizerInterface,
    private val focusManager: SeamlessFocusManagerInterface,
    private val focusChannelName: String,
    private val player: MediaPlayerInterface,
    audioPlayStackManager: PlayStackManagerInterface
) : MediaPlayerControlInterface.PlaybackEventListener
    , ChannelObserver {
    companion object {
        private const val TAG = "TTSScenarioPlayer"
    }

    interface Listener {
        fun onPlaybackStarted(source: Source)
        fun onPlaybackFinished(source: Source)
        fun onPlaybackStopped(source: Source)
        fun onPlaybackError(source: Source, type: ErrorType, error: String)
    }

    abstract class Source
        : PlaySynchronizerInterface.SynchronizeObject {
        var isCancelRequested = false
        var isStartAllowed = false
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
    
    private val focusRequester = object: SeamlessFocusManagerInterface.Requester {}
    private val focusChannel = SeamlessFocusManagerInterface.Channel(focusChannelName, this, TAG)

    init {
        Logger.d(TAG, "[init] $this")
        player.setPlaybackEventListener(this)
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
        if(focusState != FocusState.FOREGROUND) {
            focusManager.prepare(focusRequester)
        }
        executor.submit {
            Logger.d(TAG, "[prepare] execute source: $source")
            // clear lastImplicitStoppedSource
            lastImplicitStoppedSource = null

            // cancel prepared
            preparedSource?.let {
                it.onCanceled()
                playSynchronizer.releaseSyncImmediately(it)
            }
            // update prepared source
            preparedSource = source
            // cancel current
            executeStopPlayer()
        }
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

            source.isStartAllowed = true

            if(executeStartPreparedSourceIfExist()) {
                releaseFocus()
            }
        }
    }

    private fun releaseFocus() {
        if(focusState != FocusState.NONE && preparedSource == null && currentSource == null) {
            focusManager.release(focusRequester, focusChannel)
            focusState = FocusState.NONE
        }
    }

    private fun executeStartPreparedSourceIfExist(): Boolean {
        Logger.d(TAG, "[executeStartPreparedSource] preparedSource: $preparedSource")
        if (preparedSource == null) {
            return false
        }

        if (focusState == FocusState.FOREGROUND) {
            return executeStartPreparedSourceOnForeground()
        } else {
            if (!focusManager.acquire(focusRequester, focusChannel)) {
                Logger.e(TAG, "[executePlaySpeakInfo] not registered channel!")
            }
        }
        return true
    }

    private fun executeStartPreparedSourceOnForeground(): Boolean {
        Logger.d(TAG, "[executeStartPreparedSourceOnForeground] $preparedSource")
        val source = preparedSource ?: return false

        if(!source.isStartAllowed) {
            Logger.d(TAG, "[executeStartPreparedSourceOnForeground] start not allowed yet")
        }

        preparedSource = null
        currentSource = source
        playSynchronizer.startSync(source)

        return if(!startPlayer(source)) {
            playSynchronizer.releaseSync(source)
            false
        } else {
            true
        }
    }

    private fun startPlayer(source: Source): Boolean {
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
                return false
            }

            if (!player.play(sourceId)) {
                listeners.forEach { listener ->
                    listener.onPlaybackError(
                        source, ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR,
                        "playFailed"
                    )
                }
                return false
            }
            idAndSourceMap[sourceId] = source
            currentSourceId = sourceId
            return true
        }
        return false
    }

    fun cancel(source: Source) {
        executor.submit {
            if (preparedSource == source) {
                preparedSource?.let {
                    it.onCanceled()
                    playSynchronizer.releaseSyncImmediately(it)
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
                    if(!executeStartPreparedSourceOnForeground()) {
                        releaseFocus()
                    }
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
                    playSynchronizer.releaseSync(it)
                }
                if(executeStartPreparedSourceIfExist()) {
                    releaseFocus()
                }
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
                    playSynchronizer.releaseSync(it)
                }
                if(executeStartPreparedSourceIfExist()) {
                    releaseFocus()
                }
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
                        playSynchronizer.releaseSyncImmediately(it)
                    } else {
                        playSynchronizer.releaseSync(it)
                    }
                }

                if(executeStartPreparedSourceIfExist()) {
                    releaseFocus()
                }
            }
        }
    }
}