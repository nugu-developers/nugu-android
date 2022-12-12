package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.audioplayer.metadata.AudioPlayerMetadataDirectiveHandler
import com.skt.nugu.sdk.agent.util.deepMerge
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArraySet

class AudioPlayerPlaylistManager : PlaylistManager, AudioPlayerMetadataDirectiveHandler.Listener {
    companion object {
        private const val TAG = "AudioPlayerPlaylistManager"
    }

    internal data class Playlist(
        val token: String,
        val raw: JsonObject
    )

    private var playlist: Playlist? = null
    private val listeners = CopyOnWriteArraySet<OnPlaylistListener>()

    override fun setPlaylist(playlist: JsonObject) {
        val token = playlist.getPlaylistToken()

        if(token != null) {
            this.playlist = Playlist(token, playlist)

            listeners.forEach {
                it.onSetPlaylist(playlist)
            }
        }
    }

    override fun updatePlaylist(changes: JsonObject) {
        val token = changes.getPlaylistToken()

        val currentPlaylist = this.playlist

        if(currentPlaylist == null) {
            Logger.w(TAG, "[updatePlaylist] no playlist now.")
            return
        }

        if(currentPlaylist.token == token) {
            currentPlaylist.raw.deepMerge(changes)

            listeners.forEach {
                it.onUpdatePlaylist(changes, currentPlaylist.raw)
            }
        } else {
            Logger.w(TAG, "[updatePlaylist] token($token) is not matched current playlist's token(${this.playlist?.token}).")
        }
    }

    override fun getPlaylist(): JsonObject? = this.playlist?.raw

    override fun addListener(listener: OnPlaylistListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: OnPlaylistListener) {
        listeners.add(listener)
    }

    override fun onMetadataUpdate(playServiceId: String, jsonMetaData: String) {
        val playlist = getPlaylist(jsonMetaData)

        if(playlist != null) {
            updatePlaylist(playlist)
        }
    }

    private fun getPlaylist(metadata: String): JsonObject? = try {
        JsonParser.parseString(metadata).asJsonObject.getAsJsonObject("template")
            .getAsJsonObject("playlist")
    } catch (e: Exception) {
        null
    }
}