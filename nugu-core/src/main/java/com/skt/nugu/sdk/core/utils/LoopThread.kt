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

abstract class LoopThread : Thread() {
    private val wakeLoop = Object()
    private var isNotified = false

    override fun run() {
        super.run()

        while (true) {
            synchronized(wakeLoop) {
                if(isNotified) {
                    isNotified = false
                } else {
                    wakeLoop.wait()
                }
            }

            onLoop()
        }
    }

    protected abstract fun onLoop()

    fun wakeOne() {
        synchronized(wakeLoop) {
            isNotified = true
            wakeLoop.notify()
        }
    }

    fun wakeAll() {
        synchronized(wakeLoop) {
            isNotified = true
            wakeLoop.notifyAll()
        }
    }
}