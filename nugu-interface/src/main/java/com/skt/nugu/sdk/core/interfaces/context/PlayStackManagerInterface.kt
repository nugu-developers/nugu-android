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
package com.skt.nugu.sdk.core.interfaces.context

interface PlayStackManagerInterface {
    data class PlayContext(
        val playServiceId: String,
        val timestamp: Long,
        val persistent: Boolean = true
    ): Comparable<PlayContext> {
        override fun compareTo(other: PlayContext): Int {
            val diff = other.timestamp - timestamp
            return when {
                diff > 0 -> 1
                diff < 0 -> -1
                else -> 0
            }
        }
    }

    interface PlayContextProvider {
        fun getPlayContext(): PlayContext?
    }

    fun addPlayContextProvider(provider: PlayContextProvider)
    fun removePlayContextProvider(provider: PlayContextProvider)
}