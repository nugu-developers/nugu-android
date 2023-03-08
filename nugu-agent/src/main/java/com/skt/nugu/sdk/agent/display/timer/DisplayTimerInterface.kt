package com.skt.nugu.sdk.agent.display.timer

interface DisplayTimerInterface {
    interface Factory {
        fun newTimer(): DisplayTimerInterface
    }

    fun start(id: String, timeout: Long, clear:() -> Unit): Boolean
    fun stop(id: String): Boolean
    fun reset(id: String)
}