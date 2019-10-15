package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.utils.TimeoutCondition

class TestClient : ChannelObserver {
    private var focusState: FocusState = FocusState.NONE
    private var focusChangeOccured: Boolean = false

    override fun onFocusChanged(newFocus: FocusState) {
        focusState = newFocus
        focusChangeOccured = true
    }

    data class Result(
        val focusState: FocusState,
        val focusChanged: Boolean
    )

    fun waitFocusChangedAndReset(): Result {
        val v = object : TimeoutCondition<Result>(1000L, { focusChangeOccured }) {
            override fun onCondition(): Result {
                focusChangeOccured = false
                return Result(focusState, true)
            }

            override fun onTimeout(): Result {
                return Result(focusState, false)
            }
        }
        return v.get()
    }
}