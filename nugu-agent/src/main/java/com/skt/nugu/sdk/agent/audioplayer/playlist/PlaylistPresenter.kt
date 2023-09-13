package com.skt.nugu.sdk.agent.audioplayer.playlist

interface PlaylistPresenter: ShowPlaylistDirectiveHandler.PlaylistVisibilityController {
    /**
     * Returns the visibility for playlist
     * @return true: visible, false: invisible
     */
    fun getVisibility(): Boolean
}