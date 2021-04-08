/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.beep

import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BeepPlaybackController {
    companion object {
        private const val TAG = "BeepPlaybackController"
    }

    interface Source {
        val priority: Int

        fun play()
    }

    private val lock = ReentrantLock()

    // ascending order(low value has higher priority)
    private val queue = TreeMap<Int, MutableList<Source>>()

    fun addSource(source: Source) {
        lock.withLock {
            val isQueueEmpty = isQueueEmpty()
            Logger.d(TAG, "[addSource] source: $source, isQueueEmpty: $isQueueEmpty")

            var priorityQueue = queue[source.priority]
            if (priorityQueue == null) {
                priorityQueue = ArrayList()
                queue[source.priority] = priorityQueue
            }

            priorityQueue.add(source)

            if(isQueueEmpty) {
                source.play()
            }
        }
    }

    fun removeSource(source: Source) {
        lock.withLock {
            if(queue[source.priority]?.remove(source) == true) {
                Logger.d(TAG, "[removeSource] removed source: $source")
                findNextSource()?.play()
            } else {
                Logger.d(TAG, "[removeSource] already removed source: $source")
            }
        }
    }

    private fun findNextSource(): Source? {

        queue.forEach {
            val source = it.value.getOrNull(0)
            if(source != null) {
                return source
            }
        }

        return null
    }

    private fun isQueueEmpty(): Boolean {
        var count = 0

        queue.forEach {
            count += it.value.size
        }

        return count == 0
    }
}