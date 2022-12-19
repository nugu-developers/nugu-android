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

    private var playlist: Playlist? = null
    private val listeners = CopyOnWriteArraySet<OnPlaylistListener>()

    override fun setPlaylist(playServiceId: String, rawPlaylist: JsonObject) {
        val token = rawPlaylist.getPlaylistToken() ?: return

        val currentPlaylist = this.playlist

        if(currentPlaylist?.playServiceId == playServiceId && currentPlaylist.token == token) {
            updatePlaylist(playServiceId, rawPlaylist)
        } else {
            val newPlaylist = Playlist(playServiceId, token, rawPlaylist)
            this.playlist = newPlaylist

            listeners.forEach {
                it.onSetPlaylist(newPlaylist)
            }
        }
    }

    override fun updatePlaylist(playServiceId: String, changes: JsonObject) {
        val currentPlaylist = this.playlist

        if(currentPlaylist == null) {
            Logger.d(TAG, "[updatePlaylist] no playlist now.")
            return
        }

        if(currentPlaylist.playServiceId != playServiceId) {
            Logger.d(TAG, "[updatePlaylist] not matched playServiceId(current: ${currentPlaylist.playServiceId}, update: $playServiceId)")
            return
        }

        val token = changes.getPlaylistToken()
        if(currentPlaylist.token != token) {
            Logger.d(TAG, "[updatePlaylist] token($token) is not matched current playlist's token(${this.playlist?.token}).")
            return
        }

        updatePlaylistInternal(currentPlaylist, changes)
    }

    private fun updatePlaylistInternal(
        currentPlaylist: Playlist,
        changes: JsonObject
    ) {
        currentPlaylist.raw.mergePlaylist(changes)

        listeners.forEach {
            it.onUpdatePlaylist(changes, currentPlaylist)
        }
    }

    override fun getPlaylist(): Playlist? = this.playlist?.copy()

    override fun addListener(listener: OnPlaylistListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: OnPlaylistListener) {
        listeners.add(listener)
    }

    override fun onMetadataUpdate(playServiceId: String, jsonMetaData: String) {
        val playlist = getPlaylist(jsonMetaData)

        if(playlist != null) {
            updatePlaylist(playServiceId, playlist)
        }
    }

    private fun getPlaylist(metadata: String): JsonObject? = try {
        JsonParser.parseString(metadata).asJsonObject.getAsJsonObject("template")
            .getAsJsonObject("playlist")
    } catch (e: Exception) {
        null
    }
}