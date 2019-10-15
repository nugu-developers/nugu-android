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

/**
 * Defines an BlockingPolicy.
 *
 * @param mediums set mediums which blocked
 * @param isBlocking true: block handling directive which has same [Medium] defined [mediums], false: non-blocking
 */
class BlockingPolicy(
    val mediums: Mediums = MEDIUM_NONE,
    val isBlocking: Boolean = false
) {
    enum class Medium {
        AUDIO,
        VISUAL
    }

    data class Mediums(
        val audio: Boolean,
        val visual: Boolean
    )

    companion object {
        val MEDIUM_AUDIO =
            Mediums(audio = true, visual = false)
        val MEDIUM_VISUAL =
            Mediums(audio = false, visual = true)
        val MEDIUM_AUDIO_AND_VISUAL =
            Mediums(audio = true, visual = true)
        val MEDIUM_NONE =
            Mediums(audio = false, visual = false)
    }

    fun isValid(): Boolean = !(MEDIUM_NONE == mediums && isBlocking)
}