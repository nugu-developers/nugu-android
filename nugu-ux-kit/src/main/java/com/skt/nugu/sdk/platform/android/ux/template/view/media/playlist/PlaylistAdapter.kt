package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.updateImage
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton

class PlaylistAdapter(private val viewModel: PlaylistViewModel) : RecyclerView.Adapter<ViewHolder>() {
    companion object {
        private const val TAG = "PlaylistAdapter"
    }

    private var playlist: ArrayList<PlaylistViewModel.ListItem> = arrayListOf()
    private var editMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_holder_play_item, parent, false), viewModel)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindViewHolder(playlist.getOrNull(position), editMode)
    }

    override fun getItemCount(): Int {
        return playlist.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setList(list: List<PlaylistViewModel.ListItem>) {
        playlist.clear()
        playlist.addAll(list)
        notifyDataSetChanged()
    }

    fun moveData(from: Int, to: Int) {
        runCatching {
            playlist.removeAt(from)
        }.onFailure {
            Logger.e(TAG, "moveData($from, $to) fail", it)
        }.getOrNull()?.let { removed ->
            playlist.add(to, removed)
            notifyItemMoved(from, to)
        }
    }

    fun updateItem(position: Int, item: PlaylistViewModel.ListItem) {
        if (playlist.size > position) {
            playlist[position] = item
            notifyItemChanged(position)
        } else {
            Logger.e(TAG, "updateItem($position, ${item.item.token} fail. current adapter data size is ${playlist.size}")
        }
    }

    fun removeItem(positions: List<Int>) {
        runCatching {
            positions.reversed().forEach {
                playlist.removeAt(it)
                notifyItemRemoved(it)
            }
        }.onFailure {
            Logger.e(TAG, "removeItem($positions) fail", it)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(editMode: Boolean) {
        this.editMode = editMode
        notifyDataSetChanged()
    }
}

@SuppressLint("ClickableViewAccessibility")
class ViewHolder(view: View, viewModel: PlaylistViewModel) : RecyclerView.ViewHolder(view) {
    private val thumbTransformCorner by lazy { RoundedCorners(NuguButton.dpToPx(8f, view.context)) }

    private val titleColor = view.context.resources.getColor(R.color.nugu_playlist_item_title_color)
    private val titleColorPlaying = view.context.resources.getColor(R.color.nugu_playlist_item_title_color_playing)

    init {
        view.findViewById<View>(R.id.v_bg).setOnClickListener {
            viewModel.onItemClicked(adapterPosition)
        }

        view.findViewById<View>(R.id.iv_drag_handle).run {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    viewModel.onDragHandleTouchDown(adapterPosition)
                }
                false
            }
        }

        itemView.findViewById<View>(R.id.iv_favorite).setOnClickListener {
            viewModel.onItemFavoriteClicked(adapterPosition)
        }
    }

    fun bindViewHolder(listItem: PlaylistViewModel.ListItem?, editMode: Boolean) {
        val item = listItem?.item

        item ?: return

        itemView.findViewById<View>(R.id.v_bg).isSelected = listItem.isSelected

        itemView.findViewById<ImageView>(R.id.iv_thumbnail).run {
            visibility = if (item.imageUrl != null) View.VISIBLE else View.GONE
            if (item.imageUrl != null) {
                updateImage(item.imageUrl, placeHolder = R.drawable.nugu_logo_placeholder_60, transformation = thumbTransformCorner)
            }
        }

        itemView.findViewById<TextView>(R.id.tv_title).run {
            text = item.text.text
            maxLines = item.text.maxLine ?: 1
            setTextColor(if (listItem.isPlaying) titleColorPlaying else titleColor)
        }

        itemView.findViewById<ImageView>(R.id.iv_badge).run {
            visibility = if (item.badgeUrl != null) View.VISIBLE else View.GONE
            updateImage(item.badgeUrl, null)
        }

        itemView.findViewById<TextView>(R.id.tv_subtitle).run {
            visibility = if (item.subText != null) View.VISIBLE else View.GONE
            text = item.subText?.text
            maxLines = item.subText?.maxLine ?: 1
        }

        itemView.findViewById<View>(R.id.iv_favorite).run {
            visibility = if (item.favorite != null && !editMode) View.VISIBLE else View.GONE
            isSelected = item.favorite?.status == true
        }

        itemView.findViewById<View>(R.id.iv_drag_handle).run {
            visibility = if (editMode) View.VISIBLE else View.GONE
        }
    }
}
