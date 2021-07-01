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

// vorbis comment format
data class OpusTags(
    val vender: String = "skt_nugu",
    val comment: String
) {
    companion object {
        private val CAPTURE_PATTERN = "OpusTags".toByteArray()
    }

    fun getSize(): Int = CAPTURE_PATTERN.size + // capture_pattern
            4 + // vender length (int)
            vender.length + // vender string
            4 + // comment list count (int)
            4 + // comment length (we have only one comment now) (int)
            comment.length // comment string


    fun toByteArray(): ByteArray = ByteBuffer.allocate(getSize()).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        put(CAPTURE_PATTERN)
        putInt(vender.length)
        put(vender.toByteArray())
        putInt(1)
        putInt(comment.length)
        put(comment.toByteArray())
    }.array()
}