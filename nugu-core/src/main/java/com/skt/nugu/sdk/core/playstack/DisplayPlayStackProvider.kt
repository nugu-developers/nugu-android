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
package com.skt.nugu.sdk.core.playstack

import com.skt.nugu.sdk.core.context.PlayStackProvider
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

class DisplayPlayStackProvider (
    visualFocusManager: FocusManagerInterface
) : PlayStackProvider
    , FocusManagerInterface.OnFocusChangedListener {
    companion object {
        private const val TAG = "DisplayPlayStackProvider"
    }

    init {
        visualFocusManager.addListener(this)
    }

    private val lock = ReentrantLock()
    private val activeChannelAndPlayServiceIdMap = HashMap<FocusManagerInterface.ChannelConfiguration, String>()


    override fun getPlayStack(): List<PlayStackProvider.PlayStackContext> {
        lock.withLock {
            val playStack = ArrayList<PlayStackProvider.PlayStackContext>()
            activeChannelAndPlayServiceIdMap.forEach {
                playStack.add(PlayStackProvider.PlayStackContext(it.value, it.key.priority))
            }

            Logger.d(TAG, "[getPlayStack] $playStack")

            return playStack
        }
    }

    override fun onFocusChanged(
        channelConfiguration: FocusManagerInterface.ChannelConfiguration,
        newFocus: FocusState,
        interfaceName: String,
        playServiceId: String?
    ) {
        Logger.d(
            TAG,
            "[onFocusChanged] $channelConfiguration, $newFocus, $interfaceName, $playServiceId"
        )
        if (playServiceId.isNullOrBlank()) {
            return
        }

        lock.withLock {
            when (newFocus) {
                FocusState.FOREGROUND -> {
                    activeChannelAndPlayServiceIdMap[channelConfiguration] = playServiceId
                }
                FocusState.NONE -> {
                    activeChannelAndPlayServiceIdMap.remove(channelConfiguration)
                }
                else -> {
                    // nothing
                }
            }
        }
    }
}