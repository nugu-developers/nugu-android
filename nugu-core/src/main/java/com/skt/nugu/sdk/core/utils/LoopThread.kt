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
package com.skt.nugu.sdk.core.utils

abstract class LoopThread(
) : Thread() {
    private val wakeLoop = Object()
    private var requestStop = false
    private var isFinished = false
    private var skipWaitIfNotRunningYet = false

    override fun run() {
        super.run()
        try {
            Logger.d("LoopThread", "[run] start :$this")
            while (!requestStop) {
                synchronized(wakeLoop) {
                    if(skipWaitIfNotRunningYet) {
                       skipWaitIfNotRunningYet = false
                    } else {
                        wakeLoop.wait()
                    }
                }
                if (requestStop) {
                    return
                }

                onLoop()
            }
        } finally {
            isFinished = true
            Logger.d("LoopThread", "[run] finish :$this")
        }
    }

    protected abstract fun onLoop()

    fun wakeOne() {
        synchronized(wakeLoop) {
            wakeLoop.notify()
        }
    }

    fun wakeAll(skipWaitIfNotRunningYet: Boolean = false) {
        synchronized(wakeLoop) {
            this.skipWaitIfNotRunningYet = skipWaitIfNotRunningYet
            wakeLoop.notifyAll()
        }
    }

    fun requestStop() {
        if(isFinished) {
            return
        }
        Logger.d("LoopThread", "[requestStop] $this")
        requestStop = true
        wakeAll()
    }
}