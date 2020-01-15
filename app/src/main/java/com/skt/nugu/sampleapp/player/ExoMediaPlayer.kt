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
package com.skt.nugu.sampleapp.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.annotation.AnyThread
import android.support.annotation.MainThread
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.skt.nugu.sdk.agent.mediaplayer.*
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference


class ExoMediaPlayer(private val context: Context) :
    UriSourcePlayablePlayer {
    companion object {
        private const val TAG = "ExoMediaPlayer"

        private const val DEBUG = true
    }

    class FuncMessage(val latch: CountDownLatch, val data1: Any? = null, val data2: Any? = null) {
        companion object {
            const val INIT = 1
            const val SET_SOURCE = 2
            const val SOURCE_PREPARED = 3
            const val PLAY = 4
            const val STOP = 5
            const val PAUSE = 6
            const val RESUME = 7
            const val SEEK_TO = 8
            const val GET_OFFSET = 9
            const val GET_DURATION = 10
        }

        var retVal: Any? = null
    }

    private lateinit var player: SimpleExoPlayer
    private lateinit var dataSourceFactory: DefaultDataSourceFactory

    private var sourceId = SourceId.ERROR()

    private var playbackEventListener: AtomicReference<MediaPlayerControlInterface.PlaybackEventListener?> = AtomicReference(null)
    private var bufferEventListener: AtomicReference<MediaPlayerControlInterface.BufferEventListener?> = AtomicReference(null)

    private val handler = MainHandler(this)

    init {
        await<Unit>(FuncMessage.INIT)
    }

    @MainThread
    private fun initInternal() {
        dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, "nugu-sample"))
        player = ExoPlayerFactory.newSimpleInstance(context)
        player.addListener(object: Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> bufferEventListener.get()?.onBufferUnderrun(sourceId)
                    Player.STATE_READY -> {
                        bufferEventListener.get()?.onBufferRefilled(sourceId)
                        handler.sendMessage(handler.obtainMessage(FuncMessage.SOURCE_PREPARED, sourceId))
                    }
                    Player.STATE_ENDED -> playbackEventListener.get()?.onPlaybackFinished(sourceId)
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                fun getErrorType(): ErrorType {
                    return when (error.type) {
                        ExoPlaybackException.TYPE_SOURCE -> ErrorType.MEDIA_ERROR_INVALID_REQUST
                        ExoPlaybackException.TYPE_RENDERER -> ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR
                        ExoPlaybackException.TYPE_REMOTE -> ErrorType.MEDIA_ERROR_INTERNAL_SERVER_ERROR
                        else -> ErrorType.MEDIA_ERROR_UNKNOWN
                    }
                }

                playbackEventListener.get()?.onPlaybackError(sourceId, getErrorType(), error.message ?: "")
            }
        })
    }

    @AnyThread
    override fun setSource(uri: URI): SourceId {
        if (DEBUG) {
            Log.d(TAG, "[setSource] uri: $uri")
        }

        return await(FuncMessage.SET_SOURCE, uri) { it as? SourceId } ?: SourceId.ERROR()
    }

    @MainThread
    private fun setSourceInternal(uri: URI): SourceId {
        if (player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = false
            player.stop()
        }

        player.prepare(buildMediaSource(Uri.parse(uri.toString())))

        return SourceId(++sourceId.id)
            .also { sourceId = it }
    }

    @AnyThread
    override fun play(id: SourceId): Boolean {
        if (DEBUG) {
            Log.d(TAG, "[play] id: $id")
        }

        return await(FuncMessage.PLAY, id) { it as? Boolean } ?: false
    }

    @MainThread
    private fun playInternal(id: SourceId): Boolean {
        if (sourceId == id && player.playbackState == Player.STATE_READY) {
            player.playWhenReady = true

            playbackEventListener.get()?.onPlaybackStarted(sourceId)

            return true
        }

        return false
    }

    @AnyThread
    override fun stop(id: SourceId): Boolean {
        if (DEBUG) {
            Log.d(TAG, "[stop] id: $id")
        }

        return await(FuncMessage.STOP, id) { it as? Boolean } ?: false
    }

    @MainThread
    private fun stopInternal(id: SourceId): Boolean {
        if (sourceId == id) {
            player.playWhenReady = false
            player.stop()

            playbackEventListener.get()?.onPlaybackStopped(sourceId)

            return true
        }

        return false
    }

    @AnyThread
    override fun pause(id: SourceId): Boolean {
        if (DEBUG) {
            Log.d(TAG, "[pause] id: $id")
        }

        return await(FuncMessage.PAUSE, id) { it as? Boolean } ?: false
    }

    @MainThread
    private fun pauseInternal(id: SourceId): Boolean {
        if (sourceId == id) {
            player.playWhenReady = false

            playbackEventListener.get()?.onPlaybackPaused(sourceId)

            return true
        }

        return false
    }

    @AnyThread
    override fun resume(id: SourceId): Boolean {
        if (DEBUG) {
            Log.d(TAG, "[resume] id: $id")
        }

        return await(FuncMessage.RESUME, id) { it as? Boolean } ?: false
    }

    @MainThread
    private fun resumeInternal(id: SourceId): Boolean {
        if (sourceId == id && player.playbackState == Player.STATE_READY) {
            player.playWhenReady = true

            playbackEventListener.get()?.onPlaybackResumed(sourceId)

            return true
        }

        return false
    }

    @AnyThread
    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean {
        if (DEBUG) {
            Log.d(TAG, "[seekTo] id: $id")
        }

        return await(FuncMessage.SEEK_TO, id, offsetInMilliseconds) { it as? Boolean } ?: false
    }

    @MainThread
    private fun seekToInternal(id: SourceId, offsetInMilliseconds: Long): Boolean {
        if (sourceId == id && player.playbackState != Player.STATE_IDLE) {
            player.seekTo(offsetInMilliseconds)

            return true
        }

        return false
    }

    @AnyThread
    override fun getOffset(id: SourceId): Long {
        if (DEBUG) {
            Log.d(TAG, "[getOffset] id: $id")
        }

        return await(FuncMessage.GET_OFFSET, id) { it as? Long } ?: MEDIA_PLAYER_INVALID_OFFSET
    }

    @MainThread
    private fun getOffsetInternal(id: SourceId): Long {
        if (sourceId == id && player.playbackState != Player.STATE_IDLE) {
            return player.currentPosition
        }

        return MEDIA_PLAYER_INVALID_OFFSET
    }

    @AnyThread
    override fun getDuration(id: SourceId): Long {
        if (DEBUG) {
            Log.d(TAG, "[getDuration] id: $id")
        }

        var duration = await(FuncMessage.GET_DURATION, id) { it as? Long }
        if(duration == C.TIME_UNSET) {
            duration =
                MEDIA_PLAYER_INVALID_OFFSET
        }

        return duration ?: MEDIA_PLAYER_INVALID_OFFSET
    }

    @MainThread
    private fun getDurationInternal(id: SourceId): Long {
        if (sourceId == id && player.playbackState != Player.STATE_IDLE) {
            return player.duration
        }

        return MEDIA_PLAYER_INVALID_OFFSET
    }

    @AnyThread
    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        if (DEBUG) {
            Log.d(TAG, "[setPlaybackEventListener]")
        }

        while (true) {
            playbackEventListener.get().let {
                if (playbackEventListener.compareAndSet(it, listener)) {
                    return
                }
            }
        }
    }

    @AnyThread
    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        if (DEBUG) {
            Log.d(TAG, "[setBufferEventListener]")
        }

        while (true) {
            bufferEventListener.get().let {
                if (bufferEventListener.compareAndSet(it, listener)) {
                    return
                }
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun buildMediaSource(uri: Uri): MediaSource {
        Util.inferContentType(uri).let {
            return when (it) {
                C.TYPE_HLS -> {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                }
                C.TYPE_OTHER -> {
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
                }
                else -> {
                    throw IllegalStateException("Unsupported type: $it")
                }
            }
        }
    }

    private fun <T> await(what: Int, data1: Any? = null, data2: Any? = null, block: ((retVal: Any?) -> T?)? = null): T? {
        val funcMessage: FuncMessage
        CountDownLatch(1).also {
            funcMessage = FuncMessage(it, data1, data2)
            handler.sendMessage(handler.obtainMessage(what, funcMessage))
        }.await()

        return block?.invoke(funcMessage.retVal)
    }

    private class MainHandler(player: ExoMediaPlayer) : Handler(Looper.getMainLooper()) {
        private val playerRef = WeakReference(player)

        private var sourceSetFuncMessage: FuncMessage? = null

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                FuncMessage.INIT -> {
                    msg.runAndRelease {
                        playerRef.get()?.initInternal()
                    }
                }

                FuncMessage.SET_SOURCE -> {
                    (msg.obj as? FuncMessage)?.let { funcMessage ->
                        sourceSetFuncMessage = funcMessage
                        (funcMessage.data1 as? URI)?.let {
                            funcMessage.retVal = playerRef.get()?.setSourceInternal(it)
                        }
                    }
                }

                FuncMessage.SOURCE_PREPARED -> {
                    sourceSetFuncMessage?.let {
                        if (it.retVal == msg.obj) {
                            it.latch.countDown()
                            sourceSetFuncMessage = null
                        }
                    }
                }

                FuncMessage.PLAY -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.playInternal(it)
                        }
                    }
                }

                FuncMessage.STOP -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.stopInternal(it)
                        }
                    }
                }

                FuncMessage.PAUSE -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.pauseInternal(it)
                        }
                    }
                }

                FuncMessage.RESUME -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.resumeInternal(it)
                        }
                    }
                }

                FuncMessage.SEEK_TO -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let { sourceId ->
                            (data2 as? Long)?.let { offset ->
                                retVal = playerRef.get()?.seekToInternal(sourceId, offset)
                            }
                        }
                    }
                }

                FuncMessage.GET_OFFSET -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.getOffsetInternal(it)
                        }
                    }
                }

                FuncMessage.GET_DURATION -> {
                    msg.runAndRelease {
                        (data1 as? SourceId)?.let {
                            retVal = playerRef.get()?.getDurationInternal(it)
                        }
                    }
                }
            }
        }

        private inline fun Message.runAndRelease(block: (FuncMessage.() -> Unit)) {
            (obj as? FuncMessage)?.let {
                block.invoke(it)

                it.latch.countDown()
            }
        }
    }
}