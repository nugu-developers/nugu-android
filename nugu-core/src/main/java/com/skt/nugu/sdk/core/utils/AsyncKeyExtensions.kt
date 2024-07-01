package com.skt.nugu.sdk.core.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.message.AsyncKey
import com.skt.nugu.sdk.core.interfaces.message.Directive

fun Directive.getAsyncKey(): AsyncKey? = runCatching {
    if (payload.contains("\"asyncKey\"")) {
        Gson().fromJson(
            payload,
            AsyncKeyPayload::class.java
        ).asyncKey
    } else null
}.getOrNull()

data class AsyncKeyPayload(
    @SerializedName("asyncKey")
    val asyncKey: AsyncKey
)
