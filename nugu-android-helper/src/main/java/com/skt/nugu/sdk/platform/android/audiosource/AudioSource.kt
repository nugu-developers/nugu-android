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
package com.skt.nugu.sdk.platform.android.audiosource

import java.nio.ByteBuffer

/**
 * The interface for audio source
 *
 * @see [AudioSourceManager], [AudioSourceFactory]
 */
interface AudioSource {
    companion object {
        const val SOURCE_CLOSED = -1
        const val SOURCE_BAD_VALUE = -2
    }

    /**
     * Open audio source.
     *
     * Make the necessary preparations to read the source.(Optional)
     * @return: true: if success, false: otherwise
     */
    fun open(): Boolean

    /**
     * @param buffer the byte array into which the data is read.
     * @param offsetInBytes the start offset in data
     * @param sizeInBytes the maximum number of bytes to read
     * @return int zero or the positive number of bytes that were read, or negative number if error.
     * @see [SOURCE_CLOSED]
     */
    fun read(buffer: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int

    /**
     * @param buffer the byte buffer into which the data is read.
     * @param sizeInBytes the maximum number of bytes to read
     * @return int zero or the positive number of bytes that were read, or negative number if error.
     * @see [SOURCE_CLOSED]
     */
    fun read(buffer: ByteBuffer, sizeInBytes: Int): Int

    /**
     * Close audio source.
     */
    fun close()
}