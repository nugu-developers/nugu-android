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
package com.skt.nugu.sampleapp.player.exo

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.extractor.ogg.OggExtractor
import com.google.android.exoplayer2.extractor.wav.WavExtractor
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.skt.nugu.sdk.agent.mediaplayer.*
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class ExoMediaPlayer(
    private val context: Context,
    private val retryWhenBehindLiveWindow: Boolean = true,
    private val cacheEnabled: Boolean = false
) : UriSourcePlayablePlayer {
    companion object {
        private const val TAG = "ExoMediaPlayer"

        const val QUERY_KEY_AUDIO_MIME_TYPE = "mime"
        const val MIME_TYPE_WAV = "audio/wav"
        const val MIME_TYPE_OPUS = MimeTypes.AUDIO_OPUS
        const val MIME_TYPE_MP3 = MimeTypes.AUDIO_MPEG

        private const val AWAIT_TIME_OUT_SECONDS = 30L

        private const val CACHE_SIZE = 1024 * 1024 * 100L // 100MB

        private fun convertToSDKErrorType(error: Int): ErrorType {
            return when (error) {
                ExoPlaybackException.TYPE_SOURCE -> ErrorType.MEDIA_ERROR_INVALID_REQUST
                ExoPlaybackException.TYPE_RENDERER,
                ExoPlaybackException.TYPE_UNEXPECTED -> ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR
                ExoPlaybackException.TYPE_REMOTE -> ErrorType.MEDIA_ERROR_INTERNAL_SERVER_ERROR
                else -> ErrorType.MEDIA_ERROR_UNKNOWN
            }
        }

        private fun convertToReadablePlaybackState(playbackState: Int): String {
            return when (playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> playbackState.toString()
            }
        }

        private fun isMainThread(): Boolean {
            return Looper.myLooper() === Looper.getMainLooper()
        }

        private var cache: Cache? = null
        private fun getCache(context: Context): Cache {
            return cache ?: SimpleCache(
                File(context.cacheDir, "exo").also {
                    if (!it.exists()) {
                        it.mkdir()
                    }
                },
                LeastRecentlyUsedCacheEvictor(CACHE_SIZE),
                null,
                null,
                false,
                false
            ).also {
                cache = it
            }
        }
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
            const val ERROR = 11
        }

        var retVal: Any? = null
    }

    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DefaultDataSource.Factory
    private lateinit var cacheDataSourceFactory: DefaultDataSource.Factory
    private lateinit var extractorsFactory: DefaultExtractorsFactory

    private var sourceId = SourceId.ERROR()
    private var lastPreparedUri: URI? = null
    private var lastPreparedCacheKey: String? = null

    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null
    private var durationListener: MediaPlayerControlInterface.OnDurationListener? = null
    private var durationCallSourceId = SourceId.ERROR()

    private val playerListener = object : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.d(
                TAG,
                "[onPlayerStateChanged] playWhenReady: $playWhenReady, playbackState: ${
                    convertToReadablePlaybackState(
                        playbackState
                    )
                }"
            )

            when (playbackState) {
                Player.STATE_IDLE -> Unit
                Player.STATE_BUFFERING -> eventNotifier.notifyBufferUnderRun(sourceId)
                Player.STATE_READY -> {
                    if (durationCallSourceId.id < sourceId.id) {
                        durationCallSourceId = sourceId
                        durationListener?.onRetrieved(sourceId, getDuration(sourceId))
                    }
                    eventNotifier.notifyBufferRefilled(sourceId)
                    handler.sendMessage(
                        handler.obtainMessage(
                            FuncMessage.SOURCE_PREPARED,
                            sourceId
                        )
                    )
                }
                Player.STATE_ENDED -> eventNotifier.notifyPlaybackFinished(sourceId)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.d(TAG, "[onPlayerError] error: $error")

            if (error is ExoPlaybackException) {
                val needToPrepare =
                    (retryWhenBehindLiveWindow && error.type == ExoPlaybackException.TYPE_SOURCE
                            && (error.sourceException is BehindLiveWindowException || error.sourceException is HlsPlaylistTracker.PlaylistResetException))
                if (needToPrepare) {
                    Log.i(TAG, "[onPlayerError] prepare silently ${error.sourceException}")

                    lastPreparedUri?.let { uri ->
                        player.setMediaSource(buildMediaSource(Uri.parse(uri.toString()), null))
                        player.prepare()

                        return@onPlayerError // swallow exception
                    }
                }
            }

            player.playWhenReady = false
            player.clearMediaItems()

            handler.sendMessage(handler.obtainMessage(FuncMessage.ERROR, sourceId))

            eventNotifier.notifyPlaybackError(
                sourceId,
                convertToSDKErrorType(if (error is ExoPlaybackException) error.type else ExoPlaybackException.TYPE_UNEXPECTED),
                error.message ?: ""
            )
        }
    }

    private val handler =
        MainHandler(this)
    private val eventNotifier =
        EventNotifier(handler)
    private val offsetCalculator =
        OffsetCalculator()

    init {
        if (isMainThread()) {
            initInternal()
        } else {
            await<Unit>(FuncMessage.INIT)
        }
    }

    private fun initInternal() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(Util.getUserAgent(context, "sample-app"))
            setAllowCrossProtocolRedirects(true)
        }

        dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        cacheDataSourceFactory = DefaultDataSource.Factory(
            context, CacheDataSource.Factory()
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setCache(getCache(context))
                .setCacheKeyFactory { dataSpec ->
                    val cacheKey = lastPreparedCacheKey
                    if (cacheKey != null) {
                        val uri = dataSpec.uri.toString()
                        val isHls = uri.contains(".m3u8") || uri.contains(".ts")
                        if (isHls) {
                            var end = uri.indexOf(".ts").let { if (it >= 0) it + 3 else it }
                            if (end < 0) {
                                end = uri.indexOf(".m3u8").let { if (it >= 0) it + 5 else it }
                            }

                            if (end >= 0) {
                                "${cacheKey}_${uri.substring(0, end)}"
                            } else {
                                cacheKey
                            }
                        } else {
                            cacheKey
                        }
                    } else {
                        ""
                    }.also {
                        Log.d(TAG, "[buildCacheKey] uri: ${dataSpec.uri}, cacheKey: $it")
                    }
                }.setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setEventListener(object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        Log.d(
                            TAG,
                            "[onCachedBytesRead] cacheSizeBytes: $cacheSizeBytes, cachedBytesRead: $cachedBytesRead"
                        )
                    }

                    override fun onCacheIgnored(reason: Int) {
                        Log.d(TAG, "[onCacheIgnored] reason: $reason")
                    }
                })
        )
        extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ).build()
        player.addListener(playerListener)

        eventNotifier.addPlaybackEventListener(offsetCalculator)
        eventNotifier.addBufferEventListener(offsetCalculator)
    }

    override fun setSource(uri: URI, cacheKey: CacheKey?): SourceId {
        Log.d(TAG, "[setSource] uri: $uri, cacheKey: $cacheKey")

        return if (isMainThread()) {
            setSourceInternal(uri, cacheKey?.getUniqueKey())
        } else {
            await(FuncMessage.SET_SOURCE, uri, cacheKey?.getUniqueKey()) { it as? SourceId }
                ?: SourceId.ERROR()
        }
    }

    private fun setSourceInternal(uri: URI, cacheKey: String?): SourceId {
        player.playWhenReady = false

        if (player.playbackState != Player.STATE_IDLE) {
            player.clearMediaItems()
        }

        lastPreparedUri = uri
        lastPreparedCacheKey = cacheKey
        val uriStr = uri.toString()
        player.setMediaSource(buildMediaSource(Uri.parse(uriStr), cacheKey))
        player.prepare()

        return SourceId(sourceId.id + 1).also {
            sourceId = it
            offsetCalculator.sourceId = it
        }
    }

    override fun play(id: SourceId): Boolean {
        Log.d(TAG, "[play] id: $id")

        return if (isMainThread()) {
            playInternal(id)
        } else {
            await(FuncMessage.PLAY, id) { it as? Boolean } ?: false
        }
    }

    private fun playInternal(id: SourceId): Boolean {
        if (sourceId == id) {
            player.playWhenReady = true

            eventNotifier.notifyPlaybackStarted(sourceId)

            return true
        }

        return false
    }

    override fun stop(id: SourceId): Boolean {
        Log.d(TAG, "[stop] id: $id")

        return if (isMainThread()) {
            stopInternal(id)
        } else {
            await(FuncMessage.STOP, id) { it as? Boolean } ?: false
        }
    }

    private fun stopInternal(id: SourceId): Boolean {
        if (sourceId == id && !isPlayerStoppedOrError()) {
            player.playWhenReady = false
            player.clearMediaItems()

            eventNotifier.notifyPlaybackStopped(sourceId)

            return true
        }

        return false
    }

    override fun pause(id: SourceId): Boolean {
        Log.d(TAG, "[pause] id: $id")

        return if (isMainThread()) {
            pauseInternal(id)
        } else {
            await(FuncMessage.PAUSE, id) { it as? Boolean } ?: false
        }
    }

    private fun pauseInternal(id: SourceId): Boolean {
        if (sourceId == id && !isPaused() && !isPlayerStoppedOrError()) {
            player.playWhenReady = false

            eventNotifier.notifyPlaybackPaused(sourceId)

            return true
        }

        return false
    }

    override fun resume(id: SourceId): Boolean {
        Log.d(TAG, "[resume] id: $id")

        return if (isMainThread()) {
            resumeInternal(id)
        } else {
            await(FuncMessage.RESUME, id) { it as? Boolean } ?: false
        }
    }

    private fun resumeInternal(id: SourceId): Boolean {
        if (sourceId == id && isPaused() && !isPlayerStoppedOrError()) {
            player.playWhenReady = true

            eventNotifier.notifyPlaybackResumed(sourceId)

            return true
        }

        return false
    }

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean {
        Log.d(TAG, "[seekTo] id: $id")

        return if (isMainThread()) {
            seekToInternal(id, offsetInMilliseconds)
        } else {
            await(FuncMessage.SEEK_TO, id, offsetInMilliseconds) { it as? Boolean } ?: false
        }
    }

    private fun seekToInternal(id: SourceId, offsetInMilliseconds: Long): Boolean {
        if (sourceId == id && !isPlayerStoppedOrError()) {
            if (!player.isCurrentMediaItemDynamic) {
                runCatching {
                    player.seekTo(offsetInMilliseconds)

                    return true
                }
            }
        }

        return false
    }

    override fun getOffset(id: SourceId): Long {
        return if (isMainThread()) {
            getOffsetInternal(id)
        } else {
            await(FuncMessage.GET_OFFSET, id) { it as? Long } ?: -1L
        }
    }

    private fun getOffsetInternal(id: SourceId): Long {
        if (player.playbackState != Player.STATE_IDLE) {
            return if (player.isCurrentMediaItemDynamic) {
                offsetCalculator.getOffset(id)
            } else {
                player.currentPosition
            }
        }

        return -1L
    }

    override fun setOnDurationListener(listener: MediaPlayerControlInterface.OnDurationListener) {
        durationListener = listener
    }

    fun getDuration(id: SourceId): Long {
        val duration = if (isMainThread()) {
            getDurationInternal(id)
        } else {
            await(FuncMessage.GET_DURATION, id) { it as? Long }?.let {
                if (it == C.TIME_UNSET) {
                    -1L
                } else {
                    it
                }
            } ?: -1L
        }

        Log.d(TAG, "[getDuration] id: $id, duration: $duration")

        return duration
    }

    private fun getDurationInternal(id: SourceId): Long {
        if (sourceId == id && player.playbackState != Player.STATE_IDLE) {
            return player.duration
        }

        return -1L
    }

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        Log.d(TAG, "[setPlaybackEventListener]")

        playbackEventListener?.let { previous ->
            eventNotifier.removePlaybackEventListener(previous)
        }

        eventNotifier.addPlaybackEventListener(listener)
        playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        Log.d(TAG, "[setBufferEventListener]")

        bufferEventListener?.let { previous ->
            eventNotifier.removeBufferEventListener(previous)
        }

        eventNotifier.addBufferEventListener(listener)
        bufferEventListener = listener
    }

    @Throws(IllegalStateException::class)
    private fun buildMediaSource(uri: Uri, cacheKey: String?): MediaSource {
        fun getDataSourceFactory(): DefaultDataSource.Factory {
            return if (cacheEnabled) {
                if (cacheKey != null) cacheDataSourceFactory else dataSourceFactory
            } else {
                dataSourceFactory
            }
        }

        val dataSourceFactory = getDataSourceFactory()
        Util.inferContentType(uri).let {
            when (it) {
                C.TYPE_HLS -> {
                    return HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri))
                }

                C.TYPE_OTHER -> {
                    // local resource
                    if (uri.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
                        try {
                            val rawUri =
                                RawResourceDataSource.buildRawResourceUri(
                                    ContentUris.parseId(uri).toInt()
                                )
                            val dataSource = RawResourceDataSource(context)
                            val audioType = uri.getQueryParameter(QUERY_KEY_AUDIO_MIME_TYPE)
                            if (audioType == null) {
                                throw IllegalStateException("Failed to query mime type of audio: $audioType")
                            }

                            val extractor = when (audioType) {
                                MIME_TYPE_WAV -> WavExtractor.FACTORY
                                MIME_TYPE_MP3 -> Mp3Extractor.FACTORY
                                MIME_TYPE_OPUS -> OggExtractor.FACTORY
                                else -> throw IllegalStateException("unsupported mime type: $audioType")
                            }

                            dataSource.open(DataSpec(rawUri))

                            return ProgressiveMediaSource.Factory(
                                DataSource.Factory { dataSource },
                                extractor
                            ).createMediaSource(MediaItem.fromUri(rawUri))
                        } catch (ignore: Exception) {

                        }
                    }

                    return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                        .createMediaSource(MediaItem.fromUri(uri))
                }

                else -> {
                    throw IllegalStateException("Unsupported type: $it")
                }
            }
        }
    }

    fun release() {
        player.removeListener(playerListener)
        player.release()
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    private fun isPaused(): Boolean {
        return !player.playWhenReady
    }

    private fun isPlayerStoppedOrError(): Boolean {
        return player.playbackState.let { it == Player.STATE_IDLE || it == Player.STATE_ENDED }
    }

    private fun <T> await(
        what: Int,
        data1: Any? = null,
        data2: Any? = null,
        block: ((retVal: Any?) -> T?)? = null
    ): T? {
        synchronized(this) {
            return runCatching {
                val funcMessage: FuncMessage
                CountDownLatch(1).also {
                    funcMessage =
                        FuncMessage(
                            it,
                            data1,
                            data2
                        )
                    handler.sendMessage(handler.obtainMessage(what, funcMessage))
                }.await(AWAIT_TIME_OUT_SECONDS, TimeUnit.SECONDS)
                block?.invoke(funcMessage.retVal)
            }.getOrNull()
        }
    }

    private class MainHandler(player: ExoMediaPlayer) : Handler(Looper.getMainLooper()) {
        private val playerRef = WeakReference(player)

        private var sourceSetFuncMessage: FuncMessage? = null
        private var seekToFuncMessage: FuncMessage? = null

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
                            funcMessage.retVal =
                                playerRef.get()?.setSourceInternal(it, funcMessage.data2 as? String)
                        }
                    }
                }

                FuncMessage.SEEK_TO -> {
                    (msg.obj as? FuncMessage)?.let { funcMessage ->
                        seekToFuncMessage = funcMessage
                        val sourceId = funcMessage.data1 as? SourceId
                        val offset = funcMessage.data2 as? Long
                        if (sourceId != null && offset != null) {
                            val seekResult =
                                playerRef.get()?.seekToInternal(sourceId, offset) ?: false
                            funcMessage.retVal = seekResult
                            if (!seekResult || playerRef.get()?.player?.playbackState == Player.STATE_READY) {
                                funcMessage.latch.countDown()
                                seekToFuncMessage = null
                            }
                        }
                    }
                }

                FuncMessage.SOURCE_PREPARED -> {
                    sourceSetFuncMessage?.let {
                        if (isSameSourceId(it.retVal as? SourceId, msg.obj as? SourceId)) {
                            it.latch.countDown()
                            sourceSetFuncMessage = null
                        }
                    }

                    seekToFuncMessage?.let {
                        if (isSameSourceId(it.data1 as? SourceId, msg.obj as? SourceId)) {
                            it.latch.countDown()
                            seekToFuncMessage = null
                        }
                    }
                }

                FuncMessage.ERROR -> {
                    sourceSetFuncMessage?.let {
                        if (isSameSourceId(it.retVal as? SourceId, msg.obj as? SourceId)) {
                            it.retVal = 0
                            it.latch.countDown()
                            sourceSetFuncMessage = null
                        }
                    }

                    seekToFuncMessage?.let {
                        if (isSameSourceId(it.data1 as? SourceId, msg.obj as? SourceId)) {
                            it.retVal = false
                            it.latch.countDown()
                            seekToFuncMessage = null
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

        private fun isSameSourceId(id1: SourceId?, id2: SourceId?): Boolean {
            if (id1 == null && id2 == null) {
                return false
            }

            return id1 == id2
        }

        private inline fun Message.runAndRelease(block: (FuncMessage.() -> Unit)) {
            (obj as? FuncMessage)?.let {
                block.invoke(it)

                it.latch.countDown()
            }
        }
    }
}

private class EventNotifier(private val handler: Handler) {
    private val playbackEventListeners =
        mutableListOf<MediaPlayerControlInterface.PlaybackEventListener>()
    private val bufferEventListeners =
        mutableListOf<MediaPlayerControlInterface.BufferEventListener>()

    fun removePlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        handler.post {
            playbackEventListeners.remove(listener)
        }
    }

    fun addPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        handler.post {
            playbackEventListeners.add(listener)
        }
    }

    fun removeBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        handler.post {
            bufferEventListeners.remove(listener)
        }
    }

    fun addBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        handler.post {
            bufferEventListeners.add(listener)
        }
    }

    fun notifyPlaybackStarted(id: SourceId) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackStarted(id)
            }
        }
    }

    fun notifyPlaybackFinished(id: SourceId) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackFinished(id)
            }
        }
    }

    fun notifyPlaybackError(id: SourceId, type: ErrorType, error: String) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackError(id, type, error)
            }
        }
    }

    fun notifyPlaybackPaused(id: SourceId) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackPaused(id)
            }
        }
    }

    fun notifyPlaybackResumed(id: SourceId) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackResumed(id)
            }
        }
    }

    fun notifyPlaybackStopped(id: SourceId) {
        handler.post {
            playbackEventListeners.forEach {
                it.onPlaybackStopped(id)
            }
        }
    }

    fun notifyBufferUnderRun(id: SourceId) {
        handler.post {
            bufferEventListeners.forEach {
                it.onBufferUnderrun(id)
            }
        }
    }

    fun notifyBufferRefilled(id: SourceId) {
        handler.post {
            bufferEventListeners.forEach {
                it.onBufferRefilled(id)
            }
        }
    }
}

class OffsetCalculator : MediaPlayerControlInterface.PlaybackEventListener,
    MediaPlayerControlInterface.BufferEventListener {
    @Volatile
    var sourceId = SourceId.ERROR()

    @Volatile
    private var totalPlayedTime = 0L

    @Volatile
    private var lastStartedTime = -1L

    fun getOffset(id: SourceId): Long {
        return if (sourceId == id) {
            if (lastStartedTime >= 0L) {
                totalPlayedTime + (SystemClock.elapsedRealtime() - lastStartedTime)
            } else {
                totalPlayedTime
            }
        } else {
            -1L
        }
    }

    override fun onPlaybackStarted(id: SourceId) {
        sourceId = id
        totalPlayedTime = 0L
        lastStartedTime = SystemClock.elapsedRealtime()
    }

    override fun onPlaybackFinished(id: SourceId) {
        absorb()
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        absorb()
    }

    override fun onPlaybackPaused(id: SourceId) {
        absorb()
    }

    override fun onPlaybackResumed(id: SourceId) {
        lastStartedTime = SystemClock.elapsedRealtime()
    }

    override fun onPlaybackStopped(id: SourceId) {
        absorb()
    }

    override fun onBufferUnderrun(id: SourceId) {
        // do nothing
    }

    override fun onBufferRefilled(id: SourceId) {
        // do nothing
    }

    private fun absorb() {
        if (lastStartedTime >= 0L) {
            totalPlayedTime += (SystemClock.elapsedRealtime() - lastStartedTime)
            lastStartedTime = -1L
        }
    }
}