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
import java.net.URI

class IntegratedMediaPlayer(
    private val audioPlayer: UriSourcePlayablePlayer,
    private val ttsPlayer: AttachmentPlayablePlayer
) : MediaPlayerInterface
    , MediaPlayerControlInterface.PlaybackEventListener
    , MediaPlayerControlInterface.BufferEventListener
    , MediaPlayerControlInterface.OnDurationListener {
    companion object {
        private const val TAG = "IntegratedMediaPlayer"
    }

    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null
    private var durationListener: MediaPlayerControlInterface.OnDurationListener? = null

    private var activePlayer: MediaPlayerControlInterface? = null

    init {
        audioPlayer.setPlaybackEventListener(this)
        audioPlayer.setBufferEventListener(this)
        audioPlayer.setOnDurationListener(this)
        ttsPlayer.setPlaybackEventListener(this)
        ttsPlayer.setBufferEventListener(this)
        ttsPlayer.setOnDurationListener(this)
    }

    override fun setSource(attachmentReader: Attachment.Reader): SourceId {
        activePlayer = ttsPlayer
        return ttsPlayer.setSource(attachmentReader)
    }

    override fun setSource(uri: URI): SourceId {
        activePlayer = audioPlayer
        return audioPlayer.setSource(uri)
    }

    override fun play(id: SourceId): Boolean = activePlayer?.play(id) ?: false

    override fun stop(id: SourceId): Boolean = activePlayer?.stop(id) ?: false

    override fun pause(id: SourceId): Boolean = activePlayer?.pause(id) ?: false

    override fun resume(id: SourceId): Boolean = activePlayer?.resume(id) ?: false

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean = activePlayer?.seekTo(id, offsetInMilliseconds) ?: false

    override fun getOffset(id: SourceId): Long = activePlayer?.getOffset(id) ?: MEDIA_PLAYER_INVALID_OFFSET

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        bufferEventListener = listener
    }

    override fun setOnDurationListener(listener: MediaPlayerControlInterface.OnDurationListener) {
        durationListener = listener
    }

    override fun onPlaybackStarted(id: SourceId) {
        playbackEventListener?.onPlaybackStarted(convertSourceId(id))
    }

    override fun onPlaybackFinished(id: SourceId) {
        playbackEventListener?.onPlaybackFinished(convertSourceId(id))
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        playbackEventListener?.onPlaybackError(convertSourceId(id), type, error)
    }

    override fun onPlaybackPaused(id: SourceId) {
        playbackEventListener?.onPlaybackPaused(convertSourceId(id))
    }

    override fun onPlaybackResumed(id: SourceId) {
        playbackEventListener?.onPlaybackResumed(convertSourceId(id))
    }

    override fun onPlaybackStopped(id: SourceId) {
        playbackEventListener?.onPlaybackStopped(convertSourceId(id))
    }

    override fun onBufferUnderrun(id: SourceId) {
        bufferEventListener?.onBufferUnderrun(convertSourceId(id))
    }

    override fun onBufferRefilled(id: SourceId) {
        bufferEventListener?.onBufferRefilled(convertSourceId(id))
    }

    override fun onRetrieved(id: SourceId, duration: Long?) {
        durationListener?.onRetrieved(convertSourceId(id), duration)
    }

    private fun convertSourceId(id: SourceId): SourceId {
        if(id.isError()) {
            return id
        }

        if(activePlayer == ttsPlayer) {
            return SourceId(id.id * 2 + 0)
        } else {
            return SourceId(id.id * 2 + 1)
        }
    }
}