package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.model.ButtonObject
import com.skt.nugu.sdk.platform.android.ux.template.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.skt.nugu.sdk.agent.audioplayer.playlist.Playlist as PlaylistFromAgent


class PlaylistViewModel : ViewModel() {
    companion object {
        private const val TAG = "PlaylistViewModel"
    }

    data class ListItem(val item: Playlist.PlayListItem, var isSelected: Boolean, var isPlaying: Boolean)

    private var eventListener: PlaylistEventListener? = null

    private val _playlist = MutableStateFlow<ArrayList<ListItem>>(arrayListOf())
    val playlist = _playlist.asStateFlow()

    private val _updatePlaylist = MutableSharedFlow<Unit>()
    val updatePlaylist = _updatePlaylist.asSharedFlow()

    private val _updatePlaylistItem = MutableSharedFlow<Int>()
    val updatePlaylistItem = _updatePlaylistItem.asSharedFlow()

    private val _movePlaylistItem = MutableSharedFlow<Pair<Int, Int>>()  //from, to
    val movePlaylistItem = _movePlaylistItem.asSharedFlow()

    private val _removePlaylistItem = MutableSharedFlow<List<Int>>()
    val removePlaylistItem = _removePlaylistItem.asSharedFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title = _title.asStateFlow()

    private val _editButton = MutableStateFlow<Playlist.TextFormat?>(null)
    val editButton = _editButton.asStateFlow()

    private val _button = MutableStateFlow<ButtonObject?>(null)
    val button = _button.asStateFlow()

    private val _editMode = MutableStateFlow<Boolean>(false)
    val editMode = _editMode.asStateFlow()

    private val _startDrag = MutableSharedFlow<Int>()
    val startDrag = _startDrag.asSharedFlow()

    // first : isAllItemSelected
    // second : isAnyItemSelected
    private val _selectedState = MutableStateFlow<Pair<Boolean, Boolean>>(false to false)
    val selectedState = _selectedState.asStateFlow()

    private val _completable = MutableStateFlow<Boolean>(false)
    val completable = _completable.asStateFlow()

    private var previousList: List<ListItem>? = null

    private val gson = Gson()
    private fun <T> fromJsonOrNull(json: String, classOfT: Class<T>): T? {
        return runCatching {
            gson.fromJson(json, classOfT)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    private fun <T> fromJsonOrNull(json: JsonElement, classOfT: Class<T>): T? {
        return runCatching {
            gson.fromJson(json, classOfT)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    fun setEventListener(listener: PlaylistEventListener?) {
        eventListener = listener
    }

    fun setPlaylist(list: PlaylistFromAgent) {
        Logger.d(TAG, "setPlaylist() ${list.raw}")
        _playlist.value.clear()

        fromJsonOrNull(list.raw.toString(), Playlist::class.java)?.let { playlist ->
            Logger.d(TAG, "setPlaylist() playlist parsing success")

            playlist.list.items.run {
                setPlaylistItems(this, playlist.currentToken)
            }

            CoroutineScope(Dispatchers.Main).launch {
                _updatePlaylist.emit(Unit)
                _title.emit(playlist.title?.text?.text)
                _editButton.emit(playlist.edit)
                _button.emit(playlist.button)
            }
        }
    }

    private fun setPlaylistItems(list: List<Playlist.PlayListItem>, currentToken: String?) {
        Logger.d(TAG, "setPlaylistItems(")
        _playlist.value.addAll(list.map { ListItem(it, false, it.token == currentToken) })
    }

    fun updatePlaylist(changes: JsonObject, updated: PlaylistFromAgent) {
        Logger.d(TAG, "updatePlaylist() changed:\n$changes, \n updated:\n$updated")

        fromJsonOrNull(changes, Playlist::class.java)?.let { changedPlaylist ->

            Logger.d(TAG, "updatePlaylist() parsing success $changedPlaylist")

            CoroutineScope(Dispatchers.Main).launch {
                // title
                changedPlaylist.title?.text?.text?.apply { _title.emit(this) }

                // edit button
                changedPlaylist.edit?.apply { _editButton.emit(this) }

                // button
                changedPlaylist.button?.apply { _button.emit(this) }
            }

            // list items
            var isListChanged = false
            changedPlaylist.list.items.let { newItems ->
                if (newItems.isNotEmpty()) isListChanged = true
                val currentPlayingToken = changedPlaylist.currentToken ?: _playlist.value.find { it.isPlaying }?.item?.token
                setPlaylistItems(newItems, currentPlayingToken)
            }

            // current playing item
            if (!isListChanged && changedPlaylist.currentToken?.isNotBlank() == true) {
                val prevPlayingIndex = _playlist.value.indexOfFirst { it.isPlaying }
                val currPlayingIndex = _playlist.value.indexOfFirst { it.item.token == changedPlaylist.currentToken }

                _playlist.value.getOrNull(prevPlayingIndex)?.isPlaying = false
                _playlist.value.getOrNull(currPlayingIndex)?.isPlaying = true

                CoroutineScope(Dispatchers.Main).launch {
                    if (prevPlayingIndex != -1) _updatePlaylistItem.emit(prevPlayingIndex)
                    if (currPlayingIndex != -1) _updatePlaylistItem.emit(currPlayingIndex)
                }
            }
        }
    }

    fun clearPlaylist() {
        // do nothing here.
        // TemplateRenderer hide playlist.
    }

    fun moveItem(from: Int, to: Int) {
        if (editMode.value) {
            runCatching {
                val removed = _playlist.value.removeAt(from)
                _playlist.value.add(to, removed)
            }.onSuccess {
                CoroutineScope(Dispatchers.Main).launch {
                    _movePlaylistItem.emit(from to to)
                }

                onListChanged()
            }
        }
    }

    fun onEditBtnClicked() {
        switchEditMode()
    }

    fun onButtonClicked() {
        Logger.d(TAG, "onButtonClicked()  token: ${button.value?.token}")
        button.value?.run {
            eventListener?.setElementSelected(token, postback.toString(), null)
        }
    }

    fun onItemClicked(position: Int) {
        if (editMode.value) {
            _playlist.value[position].isSelected = !_playlist.value[position].isSelected
            CoroutineScope(Dispatchers.Main).launch {
                _updatePlaylistItem.emit(position)
            }

            onSelectedStateChanged()
        }
    }

    fun onItemFavoriteClicked(position: Int) {
        Logger.d(TAG, "onItemFavoriteClicked()  token: ${_playlist.value.getOrNull(position)?.item?.favorite?.token}")
        _playlist.value.getOrNull(position)?.item?.run {
            eventListener?.setElementSelected(token, postback.toString(), null)
        }
    }

    fun onDragHandleTouchDown(position: Int) {
        if (editMode.value) {
            CoroutineScope(Dispatchers.Main).launch {
                _startDrag.emit(position)
            }
        }
    }

    fun onSelectAllClicked() {
        if (_selectedState.value.first) {
            //deselect all
            clearAllSelectedState()
        } else {
            //select all
            setAllSelectedState()
        }

        CoroutineScope(Dispatchers.Main).launch {
            _updatePlaylist.emit(Unit)
        }
    }

    fun onDeleteBtnClicked() {
        if (editMode.value) {
            val deleteIndices = arrayListOf<Int>()

            _playlist.value.forEachIndexed { index, item ->
                if (item.isSelected) {
                    deleteIndices.add(index)
                }
            }

            if (deleteIndices.size > 0) {
                CoroutineScope(Dispatchers.Main).launch {
                    _removePlaylistItem.emit(deleteIndices)
                }

                _playlist.value.removeAll { it.isSelected }

                onListChanged()
            }
        }
    }

    fun onBtnCancelClicked() {
        if (editMode.value) {
            if (completable.value) {
                _playlist.value.clear()
                _playlist.value.addAll(previousList ?: return)

                CoroutineScope(Dispatchers.Main).launch {
                    _updatePlaylist.emit(Unit)
                }
            }

            setCompletable(false)
            switchEditMode()
        }
    }

    fun onCompleteBtnClicked() {
        Logger.d(TAG, "onCompleteBtnClicked()  stateOk : ${editMode.value && completable.value}")

        if (editMode.value && completable.value) {
            val tokens = _playlist.value.map { it.item.token }
            val deleteTokens = previousList?.map { it.item.token }?.filterNot { tokens.contains(it) }
            eventListener?.modifyPlaylist(deleteTokens ?: emptyList(), tokens)

            setCompletable(false)
            clearAllSelectedState()
            switchEditMode()
        }
    }

    private fun switchEditMode() {
        CoroutineScope(Dispatchers.Main).launch {
            _editMode.emit(!_editMode.value)

            if (editMode.value) {
                previousList = arrayListOf<ListItem>().apply {
                    addAll(_playlist.value)
                }
            } else {
                clearAllSelectedState()
            }
        }
    }

    private fun clearAllSelectedState() {
        var changed = false
        _playlist.value.forEach {
            if (it.isSelected) {
                it.isSelected = false
                changed = true
            }
        }

        if (changed) onSelectedStateChanged()
    }

    private fun setAllSelectedState() {
        var changed = false
        _playlist.value.forEach {
            if (!it.isSelected) {
                it.isSelected = true
                changed = true
            }
        }

        if (changed) onSelectedStateChanged()
    }

    private fun onSelectedStateChanged() {
        val selectedCount = playlist.value.count { it.isSelected }
        CoroutineScope(Dispatchers.Main).launch {
            _selectedState.emit(Pair(selectedCount == playlist.value.count(), selectedCount > 0))
        }
    }

    private fun onListChanged() {
        previousList?.let { prevList ->
            val prevSize = prevList.size
            val currSize = playlist.value.size

            val listEqual = prevList.map { it.item } == playlist.value.map { it.item }
            val changed = (prevSize != currSize) or !listEqual

            setCompletable(changed)
        }
    }

    private fun setCompletable(enable: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            _completable.emit(enable)
        }
    }
}
