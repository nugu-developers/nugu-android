package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.audioplayer.playlist.Playlist
import com.skt.nugu.sdk.platform.android.ux.R
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
            playlist: Playlist?,
            eventListener: PlaylistEventListener
        ): PlaylistFragment {
            return PlaylistFragment().apply {
                this.playlist = playlist
                this.eventListener = eventListener
            }
        }
    }

    private val dragHelper: ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition

            if (from in 0 until adapter.itemCount && to in 0 until adapter.itemCount) {
                viewModel.moveItem(from, to)
            }

            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // do nothing.
        }

        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return true
        }
    })

    private val viewModel: PlaylistViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(PlaylistViewModel::class.java)
    }

    private var playlist: Playlist? = null
    private var eventListener: PlaylistEventListener? = null

    private lateinit var recyclerView: RecyclerView
    private val adapter by lazy { PlaylistAdapter(viewModel) }

    private lateinit var btnEdit: TextView
    private lateinit var btnBtn: TextView  // btn from ButtonObject

    private lateinit var btnComplete: TextView

    private lateinit var viewTitleContainer: View
    private lateinit var viewEditTopButtonContainer: View
    private lateinit var viewEditBottomButtonContainer: View
    private lateinit var btnSelectAll: TextView
    private lateinit var btnDelete: TextView

    var playlistBottomMargin = 0
    private val playlistBottomMarginOnEditMode: Int by lazy { requireContext().resources.getDimensionPixelSize(R.dimen.media_player_playlist_edit_mode_button_height) }

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
        return inflater.inflate(R.layout.nugu_fragment_playlist, container, false).also {
            recyclerView = it.findViewById(R.id.view_play_list)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            dragHelper.attachToRecyclerView(recyclerView)
            (recyclerView.layoutParams as? MarginLayoutParams)?.bottomMargin = playlistBottomMargin

            viewTitleContainer = it.findViewById(R.id.view_title_container)
            viewEditTopButtonContainer = it.findViewById(R.id.view_edit_top_container)
            viewEditBottomButtonContainer = it.findViewById(R.id.view_edit_bottom_container)

            btnEdit = it.findViewById<TextView>(R.id.btn_edit).apply {
                setOnClickListener {
                    viewModel.onEditBtnClicked()
                }
            }

            btnBtn = it.findViewById<TextView>(R.id.btn_btn).apply {
                setOnClickListener {
                    viewModel.onButtonClicked()
                }
            }

            btnSelectAll = it.findViewById<TextView?>(R.id.btn_select_all).apply {
                setOnClickListener {
                    viewModel.onSelectAllClicked()
                }
            }

            btnDelete = it.findViewById<TextView?>(R.id.btn_delete).apply {
                setOnClickListener {
                    viewModel.onDeleteBtnClicked()
                }
            }

            btnComplete = it.findViewById<TextView?>(R.id.btn_complete).apply {
                setOnClickListener {
                    viewModel.onCompleteBtnClicked()
                }
            }

            it.findViewById<View>(R.id.btn_cancel).setOnClickListener {
                viewModel.onBtnCancelClicked()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineJobs.forEach {
            it.cancel()
        }

        coroutineJobs.clear()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {
        viewModel.playlist.collectCancelable {
            adapter.setList(it)
        }

        viewModel.updatePlaylist.collectCancelable {
            adapter.setList(viewModel.playlist.value)
        }

        viewModel.updatePlaylistItem.collectCancelable {
            adapter.updateItem(it, viewModel.playlist.value[it])
        }

        viewModel.removePlaylistItem.collectCancelable {
            adapter.removeItem(it)
        }

        viewModel.movePlaylistItem.collectCancelable {
            adapter.moveData(it.first, it.second)
        }

        viewModel.title.collectCancelable {
            requireView().findViewById<TextView>(R.id.tv_title)?.text = it
        }

        viewModel.editButton.collectCancelable {
            btnEdit.visibility = if (it == null) View.GONE else View.VISIBLE
            btnEdit.text = it?.text?.text
        }

        viewModel.button.collectCancelable {
            btnBtn.visibility = if (it == null) View.GONE else View.VISIBLE
            btnBtn.text = it?.text
        }

        viewModel.editMode.collectCancelable {
            adapter.setEditMode(it)

            viewEditTopButtonContainer.visibility = if (it) View.VISIBLE else View.GONE
            viewEditBottomButtonContainer.visibility = if (it) View.VISIBLE else View.GONE
            viewTitleContainer.visibility = if (it) View.GONE else View.VISIBLE

            (recyclerView.layoutParams as? MarginLayoutParams)?.bottomMargin = if (it) playlistBottomMarginOnEditMode else playlistBottomMargin
        }

        viewModel.startDrag.collectCancelable {
            recyclerView.findViewHolderForAdapterPosition(it)?.apply(dragHelper::startDrag)
        }

        viewModel.selectedState.collectCancelable {
            btnSelectAll.isSelected = it.first
            btnSelectAll.text = getText(if (it.first) R.string.nugu_playlist_deselect_all else R.string.nugu_playlist_select_all)
            btnDelete.isEnabled = it.second
        }

        viewModel.completable.collectCancelable {
            btnComplete.isEnabled = it
        }
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

    fun observeEditMode(listener: (Boolean) -> Unit) {
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



