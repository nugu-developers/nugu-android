package com.skt.nugu.sdk.core.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.message.AsyncKey
fun String.getAsyncKey(): AsyncKey? = runCatching {
    if (contains("\"asyncKey\"")) {
        Gson().fromJson(
            this,
            AsyncKeyPayload::class.java
        ).asyncKey
    } else null
}.getOrNull()

data class AsyncKeyPayload(
    @SerializedName("asyncKey")
    val asyncKey: AsyncKey
)
