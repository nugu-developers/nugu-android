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
package com.skt.nugu.sdk.agent.audioplayer

import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.display.AudioPlayerDisplayInterface
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.util.TimeUnit
import com.skt.nugu.sdk.core.interfaces.message.Header

/**
 * Interface for AudioPlayer Capability Agent
 */
interface AudioPlayerAgentInterface: AudioPlayerDisplayInterface {
    data class Context(
        val audioItemId: String,
        val templateId: String,
        val audioItemTemplate: String?,
        val offset: Long,
        val dialogRequestId: String
    )

    /**
     * Interface of a listener to be called when there has been an change of state
     */
    interface Listener {
        /** Called to notify an change of state
         * @param activity current activity
         * @param context current context
         */
        fun onStateChanged(activity: State, context: Context)
    }

    /**
     * Listener to be called when occur a playback event.
     */
    interface OnPlaybackListener {
        fun onPlaybackStarted(context: Context)
        fun onPlaybackFinished(context: Context)
        fun onPlaybackError(context: Context, type: ErrorType, error: String)
        fun onPlaybackPaused(context: Context)
        fun onPlaybackResumed(context: Context)
        fun onPlaybackStopped(context: Context, stopReason: StopReason)
    }

    /**
     *
     */
    enum class StopReason {
        PLAY_ANOTHER,
        STOP
    }

    interface OnDurationListener {
        /**
         * Notified when duration retrieved.
         * @param duration the duration, null if not available.
         */
        fun onRetrieved(duration: Long?, context: Context)
    }

    enum class State {
        IDLE,
        PLAYING,
        STOPPED,
        PAUSED,
        FINISHED;
        // Indicates that audio is currently playing, paused.
        fun isActive(): Boolean = when (this) {
            PLAYING, PAUSED -> true
            else -> false
        }
    }

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: Listener, requestCurrentState: Boolean = false)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)

    /** Add a playback listener
     * @param listener the listener that added
     */
    fun addOnPlaybackListener(listener: OnPlaybackListener)

    /**
     * Remove a playback listener
     * @param listener the listener that removed
     */
    fun removeOnPlaybackListener(listener: OnPlaybackListener)

    /** Add a listener to be called when duration retrieved
     * @param listener the listener that added
     */
    fun addOnDurationListener(listener: OnDurationListener, requestCurrentState: Boolean = false)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeOnDurationListener(listener: OnDurationListener)

    /** Set a presenter for lyrics
     * @param presenter the presenter to be set
     */
    fun setLyricsPresenter(presenter: LyricsPresenter?)

    interface RequestCommandHandler {
        fun handleRequestCommand(payload: String, header: Header): Boolean
    }

    fun setRequestCommandHandler(handler: RequestCommandHandler)

    /**
     * Starts or resumes playback.
     */
    fun play()

    /**
     * Stops playback
     */
    fun stop()

    /**
     * Starts next playback if available
     */
    fun next()

    /**
     * Starts previous playback if available
     */
    fun prev()

    /**
     * Pauses playback
     */
    fun pause()

    /**
     * Seeks to specified time position
     *
     * @param offsetInMilliseconds the offset in milliseconds from the start to seek to
     */
    fun seek(offsetInMilliseconds: Long)

    /**
     * Gets the current playback offset
     * @param unit the unit of offset which return (default=TimeUnit.SECONDS)
     * @return the current offset in seconds
     */
    fun getOffset(unit: TimeUnit = TimeUnit.SECONDS): Long

    /**
     * Request the favorite command if supported.
     * @param current the current favorite
     */
    fun requestFavoriteCommand(current: Boolean)

    enum class RepeatMode {
        ALL, ONE, NONE
    }

    /**
     * Request the repeat command if supported.
     * @param current the current repeat mode
     */
    fun requestRepeatCommand(current: RepeatMode)

    /**
     * Request the shuffle command if supported
     * @param current the current shuffle
     */
    fun requestShuffleCommand(current: Boolean)

    /**
     * This should be called when occur interaction(input event such as touch, drag, etc...) for display
     *
     * @param templateId the unique identifier for the template card
     */
    fun notifyUserInteractionOnDisplay(templateId: String)
}