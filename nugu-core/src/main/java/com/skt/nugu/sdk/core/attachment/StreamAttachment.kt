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
package com.skt.nugu.sdk.core.attachment

import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.utils.Logger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.ArrayList
import kotlin.concurrent.read
import kotlin.concurrent.write

class StreamAttachment(private val attachmentId: String) : Attachment {
    companion object {
        private const val TAG = "StreamAttachment"
    }

    private val attachmentContents = ArrayList<ByteArray>()
    private var reachEnd = false
    private val lock = ReentrantReadWriteLock()
    private val writer: Attachment.Writer = object : Attachment.Writer {
        override fun write(bytes: ByteArray) {
            lock.write {
                attachmentContents.add(bytes)
                Logger.d(TAG, "[write] size : ${bytes.size} / id: $attachmentId")
            }
        }

        override fun close() {
            lock.write {
                reachEnd = true
            }
        }

        override fun isClosed(): Boolean = reachEnd
    }

    private var hasCreatedReader = false
    private var hasCreatedWriter = false

    override fun createWriter(): Attachment.Writer {
        Logger.d(TAG, "[createWriter]")
        hasCreatedWriter = true
        return writer
    }

    override fun createReader(): Attachment.Reader {
        Logger.d(TAG, "[createReader]")
        hasCreatedReader = true
        return object : Attachment.Reader {
            private var contentIndex = 0
            private var contentPosition = 0
            private var isClosing = false
            private var isReading = false

            override fun read(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
                var dstOffsetInBytes = offsetInBytes
                var leftSizeInBytes = sizeInBytes

                while (leftSizeInBytes > 0 && !isClosing) {
                    isReading = true

                    lock.read {
                        if (attachmentContents.size > contentIndex) {
                            val source = attachmentContents[contentIndex]
                            val readableSize = source.size - contentPosition
                            val readSize = Math.min(readableSize, leftSizeInBytes)

                            System.arraycopy(source, contentPosition, bytes, dstOffsetInBytes, readSize)

                            contentPosition += readSize
                            if (contentPosition == source.size) {
                                contentPosition = 0
                                contentIndex++
                            }

                            dstOffsetInBytes += readSize
                            leftSizeInBytes -= readSize
                        } else if (reachEnd) {
                            // 끝까지 읽었음.
                            if (leftSizeInBytes == sizeInBytes) {
                                return -1
                            } else {
                                return sizeInBytes - leftSizeInBytes
                            }
                        } else {
                            // 읽을 데이터가 하나도 준비되있지 않음. (UNDERRUN)
                        }
                    }
                }

                isReading = false

                Logger.d(TAG, "[read] $sizeInBytes, $leftSizeInBytes / id: $attachmentId")
                return sizeInBytes - leftSizeInBytes
            }

            override fun close() {
                isClosing = true
            }

            override fun isClosed(): Boolean {
                return !isReading && isClosing
            }
        }
    }

    override fun hasCreatedReader(): Boolean = hasCreatedReader

    override fun hasCreatedWriter(): Boolean = hasCreatedWriter
}