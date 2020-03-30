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
 * The wakeup info for payload
 * @param word the wakeup word
 * @param boundary the boundary sample position for which [word]
 * @param power the power, may be null if not available.
 */
data class PayloadWakeup (
    val word: String?,
    val boundary: Boundary?,
    val power: WakeupInfo.Power?
) {
    /**
     * @param startSamplePosition the start sample position of wakeupword.
     * @param endSamplePosition the end sample position of wakeupword.
     * @param detectSamplePosition the detect sample position of wakeupword.
     */
    data class Boundary(
        val startSamplePosition: Long,
        val endSamplePosition: Long,
        val detectSamplePosition: Long
    )
}