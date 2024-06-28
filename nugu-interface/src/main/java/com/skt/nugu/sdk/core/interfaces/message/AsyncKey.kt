package com.skt.nugu.sdk.core.interfaces.message

data class AsyncKey(
    val eventDialogRequestId: String,
    val state: State?
) {
    enum class State {
        START, ONGOING, END
    }
}
