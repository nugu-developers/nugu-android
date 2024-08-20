package com.skt.nugu.sdk.external.silvertray

import com.skt.nugu.sdk.agent.mediaplayer.AttachmentPlayablePlayer
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType
import com.skt.nugu.sdk.agent.mediaplayer.MediaPlayerControlInterface
import com.skt.nugu.sdk.agent.mediaplayer.SourceId
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.silvertray.player.BufferListener
import com.skt.nugu.silvertray.player.DurationListener
import com.skt.nugu.silvertray.player.EventListener
import com.skt.nugu.silvertray.player.Player
import com.skt.nugu.silvertray.player.Status

abstract class AbstractNuguOpusPlayer(protected val player: Player = Player()): AttachmentPlayablePlayer {
    companion object {
        private const val TAG = "AbstractNuguOpusPlayer"
    }

    private var currentSourceId: SourceId = SourceId.ERROR()
    private var status = Status.IDLE
    private var playbackEventListener: MediaPlayerControlInterface.PlaybackEventListener? = null
    private var bufferEventListener: MediaPlayerControlInterface.BufferEventListener? = null
    private var durationListener: MediaPlayerControlInterface.OnDurationListener? = null

    init {
        player.addListener(object : EventListener {
            override fun onError(message: String) {
                playbackEventListener?.onPlaybackError(currentSourceId, ErrorType.MEDIA_ERROR_INTERNAL_DEVICE_ERROR, message)
            }

            override fun onStatusChanged(status: Status) {
                Logger.d(TAG, "[onStatusChanged] status: $status")
                handleStatusChanged(status)
            }
        })
        player.addDurationListener(object : DurationListener {
            override fun onFoundDuration(duration: Long) {
                Logger.d(TAG, "[onFoundDuration] duration: $duration")
                durationListener?.onRetrieved(currentSourceId, duration)
            }
        })
        player.addBufferListener(object: BufferListener {
            override fun onBufferRefilled() {
                bufferEventListener?.onBufferRefilled(currentSourceId)
            }

            override fun onBufferUnderrun() {
                bufferEventListener?.onBufferUnderrun(currentSourceId)
            }
        })
    }

    private fun handleStatusChanged(status: Status) {
        val prevStatus = this.status
        this.status = status
        val listener = this.playbackEventListener ?: return
        when (status) {
            Status.IDLE -> {
                if(prevStatus == Status.READY) {
                    listener.onPlaybackStarted(currentSourceId)
                }
                listener.onPlaybackStopped(currentSourceId)
            }
            Status.STARTED -> {
                if (prevStatus == Status.PAUSED) {
                    listener.onPlaybackResumed(currentSourceId)
                } else {
                    listener.onPlaybackStarted(currentSourceId)
                }
            }
            Status.PAUSED -> {
                listener.onPlaybackPaused(currentSourceId)
            }
            Status.ENDED -> {
                if(prevStatus == Status.READY) {
                    listener.onPlaybackStarted(currentSourceId)
                }
                listener.onPlaybackFinished(currentSourceId)
            }
            else -> {
            }
        }
    }

    override fun setSource(attachmentReader: Attachment.Reader): SourceId {
        if(status == Status.READY || status == Status.STARTED) {
            player.reset()
        }

        prepareSource(RawCBRStreamSource(attachmentReader))
        currentSourceId.id++
        Logger.d(TAG, "[setSource] ${currentSourceId.id}")
        return currentSourceId
    }

    internal abstract fun prepareSource(source: RawCBRStreamSource)

    override fun play(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        Logger.d(TAG, "[play] $id")
        player.start()
        return true
    }

    override fun stop(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        Logger.d(TAG, "[stop] $id")
        player.reset()
        return true
    }

    override fun pause(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }

        if(this.status == Status.ENDED) {
            return false
        }

        Logger.d(TAG, "[pause] $id")
        player.pause()
        return true
    }

    override fun resume(id: SourceId): Boolean {
        if(currentSourceId.id != id.id) {
            return false
        }
        player.start()
        return true
    }

    override fun seekTo(id: SourceId, offsetInMilliseconds: Long): Boolean {
        // TODO : Impl
        return false
    }

    override fun getOffset(id: SourceId): Long {
        return player.getCurrentPosition()
    }

    override fun setPlaybackEventListener(listener: MediaPlayerControlInterface.PlaybackEventListener) {
        this.playbackEventListener = listener
    }

    override fun setBufferEventListener(listener: MediaPlayerControlInterface.BufferEventListener) {
        this.bufferEventListener = listener
    }

    override fun setOnDurationListener(listener: MediaPlayerControlInterface.OnDurationListener) {
        this.durationListener = listener
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }
}
