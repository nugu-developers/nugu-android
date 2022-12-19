package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject

data class Playlist(
    val playServiceId: String,
    val token: String,
    val raw: JsonObject
)
