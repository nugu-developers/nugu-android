package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.audioplayer.playlist.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlaylistFragment : Fragment(), PlaylistDataListener {

    companion object {
        const val TAG = "PlaylistFragment"

        fun newInstance(
            playlist: Playlist?, eventListener: PlaylistEventListener
        ): PlaylistFragment {
            return PlaylistFragment().apply {
                this.playlist = playlist
                this.eventListener = eventListener
            }
        }
    }

    private var playlistBottomMargin = 0
    private val viewModel: PlaylistViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(PlaylistViewModel::class.java).apply {
            this.playlistBottomMargin = this@PlaylistFragment.playlistBottomMargin
        }
    }

    private lateinit var playlistView: PlaylistView
    private var playlist: Playlist? = null
    private var eventListener: PlaylistEventListener? = null

    private val coroutineJobs = arrayListOf<Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setEventListener(eventListener)
        viewModel.setPlaylist(playlist ?: return)
        playlist = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return PlaylistView.createView(playlist?.playServiceId, requireContext()).also {
            playlistView = it
            playlistView.setPlaylistViewModel(viewModel)
        }.asView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlistView.onParentFragmentViewCreated()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineJobs.forEach {
            it.cancel()
        }

        coroutineJobs.clear()
        playlistView.onParentFragmentViewDestroyed()
    }

    override fun onSetPlaylist(playlist: Playlist) {
        viewModel.setPlaylist(playlist)
    }

    override fun onUpdatePlaylist(changes: JsonObject, updated: Playlist) {
        viewModel.updatePlaylist(changes, updated)
    }

    override fun onClearPlaylist() {
        viewModel.clearPlaylist()
    }

    fun setPlaylistBottomMargin(bottomMargin: Int) {
        playlistBottomMargin = bottomMargin
    }

    fun observeEditMode(listener: (Boolean) -> Unit) {
        //todo.
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.editMode.collectCancelable {
                listener.invoke(it)
            }
        }.apply(coroutineJobs::add)
    }

    private fun <T> Flow<T>.collectCancelable(block: (T) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            collect {
                block.invoke(it)
            }
        }.apply(coroutineJobs::add)
    }
}



