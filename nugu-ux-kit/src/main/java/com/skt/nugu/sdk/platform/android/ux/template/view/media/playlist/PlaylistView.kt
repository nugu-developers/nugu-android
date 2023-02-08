package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import android.content.Context
import android.view.View
import androidx.annotation.LayoutRes

interface PlaylistView {
    companion object {

        /**
         * key : playServiceId
         * value : Playlist Constructor
         */
        val playlistConstructor: HashMap<List<String>, (Context, String?) -> PlaylistView> by lazy {
            HashMap()
        }

        fun createView(
            playServiceId: String?, context: Context
        ): PlaylistView {
            playlistConstructor.keys.find { it.contains(playServiceId) }?.let { key ->
                playlistConstructor[key]?.invoke(context, playServiceId)?.run {
                    return this
                }
            }

            return DefaultPlaylistView(playServiceId, context)
        }

        /**
         *  for Custom Layout.
         *  This must include every res in R.layout.nugu_fragment_playlist
         */
        @LayoutRes
        var playlistLayoutId: Int? = null

        /**
         *  for Custom Layout.
         *  This must include every res in R.layout.nugu_view_holder_playlist_item
         */
        @LayoutRes
        var playlistItemLayoutId: Int? = null
    }

    fun asView(): View = this as View

    fun setPlaylistViewModel(vm: PlaylistViewModel) {}

    fun onParentFragmentViewCreated() {}
    fun onParentFragmentViewDestroyed() {}
}