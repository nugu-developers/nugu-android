package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject

interface PlaylistManager {
    fun setPlaylist(playlist: JsonObject)
    fun updatePlaylist(playlist: JsonObject)
    fun getPlaylist(): JsonObject?

    fun addListener(listener: OnPlaylistListener)
    fun removeListener(listener: OnPlaylistListener)
}