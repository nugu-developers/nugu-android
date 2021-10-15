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
package com.skt.nugu.sdk.agent.display

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DisplayTimer(private val tag: String) {
    private val lock = ReentrantLock()
    private val clearTimeoutScheduler = ScheduledThreadPoolExecutor(1)
    private val clearTimeoutFutureMap: MutableMap<String, ScheduledFuture<*>> = HashMap()
    private val clearRequestParamMap: MutableMap<String, StartParam> = HashMap()

    private data class StartParam(
        val id: String,
        val timeout: Long,
        val clear:() -> Unit
    )

    fun start(id: String, timeout: Long, clear:() -> Unit): Boolean {
        lock.withLock {
            val exist = clearRequestParamMap[id] != null
            if(exist) {
                Logger.d(tag, "[DisplayTimer.start] already started (skip) - templateId: $id, timeout: $timeout")
                return false
            }

            Logger.d(tag, "[DisplayTimer.start] templateId: $id, timeout: $timeout")
            clearRequestParamMap[id] = StartParam(id, timeout, clear)
            clearTimeoutFutureMap[id] =
                clearTimeoutScheduler.schedule({
                    lock.withLock {
                        clearRequestParamMap.remove(id)
                        clearTimeoutFutureMap.remove(id)
                    }
                    clear.invoke()
                }, timeout, TimeUnit.MILLISECONDS)

            return true
        }
    }

    fun stop(id: String): Boolean {
        lock.withLock {
            clearRequestParamMap.remove(id)
            val future = clearTimeoutFutureMap.remove(id)
            var canceled = false
            if (future != null) {
                canceled = future.cancel(true)
            }

            Logger.d(
                tag,
                "[DisplayTimer.stop] templateId: $id , future: $future, canceled: $canceled"
            )
            return canceled
        }
    }

    fun reset(id: String) {
        lock.withLock {
            val param = clearRequestParamMap[id] ?: return
            if (stop(id)) {
                Logger.d(tag, "[DisplayTimer.reset] start: $id")
                start(param.id, param.timeout, param.clear)
            } else {
                Logger.d(tag, "[DisplayTimer.reset] skipped: $id")
            }
        }
    }
}