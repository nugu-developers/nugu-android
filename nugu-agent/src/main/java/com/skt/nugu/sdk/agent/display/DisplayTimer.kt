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
                Logger.d(tag, "[start] already started (skip) - templateId: $id, timeout: $timeout")
                return false
            }

            Logger.d(tag, "[start] templateId: $id, timeout: $timeout")
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
                "[stop] templateId: $id , future: $future, canceled: $canceled"
            )
            return canceled
        }
    }

    fun reset(id: String) {
        lock.withLock {
            val param = clearRequestParamMap[id] ?: return
            if (param != null && stop(id)) {
                Logger.d(tag, "[reset] start: $id")
                start(param.id, param.timeout, param.clear)
            } else {
                Logger.d(tag, "[reset] skipped: $id")
            }
        }
    }
}