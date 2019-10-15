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
package com.skt.nugu.sdk.core.interfaces.capability.audioplayer

import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface

/**
 * Interface for AudioPlayer Capability Agent
 */
interface AudioPlayerAgentInterface: DisplayAgentInterface {
    data class Context(
        val audioItemId: String,
        val audioItemTemplate: String?,
        var offset: Long
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
    fun addListener(listener: Listener)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)

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
     * @param msec the offset in milliseconds from the start to seek to
     */
    fun seek(msec: Long)

    /**
     * Gets the current playback offset
     * @return the current offset in seconds
     */
    fun getOffset(): Long


    /**
     * Gets the current playback duration
     * @return the current duration in seconds
     */
    fun getDuration(): Long
}