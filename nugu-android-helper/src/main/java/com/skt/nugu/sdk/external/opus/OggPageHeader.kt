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

data class OggPageHeader(
    val version: Byte = 0,
    val headerType: Byte,
    val granulepos: Long,
    val streamSerialNumber: Int,
    val pageCount: Int,
    var checksum: Int = 0,
    val packetCount: Byte,
    val packets: ByteArray
) {
    companion object {
        private val CAPTURE_PATTERN = "OggS".toByteArray()
        private const val PAGE_HEADER_LENGTH = 27
    }

    fun toByteArray(): ByteArray = ByteBuffer.allocate(PAGE_HEADER_LENGTH + packetCount).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        put(CAPTURE_PATTERN) //  0 -  3: capture_pattern
        put(version) //       4: stream_structure_version
        put(headerType) //       5: header_type_flag
        putLong(granulepos) //  6 - 13: absolute granule position
        putInt(streamSerialNumber) // 14 - 17: stream serial number
        putInt(pageCount) // 18 - 21: page sequence no
        putInt(0)  // 22 - 25: page checksum
        put(packetCount) //      26: page_segments
        put(packets, 0, packetCount.toInt()) // 27 -  x: segment_table
    }.array()
}