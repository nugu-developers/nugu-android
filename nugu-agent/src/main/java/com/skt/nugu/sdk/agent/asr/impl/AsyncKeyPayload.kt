package com.skt.nugu.sdk.agent.asr.impl

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.message.AsyncKey

internal data class AsyncKeyPayload(
    @SerializedName("asyncKey")
    val asyncKey: AsyncKey
)

