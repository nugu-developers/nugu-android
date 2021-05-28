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

import com.skt.nugu.sdk.agent.mediaplayer.*
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.utils.Logger
import java.net.URI

class IntegratedMediaPlayer(
    private val audioPlayer: UriSourcePlayablePlayer,
    private val ttsPlayer: AttachmentPlayablePlayer
) : MediaPlayerInterface {
    companion object {
        private const val TAG = "IntegratedMediaPlayer"
        private const val MAX_SOURCE_ID = Int.MAX_VALUE / 2 - 1
    }

    private interface Listener :
        MediaPlayerControlInterface.PlaybackEventListener
        , MediaPlayerControlInterface.BufferEventListener
        , MediaPlayerControlInterface.OnDurationListener

    private val listenerForAudioPlayer = object : Listener {
        override fun onPlaybackStarted(id: SourceId) {
            playbackEventListener?.onPlaybackStarted(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onPlaybackFinished(id: SourceId) {
            playbackEventListener?.onPlaybackFinished(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
            playbackEventListener?.onPlaybackError(convertSourceIdFromAudioPlayerToIntegrated(id), type, error)
        }

        override fun onPlaybackPaused(id: SourceId) {
            playbackEventListener?.onPlaybackPaused(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onPlaybackResumed(id: SourceId) {
            playbackEventListener?.onPlaybackResumed(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onPlaybackStopped(id: SourceId) {
            playbackEventListener?.onPlaybackStopped(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onBufferUnderrun(id: SourceId) {
            bufferEventListener?.onBufferUnderrun(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onBufferRefilled(id: SourceId) {
            bufferEventListener?.onBufferRefilled(convertSourceIdFromAudioPlayerToIntegrated(id))
        }

        override fun onRetrieved(id: SourceId, duration: Long?) {
            durationListener?.onRetrieved(convertSourceIdFromAudioPlayerToIntegrated(id), duration)
        }
    }

    private val listenerForTtsPlayer = object : Listener {
        override fun onPlaybackStarted(id: SourceId) {
            playbackEventListener?.onPlaybackStarted(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onPlaybackFinished(id: SourceId) {
            playbackEventListener?.onPlaybackFinished(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
            playbackEventListener?.onPlaybackError(convertSourceIdFromTtsToIntegrated(id), type, error)
        }

        override fun onPlaybackPaused(id: SourceId) {
            playbackEventListener?.onPlaybackPaused(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onPlaybackResumed(id: SourceId) {
            playbackEventListener?.onPlaybackResumed(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onPlaybackStopped(id: SourceId) {
            playbackEventListener?.onPlaybackStopped(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onBufferUnderrun(id: SourceId) {
            bufferEventListener?.onBufferUnderrun(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onBufferRefilled(id: SourceId) {
            bufferEventListener?.onBufferRefilled(convertSourceIdFromTtsToIntegrated(id))
        }

        override fun onRetrieved(id: SourceId, duration: Long?) {
            durationListener?.onRetrieved(convertSourceIdFromTtsToIntegrated(id), duration)
        }
    }

    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null
    private var durationListener: MediaPlayerControlInterface.OnDurationListener? = null

    private var activePlayer: MediaPlayerControlInterface? = null

    init {
        Logger.d(TAG, "[init] $this")
        audioPlayer.setPlaybackEventListener(listenerForAudioPlayer)
        audioPlayer.setBufferEventListener(listenerForAudioPlayer)
        audioPlayer.setOnDurationListener(listenerForAudioPlayer)
        ttsPlayer.setPlaybackEventListener(listenerForTtsPlayer)
        ttsPlayer.setBufferEventListener(listenerForTtsPlayer)
        ttsPlayer.setOnDurationListener(listenerForTtsPlayer)
    }

    private fun convertSourceIdFromTtsToIntegrated(id: SourceId): SourceId {
        return if (id.isError()) {
            id
        } else {
            if(MAX_SOURCE_ID <= id.id) {
                // prevent overflow
                SourceId((id.id - MAX_SOURCE_ID) * 2 + 0)
            } else {
                SourceId(id.id * 2 + 0)
            }
        }
    }

    private fun convertSourceIdFromAudioPlayerToIntegrated(id: SourceId): SourceId {
        return if (id.isError()) {
            id
        } else {
            if(MAX_SOURCE_ID <= id.id) {
                // prevent overflow
                SourceId((id.id - MAX_SOURCE_ID) * 2 + 1)
            } else {
                SourceId(id.id * 2 + 1)
            }
        }
    }

    override fun setSource(attachmentReader: Attachment.Reader): SourceId {
        activePlayer = ttsPlayer
        return convertSourceIdFromTtsToIntegrated(ttsPlayer.setSource(attachmentReader))
    }

    override fun setSource(uri: URI, cacheKey: CacheKey?): SourceId {
        activePlayer = audioPlayer
        return convertSourceIdFromAudioPlayerToIntegrated(audioPlayer.setSource(uri, cacheKey))
    }

    override fun play(id: SourceId): Boolean = operationAtValidPlayer(id, false) {
        activePlayer?.play(it) ?: false
    }

    override fun stop(id: SourceId): Boolean = operationAtValidPlayer(id, false) {
        activePlayer?.stop(it) ?: false
    }

    override fun pause(id: SourceId): Boolean = operationAtValidPlayer(id, false) {
        activePlayer?.pause(it) ?: false
    }

    override fun resume(id: SourceId): Boolean = operationAtValidPlayer(id, false) {
        activePlayer?.resume(it) ?: false
    }

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean =
        operationAtValidPlayer(id, false) {
            activePlayer?.seekTo(it, offsetInMilliseconds) ?: false
        }

    override fun getOffset(id: SourceId): Long =
        operationAtValidPlayer(id, MEDIA_PLAYER_INVALID_OFFSET) {
            activePlayer?.getOffset(it) ?: MEDIA_PLAYER_INVALID_OFFSET
        }

    private fun <T> operationAtValidPlayer(id: SourceId, failureReturnValue: T, op: (convertedId: SourceId) -> T): T {
        if (id.isError()) {
            return failureReturnValue
        }

        return if (id.id % 2 == 0) {
            // tts id
            if (ttsPlayer == activePlayer) {
                op(SourceId(id.id / 2))
            } else {
                failureReturnValue
            }
        } else {
            if (audioPlayer == activePlayer) {
                op(SourceId((id.id - 1) /2))
            } else {
                failureReturnValue
            }
        }
    }

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        Logger.d(TAG, "[setPlaybackEventListener] set listener($listener) at $this")
        playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        bufferEventListener = listener
    }

    override fun setOnDurationListener(listener: MediaPlayerControlInterface.OnDurationListener) {
        durationListener = listener
    }

    override fun setVolume(volume: Float) {
        ttsPlayer.setVolume(volume)
        audioPlayer.setVolume(volume)
    }
}