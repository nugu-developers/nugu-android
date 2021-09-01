/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.external.opus

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class OpusHeader(
    val version: Byte = 1, // Version, MUST The version number MUST always be '1' for this version of the encapsulation specification.
    val channelCount: Byte = 1, // Output Channel Count
    val preSkip: Short = 0, // Pre-skip
    val sampleRate: Int, // Input Sample Rate (Hz)
    val outputGain: Short = 0 // Output Gain (Q7.8 in dB), +/- 128 dB
) {
    companion object {
        private val CAPTURE_PATTERN = "OpusHead".toByteArray()
        private val HEADER_LENGTH = 19
    }
    fun getMappingFamily(): Byte = if(channelCount == 1.toByte()) 0 else 1

    fun toByteArray(): ByteArray = ByteBuffer.allocate(HEADER_LENGTH).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        put(CAPTURE_PATTERN)
        put(version)
        put(channelCount)
        putShort(preSkip)
        putInt(sampleRate)
        putShort(outputGain)
        put(getMappingFamily())
    }.array()
}