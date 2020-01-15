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
package com.skt.nugu.sdk.agent.asr.audio

/**  Describe format of PCM audio
 * @param sampleRateHz the sample rate expressed in Hertz
 * @param bitsPerSample the number of bit per sample
 * @param numChannels the number of channels. mono : 1, stereo : 2
 */
data class AudioFormat(
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val numChannels: Int
) {
    /**
     * Utility function to convert time(ms) to size
     * @return the number of byte per millisecond
     */
    // fun getBytesPerMillis(): Int = ((sampleRateHz / 1000) * (bitsPerSample / 8) * numChannels)
    fun getBytesPerMillis(): Int = (sampleRateHz * bitsPerSample * numChannels) / 1000 / 8

    fun getBytesPerSample(): Int = bitsPerSample / 8
}