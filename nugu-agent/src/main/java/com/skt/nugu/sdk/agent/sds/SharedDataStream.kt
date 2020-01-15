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
package com.skt.nugu.sdk.agent.sds

import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Provide a streaming data from a single producer([Writer]) to multiple consumer([Reader])
 */
interface SharedDataStream {
    /**
     * Provides an interface for writing data into the stream
     */
    interface Writer : Closeable {
        /**
         * @param bytes the data to write
         * @param offsetInBytes the start offset in data
         * @param sizeInBytes the number of bytes to write
         */
        fun write(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int)
    }

    /**
     * Provides an interface for reading data from the stream
     */
    interface Reader : Closeable {
        /**
         * @param bytes the byte array into which the data is read.
         * @param offsetInBytes the start offset in data
         * @param sizeInBytes the maximum number of bytes to read
         *
         * @return the total number of bytes which read, or or -1 if cannot read anymore because of various reason.
         */
        fun read(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int

        /**
         * @param byteBuffer the byte buffer into which the data is read.
         * @param offsetInBytes the start offset in data
         * @param sizeInBytes the maximum number of bytes to read
         *
         * @return the total number of bytes which read, or or -1 if cannot read anymore because of various reason.
         */
        fun read(byteBuffer: ByteBuffer, offsetInBytes: Int, sizeInBytes: Int): Int

        /**
         * Check if reader closed or not.
         * @return true if closed, false otherwise.
         */
        fun isClosed(): Boolean

        /**
         * Get position which start reading at stream
         * @return position of reading start
         */
        fun position(): Long
    }

    /**
     * Creates a [Writer] to the stream.
     * Should be only one [Writer] is allowed.
     */
    fun createWriter(): Writer

    /**
     * Creates a [Reader] to the stream.
     */
    fun createReader(initialPosition: Long? = null): Reader

    /**
     * Get last written position of stream
     */
    fun getPosition(): Long
}