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
        val playStack = arrayListOf<PlayStackManagerInterface.PlayContext>().apply {
            providers.forEach { add(it.getPlayContext() ?: return@forEach) }
        }

        val oldestTimestamp = playStack.filter { it.affectPersistent }
            .minByOrNull { it.timestamp }?.timestamp ?: Long.MAX_VALUE

        Logger.d(TAG, "[getPlayStack] provided : $playStack, oldestTimestamp: $oldestTimestamp")

        playStack.removeAll { it.timestamp > oldestTimestamp && !it.persistent }

        playStack.sortByDescending {
            it.timestamp
        }

        return ArrayList<PlayStackProvider.PlayStackContext>()
            .apply {
                playStack.forEach {
                    add(PlayStackProvider.PlayStackContext(it.playServiceId, it.timestamp, it.isBackground))
                }
            }.also { Logger.d(TAG, "[getPlayStack] $it") }
    }
}