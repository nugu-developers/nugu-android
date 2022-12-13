package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.utils.Logger

private const val TAG = "PlaylistHelper"

fun JsonObject.getPlaylistToken(): String? = try {
    getAsJsonPrimitive("token").asString
} catch (e: Exception) {
    Logger.w(TAG, "[getPlaylistToken] failed", e)
    null
}