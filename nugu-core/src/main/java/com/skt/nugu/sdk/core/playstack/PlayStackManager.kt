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
import com.skt.nugu.sdk.core.interfaces.context.PlayStackManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

class PlayStackManager(tagPrefix: String) : PlayStackManagerInterface, PlayStackProvider {
    private val TAG = "${tagPrefix}PlayStackManager"

    private val providers = CopyOnWriteArraySet<PlayStackManagerInterface.PlayContextProvider>()

    override fun addPlayContextProvider(provider: PlayStackManagerInterface.PlayContextProvider) {
        providers.add(provider)
    }

    override fun removePlayContextProvider(provider: PlayStackManagerInterface.PlayContextProvider) {
        providers.remove(provider)
    }

    override fun getPlayStack(): List<PlayStackProvider.PlayStackContext> {
        val playStack = TreeSet<PlayStackManagerInterface.PlayContext>()
        var oldestTimestampOfPlayContext: Long = Long.MAX_VALUE

        providers.forEach {
            it.getPlayContext()?.let { playContext ->
                playStack.add(playContext)
                if(playContext.timestamp < oldestTimestampOfPlayContext) {
                    oldestTimestampOfPlayContext = playContext.timestamp
                }
            }
        }
        Logger.d(TAG, "[getPlayStack] provided : $playStack, oldestTimestampOfPlayContext: $oldestTimestampOfPlayContext")

        val shouldBeExcluded:List<PlayStackManagerInterface.PlayContext> = playStack.filter {
            it.timestamp > oldestTimestampOfPlayContext && !it.persistent
        }
        Logger.d(TAG, "[getPlayStack] shouldBeExcluded : $shouldBeExcluded")

        playStack.removeAll(shouldBeExcluded)
        val playStackContext = ArrayList<PlayStackProvider.PlayStackContext>()

        playStack.forEach {
            playStackContext.add(PlayStackProvider.PlayStackContext(it.playServiceId, it.timestamp))
        }

        Logger.d(TAG, "[getPlayStack] $playStackContext")

        return playStackContext
    }
}