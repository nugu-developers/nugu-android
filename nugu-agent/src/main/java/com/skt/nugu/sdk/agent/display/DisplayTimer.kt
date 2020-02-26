package com.skt.nugu.sdk.agent.display

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import com.skt.nugu.sdk.core.utils.Logger

class DisplayTimer(private val tag: String) {
    private val clearTimeoutScheduler = ScheduledThreadPoolExecutor(1)
    private val clearTimeoutFutureMap: MutableMap<String, ScheduledFuture<*>?> = HashMap()

    fun start(id: String, timeout: Long, clear:() -> Unit) {
        Logger.d(tag, "[start] templateId: $id, timeout: $timeout")
        clearTimeoutFutureMap[id] =
            clearTimeoutScheduler.schedule({
                clear.invoke()
            }, timeout, TimeUnit.MILLISECONDS)
    }

    fun stop(id: String) {
        val future = clearTimeoutFutureMap[id]
        var canceled = false
        if (future != null) {
            canceled = future.cancel(true)
            clearTimeoutFutureMap[id] = null
        }

        Logger.d(
            tag,
            "[stop] templateId: $id , future: $future, canceled: $canceled"
        )
    }
}