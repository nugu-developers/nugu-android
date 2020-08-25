/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface.Requester
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SeamlessFocusManager(private val focusManager: FocusManagerInterface, private val holderChannelName: String)
    : SeamlessFocusManagerInterface
    , ChannelObserver {
    companion object {
        private const val TAG = "SeamlessFocusManager"
        private const val HOLDER_INTERFACE_NAME = "FocusHolder"
    }

    private val lock = ReentrantLock()
    private val requesterSet = HashSet<Requester>()
    private var focus = FocusState.NONE

    override fun prepare(requester: Requester) {
        lock.withLock {
            val changed = requesterSet.add(requester)
            Logger.d(TAG, "[prepare] changed: $changed, requester: $requester")
        }
    }

    override fun acquire(requester: Requester): Boolean {
        lock.withLock {
            val removed = requesterSet.remove(requester)
            val result = focusManager.acquireChannel(requester.channelName, requester.channelObserver, requester.interfaceName)
            Logger.d(TAG, "[acquire] result: $result, requester: $requester, removed: $removed")
            return result
        }
    }

    override fun release(requester: Requester, focusState: FocusState) {
        lock.withLock {
            val removed = requesterSet.remove(requester)
            if(requesterSet.isNotEmpty() && focusState == FocusState.FOREGROUND) {
                Logger.d(TAG, "[release] acquire group channel before release requester")
                focusManager.acquireChannel(holderChannelName, this, HOLDER_INTERFACE_NAME)
            }
            focusManager.releaseChannel(requester.channelName, requester.channelObserver)
            Logger.d(TAG, "[release] requester: $requester, focusState: $focusState, removed: $removed")
        }
    }

    override fun onFocusChanged(newFocus: FocusState) {
        lock.withLock {
            focus = newFocus

            Logger.d(TAG, "[onFocusChanged] newFocus: $newFocus")
            if(newFocus == FocusState.BACKGROUND) {
                focusManager.releaseChannel(holderChannelName, this)
            }
        }
    }
}