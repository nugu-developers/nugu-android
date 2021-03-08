/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.utils.Logger

data class Channel(
    val name: String,
    val priority: Priority
) {
    companion object {
        private const val TAG = "Channel"
    }

    data class Priority(
        val acquire: Int,
        val release: Int
    )

    data class State(
        var focusState: FocusState = FocusState.NONE,
        var interfaceName: String = ""
    )

    val state = State()
    private var observer: ChannelObserver? = null

    fun setFocus(focus: FocusState): Boolean {
        Logger.d(TAG, "[setFocus] focus: $focus, $state")
        if (focus == state.focusState) {
            return false
        }

        state.focusState = focus
        observer?.onFocusChanged(state.focusState)

        if (FocusState.NONE == state.focusState) {
            observer = null
        }

        return true
    }

    fun setObserver(observer: ChannelObserver?) {
        this.observer = observer
    }

    fun hasObserver() = observer != null

    fun setInterfaceName(interfaceName: String) {
        state.interfaceName = interfaceName
    }

    fun getInterfaceName() = state.interfaceName

    fun doesObserverOwnChannel(observer: ChannelObserver): Boolean = this.observer == observer

//    override fun compare(o1: Channel, o2: Channel) = o2.priority - o1.priority
//
//    override fun compareTo(other: Channel) = other.priority - priority
}