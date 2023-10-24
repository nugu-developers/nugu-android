package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.skt.nugu.sdk.agent.util.deepMerge
import com.skt.nugu.sdk.core.utils.Logger

private const val TAG = "PlaylistMerger"
private const val KEY_LIST = "list"
private const val KEY_LIST_REPLACE_TYPE= "replaceType"
private const val REPLACE_TYPE_PARTIAL = "partial"
private const val REPLACE_TYPE_ALL = "all"

fun JsonObject.mergePlaylist(changes: JsonObject) {
    val originList = remove(KEY_LIST) as? JsonObject
    val updateList = changes.remove(KEY_LIST) as? JsonObject

    Logger.d(TAG, "[mergePlaylist] originList: $originList")
    Logger.d(TAG, "[mergePlaylist] updateList: $updateList")

    deepMerge(changes)

    if (originList != null && updateList != null) {
        add(KEY_LIST, getMergePlaylistList(originList, updateList))
    } else if (originList != null) {
        add(KEY_LIST, originList)
    } else if (updateList != null) {
        add(KEY_LIST, updateList)
    }
}

fun getMergePlaylistList(originList: JsonObject, updateList: JsonObject): JsonObject {
    val replaceType = (updateList.get(KEY_LIST_REPLACE_TYPE) as? JsonPrimitive)?.asString ?: REPLACE_TYPE_ALL

    return when(replaceType.lowercase()) {
        REPLACE_TYPE_ALL -> {
            updateList
        }
        REPLACE_TYPE_PARTIAL -> {
            val items = updateList.getAsJsonArray("items")
            val originItemsJsonArray = originList.getAsJsonArray("items")
            val originItems = originItemsJsonArray.map { it.asJsonObject }
            val originItemsTokenAndIndexMap = originItems.mapIndexed { index, jsonObject ->
                jsonObject.get("token").asString to index
            }.toMap()

            items.map {
                it.asJsonObject
            }.forEach { item->
                val itemToken = item.getAsJsonPrimitive("token").asString
                val index = originItemsTokenAndIndexMap[itemToken]
                if(index != -1 && index != null) {
                    originItems[index].deepMerge(item)
                } else {
                    originItemsJsonArray.add(item)
                }
            }

            originList
        }
        else -> {
            // unknown replace type do not merge anything.
            originList
        }
    }
}
