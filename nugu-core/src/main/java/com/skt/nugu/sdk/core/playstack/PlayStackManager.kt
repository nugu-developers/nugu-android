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
import com.skt.nugu.sdk.core.interfaces.utils.Logger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

class PlayStackManager(tagPrefix: String) : PlayStackManagerInterface, PlayStackProvider {
    private val TAG = "${tagPrefix}PlayStackManager"

    private val lock = ReentrantLock()
    private val playStack = TreeSet<PlayStackManagerInterface.PlayContext>()
    private val delayedRemovePlayContext = HashMap<PlayStackManagerInterface.PlayContext, Long>()

    override fun add(playContext: PlayStackManagerInterface.PlayContext) {
        lock.withLock {
            earlyRemoveDelayedPlayContextLocked()
            if(playStack.add(playContext)) {
                Logger.d(TAG, "[add] added: $playContext")
            } else {
                Logger.d(TAG, "[add] already exist: $playContext")
            }
        }
    }

    override fun remove(playContext: PlayStackManagerInterface.PlayContext) {
        lock.withLock {
            if(playStack.remove(playContext)) {
                Logger.d(TAG, "[remove] removed: $playContext")
            } else {
                Logger.d(TAG, "[remove] no item: $playContext")
            }
        }
    }

    override fun removeDelayed(playContext: PlayStackManagerInterface.PlayContext, delay: Long) {
        lock.withLock {
            Logger.d(TAG, "[removeDelayed] $playContext / $delay")
            if(existLowerPriorityPlayContextThanLocked(playStack, playContext.priority)) {
                Logger.d(TAG, "[removeDelayed] ignore delay due to exist lower priority play context.")
                playStack.remove(playContext)
            } else {
                delayedRemovePlayContext[playContext] = System.currentTimeMillis() + delay
            }
        }
    }

    override fun getPlayStack(): List<PlayStackProvider.PlayStackContext> {
        lock.withLock {
            trimTimeoutPlayStackLocked()

            val playStackContext = ArrayList<PlayStackProvider.PlayStackContext>()
            playStack.forEach {
                playStackContext.add(PlayStackProvider.PlayStackContext(it.playServiceId, it.priority))
            }

            Logger.d(TAG, "[getPlayStack] $playStackContext")

            return playStackContext
        }
    }

    private fun earlyRemoveDelayedPlayContextLocked() {
        Logger.d(TAG, "[earlyPopDelayedPlayContextLocked]")
        delayedRemovePlayContext.forEach {
            playStack.remove(it.key)
            Logger.d(TAG, "[earlyPopDelayedPlayContextLocked] removed: $it")
        }
        delayedRemovePlayContext.clear()
    }

    private fun trimTimeoutPlayStackLocked() {
        Logger.d(TAG, "[trimTimeoutPlayStackLocked]")
        val currentTimestamp = System.currentTimeMillis()

        delayedRemovePlayContext.filter {
            it.value < currentTimestamp
        }.forEach {
            delayedRemovePlayContext.remove(it.key)
            playStack.remove(it.key)
        }
    }

    private fun existLowerPriorityPlayContextThanLocked(dest: Set<PlayStackManagerInterface.PlayContext>, priority: Int): Boolean {
        Logger.d(TAG, "[existLowerPriorityPlayContextThan] $priority")

        dest.forEach {
            if(it.priority > priority) {
                return true
            }
        }

        return false
    }
}