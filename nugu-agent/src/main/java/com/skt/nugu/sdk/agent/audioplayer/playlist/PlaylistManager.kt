package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject

interface PlaylistManager {
    fun setPlaylist(playServiceId: String, rawPlaylist: JsonObject)
    fun updatePlaylist(playServiceId: String, rawPlaylist: JsonObject)
    fun clearPlaylist()
    fun getPlaylist(): Playlist?

    fun addListener(listener: OnPlaylistListener)
    fun removeListener(listener: OnPlaylistListener)
}