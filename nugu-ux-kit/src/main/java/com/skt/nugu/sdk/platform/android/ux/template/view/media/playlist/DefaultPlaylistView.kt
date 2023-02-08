package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.widget.NuguSimpleDialogView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

open class DefaultPlaylistView constructor(
    protected val playServiceId: String?, context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), PlaylistView {

    private constructor(context: Context) : this("", context)
    private constructor(context: Context, attrs: AttributeSet?) : this("", context)
    private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this("", context)

    protected val coroutineJobs = arrayListOf<Job>()

    protected lateinit var viewModel: PlaylistViewModel
    protected lateinit var recyclerView: RecyclerView
    protected val layoutManager by lazy { LinearLayoutManager(context, RecyclerView.VERTICAL, false) }
    protected val adapter by lazy {
        PlaylistAdapter(viewModel).apply {
            onViewClicked = {
                onViewClicked(it)
            }
        }
    }

    protected lateinit var btnEdit: TextView
    protected lateinit var btnBtn: TextView  // btn from ButtonObject

    protected lateinit var btnComplete: TextView

    protected lateinit var viewTitleContainer: View
    protected lateinit var viewEditTopButtonContainer: View
    protected lateinit var viewEditBottomButtonContainer: View
    protected lateinit var btnSelectAll: TextView
    protected lateinit var btnDelete: TextView

    protected val dragHelper: ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
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
                adapter.moveData(from, to)
                return true
            }

            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // do nothing.
        }
    })
    protected val playlistBottomMarginOnEditMode: Int by lazy { context.resources.getDimensionPixelSize(R.dimen.media_player_playlist_edit_mode_button_height) }

    init {
        LayoutInflater.from(context).inflate(PlaylistView.playlistLayoutId ?: R.layout.nugu_fragment_playlist, this, true)
    }

    protected open fun initViews() {
        recyclerView = findViewById(R.id.nugu_playlist_play_list)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator?.changeDuration = 0L

        dragHelper.attachToRecyclerView(recyclerView)
        (recyclerView.layoutParams as? MarginLayoutParams)?.bottomMargin = viewModel.playlistBottomMargin

        viewTitleContainer = findViewById(R.id.nugu_playlist_title_container)
        viewEditTopButtonContainer = findViewById(R.id.nugu_playlist_edit_top_container)
        viewEditBottomButtonContainer = findViewById(R.id.nugu_playlist_edit_bottom_container)

        btnEdit = findViewById<TextView>(R.id.nugu_playlist_btn_edit).apply {
            setOnClickListener {
                viewModel.onEditBtnClicked()
                onViewClicked(this)
            }
        }

        btnBtn = findViewById<TextView>(R.id.nugu_playlist_btn_btn).apply {
            setOnClickListener {
                viewModel.onButtonClicked()
                onViewClicked(this)
            }
        }

        btnSelectAll = findViewById<TextView?>(R.id.nugu_playlist_btn_select_all).apply {
            setOnClickListener {
                viewModel.onSelectAllClicked()
                onViewClicked(this)
            }
        }

        btnDelete = findViewById<TextView?>(R.id.nugu_playlist_btn_delete).apply {
            setOnClickListener {
                onDeleteClicked()
                onViewClicked(this)
            }
        }

        btnComplete = findViewById<TextView?>(R.id.nugu_playlist_btn_complete).apply {
            setOnClickListener {
                viewModel.onCompleteBtnClicked()
                onViewClicked(this)
            }
        }

        findViewById<View>(R.id.nugu_playlist_btn_cancel).setOnClickListener {
            onCancelClicked()
            onViewClicked(it)
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    protected open fun observeViewModel() {
        viewModel.playlist.collectCancelable { list ->
            adapter.setList(list)
            list.indexOfFirst { it.isPlaying }.takeIf { it >= 0 }?.let { playingPosition ->
                layoutManager.scrollToPositionWithOffset(playingPosition, 0)
            }
        }

        viewModel.updatePlaylist.collectCancelable {
            adapter.setList(viewModel.playlist.value, diff = it)
        }

        viewModel.updatePlaylistItem.collectCancelable {
            adapter.updateItem(it, viewModel.playlist.value[it])
        }

        viewModel.removePlaylistItem.collectCancelable {
            adapter.removeItem(it)
        }

        viewModel.title.collectCancelable {
            findViewById<TextView>(R.id.nugu_playlist_item_title)?.text = it
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

            (recyclerView.layoutParams as? MarginLayoutParams)?.bottomMargin =
                if (it) playlistBottomMarginOnEditMode else viewModel.playlistBottomMargin
        }

        viewModel.startDrag.collectCancelable {
            recyclerView.findViewHolderForAdapterPosition(it)?.apply(dragHelper::startDrag)
        }

        viewModel.selectedState.collectCancelable {
            btnSelectAll.isSelected = it.first
            btnSelectAll.text = context.getText(if (it.first) R.string.nugu_playlist_deselect_all else R.string.nugu_playlist_select_all)
            btnDelete.isEnabled = it.second
        }

        viewModel.completable.collectCancelable {
            btnComplete.isEnabled = it
        }
    }

    protected open fun onCancelClicked() {
        if (viewModel.completable.value) {
            NuguSimpleDialogView(
                context,
                root = this,
                title = context.getString(R.string.nugu_playlist_dialog_cancel_title),
                positiveText = context.getString(R.string.nugu_playlist_dialog_cancel_positive),
                negativeText = context.getString(R.string.nugu_playlist_dialog_negative),
                onPositive = {
                    viewModel.onBtnCancelClicked()
                },
            ).show()
        } else {
            viewModel.onBtnCancelClicked()
        }
    }

    protected open fun onDeleteClicked() {
        if (viewModel.editMode.value) {
            NuguSimpleDialogView(
                context,
                root = this,
                title = context.getString(R.string.nugu_playlist_dialog_delete_title, viewModel.playlist.value.count { it.isSelected }),
                positiveText = context.getString(R.string.nugu_playlist_dialog_delete_positive),
                negativeText = context.getString(R.string.nugu_playlist_dialog_negative),
                onPositive = {
                    viewModel.onDeleteBtnClicked()
                },
            ).show()
        }
    }

    override fun setPlaylistViewModel(vm: PlaylistViewModel) {
        viewModel = vm
    }

    override fun onParentFragmentViewCreated() {
        super.onParentFragmentViewCreated()
        initViews()
        observeViewModel()
    }

    override fun onParentFragmentViewDestroyed() {
        super.onParentFragmentViewDestroyed()
        coroutineJobs.forEach {
            it.cancel()
        }

        coroutineJobs.clear()
    }

    private fun <T> Flow<T>.collectCancelable(block: (T) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            collect {
                block.invoke(it)
            }
        }.apply(coroutineJobs::add)
    }

    protected open fun onViewClicked(v: View) {
        // do nothing. This function is only for notifying clicked view information to Custom Media Template
    }
}