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
package com.skt.nugu.sdk.agent.util

abstract class TimeoutCondition<T>(
    private val timeout: Long,
    condition: () -> Boolean
) {
    private var result: T? = null

    init {
        Thread {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeout) {
                if (condition()) {
                    result = onCondition()
                    return@Thread
                }

                Thread.sleep(10)
            }

            result = onTimeout()
        }.start()
    }

    abstract fun onCondition(): T
    abstract fun onTimeout(): T
    fun get(): T {
        while (result == null) {
            Thread.sleep(5)
        }

        return result!!
    }
}