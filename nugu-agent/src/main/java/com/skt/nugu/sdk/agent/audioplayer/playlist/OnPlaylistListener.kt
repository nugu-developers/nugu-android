package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject

interface OnPlaylistListener {
    /**
     * Called when set a new playlist
     * @param playlist new playlist
     */
    fun onSetPlaylist(playlist: Playlist)

    /**
     * Called when update a current playlist
     * @param changes a changes for playlist
     * @param updated a playlist which updated (changes + current playlist)
     */
    fun onUpdatePlaylist(changes: JsonObject, updated: Playlist)
}