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
package com.skt.nugu.sdk.platform.android.mediaplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.*
import java.net.URI

/**
 * Default Implementation of [MediaPlayerInterface] for android
 */
class AndroidMediaPlayer(
    private val context: Context,
    private val player: MediaPlayer
) : UriSourcePlayablePlayer {

    companion object {
        private const val TAG = "AndroidMediaPlayer"
    }

    private var currentSourceId: SourceId = SourceId.ERROR()
    private var playerActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.IDLE
    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null
    private var durationListener: MediaPlayerControlInterface.OnDurationListener? = null

    init {
        player.setOnErrorListener { mp, what, extra ->
            playerActivity = AudioPlayerAgentInterface.State.STOPPED
            playbackEventListener?.onPlaybackError(
                currentSourceId,
                ErrorType.MEDIA_ERROR_UNKNOWN,
                "what  : $what / extra : $extra"
            )
            true
        }
        player.setOnBufferingUpdateListener { mp, percent ->
            bufferEventListener?.onBufferRefilled(currentSourceId)
        }

        player.setOnCompletionListener {
            Log.d(TAG, "[onCompletion]")
            playerActivity = AudioPlayerAgentInterface.State.FINISHED
            playbackEventListener?.onPlaybackFinished(currentSourceId)
        }
    }

    override fun setSource(uri: URI): SourceId {
        Log.d(TAG, "[setSource] $uri")
        try {
            player.reset()
            playerActivity = AudioPlayerAgentInterface.State.IDLE
            player.setDataSource(context, Uri.parse(uri.toString()))
            player.prepare()
            playerActivity = AudioPlayerAgentInterface.State.PAUSED
        } catch (e: Exception) {
            Log.e(TAG, "[setSource] $uri", e)
            return SourceId.ERROR()
        }

        currentSourceId.id++

        Thread {
            val duration = player.duration.toLong()
            if(duration < 0) {
                durationListener?.onRetrieved(currentSourceId,null)
            } else {
                durationListener?.onRetrieved(currentSourceId, duration)
            }
        }.start()
        
        return currentSourceId
    }

    override fun play(id: SourceId): Boolean {
        Log.d(TAG, "[play] $id")
        return if (id.id == currentSourceId.id && playerActivity == AudioPlayerAgentInterface.State.PAUSED) {
            try {
                player.start()
                playerActivity = AudioPlayerAgentInterface.State.PLAYING
                playbackEventListener?.onPlaybackStarted(id)
                true
            } catch (e: IllegalStateException) {
                // failed
                false
            }
        } else {
            // invalid id
            false
        }
    }

    override fun stop(id: SourceId): Boolean {
        Log.d(TAG, "[stop] $id")
        if (id.id == currentSourceId.id && playerActivity.isActive()) {
            player.stop()
            playerActivity = AudioPlayerAgentInterface.State.STOPPED
            playbackEventListener?.onPlaybackStopped(id)
            return true
        }

        return false
    }

    override fun pause(id: SourceId): Boolean {
        Log.d(TAG, "[pause] $id, ${player.isPlaying}")
        if (id.id == currentSourceId.id && player.isPlaying) {
            player.pause()
            playerActivity = AudioPlayerAgentInterface.State.PAUSED
            playbackEventListener?.onPlaybackPaused(id)
            return true
        }

        return false
    }

    override fun resume(id: SourceId): Boolean {
        Log.d(TAG, "[resume] $id")
        if (id.id == currentSourceId.id && playerActivity.isActive()) {
            player.start()
            playerActivity = AudioPlayerAgentInterface.State.PLAYING
            playbackEventListener?.onPlaybackResumed(id)
            return true
        }

        return false
    }

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean {
        if (id.id == currentSourceId.id && playerActivity.isActive()) {
            player.seekTo(offsetInMilliseconds.toInt())
            return true
        }

        return false
    }

    override fun getOffset(id: SourceId): Long {
        if (id.id == currentSourceId.id && playerActivity.isActive()) {
            return player.currentPosition.toLong()
        }

        return MEDIA_PLAYER_INVALID_OFFSET
    }

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        bufferEventListener = listener
    }

    override fun setOnDurationListener(listener: MediaPlayerControlInterface.OnDurationListener) {
        durationListener = listener
    }
}