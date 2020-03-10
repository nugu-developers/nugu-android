/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent.asr

/**
 * The wakeup info
 * @param word the wakeup word
 * @param boundary the boundary position for which [word]
 * @param power the power, may be null if not available.
 */
data class WakeupInfo(
    val word: String,
    val boundary: Boundary,
    val power: Power? = null
) {
    /**
     * @param startPosition the start position of wakeupword.
     * @param endPosition the end position of wakeupword.
     * @param detectPosition the detect position of wakeupword.
     */
    data class Boundary(
        val startPosition: Long,
        val endPosition: Long,
        val detectPosition: Long
    )

    /**
     * @param speech the power of speech
     * @param noise the power of noise
     */
    data class Power(
        val speech: Float,
        val noise: Float
    )
}