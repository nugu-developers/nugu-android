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
package com.skt.nugu.sdk.core.interfaces.directive

import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Defines an BlockingPolicy.
 *
 * Prefer to use [sharedInstanceFactory] to get instance instead of creation.
 *
 * @param blockedBy set mediums which blocked
 * @param blocking set mediums which blocking
 */
data class BlockingPolicy(
    val blockedBy: EnumSet<Medium>? = MEDIUM_ANY_ONLY,
    val blocking: EnumSet<Medium>? = null
) {
    enum class Medium {
        AUDIO,
        VISUAL,
        ANY
    }

    companion object {
        val MEDIUM_ALL: EnumSet<Medium> = EnumSet.allOf(Medium::class.java)
        val MEDIUM_ANY_ONLY: EnumSet<Medium> = EnumSet.of(Medium.ANY)
        val MEDIUM_AUDIO: EnumSet<Medium> = EnumSet.of(Medium.ANY, Medium.AUDIO)
        val MEDIUM_AUDIO_ONLY: EnumSet<Medium> = EnumSet.of(Medium.AUDIO)

        val sharedInstanceFactory = SharedInstanceFactory()
    }

    class SharedInstanceFactory {
        private val instances = CopyOnWriteArraySet<BlockingPolicy>()

        fun get(
            blockedBy: EnumSet<Medium>? = MEDIUM_ANY_ONLY,
            blocking: EnumSet<Medium>? = null
        ): BlockingPolicy {
            return instances.find { it.blockedBy == blockedBy && it.blocking == blocking }
                ?: BlockingPolicy(blockedBy, blocking).apply {
                    instances.add(this)
                }
        }
    }
}