package com.skt.nugu.sdk.client.port.transport.http2.multipart

import okhttp3.Call
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MultipartStreamingCalls<T>(val pendingRequest : PendingRequestListener<T>) {
    private var executed = false

    interface PendingRequestListener<T>{
        fun execute(request: T)
    }

    private val pendingRequests = ConcurrentLinkedQueue<T>()
    private val executor = ScheduledThreadPoolExecutor(1)
    private var future: ScheduledFuture<*>? = null
    private var callReference : WeakReference<Call>? = null

    fun isExecuted() = executed
    fun start() {
        future?.cancel(false)
        executed = true
        timeout()
    }

    fun stop() {
        future?.cancel(false)
        executed = false
        drain()
    }
    fun executePendingRequest(request : T) {
        pendingRequests.add(request)
    }

    private fun drain() {
        while(!pendingRequests.isEmpty()) {
            pendingRequest.execute(pendingRequests.poll())
        }
    }

    private fun timeout() {
        future = executor.schedule({
            if(executed) {
                stop()
            }
        }, 10, TimeUnit.SECONDS)
    }

    fun set(call: Call) : Call{
        callReference = WeakReference<Call>(call)
        return call
    }

    fun cancel() {
        callReference = null
    }

    fun get() : Call? {
        return callReference?.get()
    }
}
