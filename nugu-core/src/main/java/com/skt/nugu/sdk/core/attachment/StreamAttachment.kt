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
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class StreamAttachment(private val attachmentId: String) : Attachment {
    companion object {
        private const val TAG = "StreamAttachment"
    }

    private interface BufferEventListener {
        fun onBufferFilled()
        fun onBufferFullFilled()
    }

    private val bufferEventListeners = HashSet<BufferEventListener>()
    private val attachmentContents = ArrayList<ByteArray>()
    private var reachEnd = false
    private val lock = ReentrantReadWriteLock()
    private val writer: Attachment.Writer = object : Attachment.Writer {
        override fun write(bytes: ByteArray) {
            lock.write {
                attachmentContents.add(bytes)
                Logger.d(TAG, "[write] size : ${bytes.size} / id: $attachmentId")
            }

            notifyBufferFilled()
        }

        override fun close() {
            lock.write {
                reachEnd = true
            }

            notifyBufferFullFilled()
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
        return object : Attachment.Reader, BufferEventListener {
            private var contentIndex = 0
            private var contentPosition = 0
            private var isClosing = false
            private var isReading = false
            private val waitLock = Object()

            init {
                synchronized(bufferEventListeners) {
                    bufferEventListeners.add(this)
                }
            }

            override fun read(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
                var dstOffsetInBytes = offsetInBytes
                var leftSizeInBytes = sizeInBytes

                while (leftSizeInBytes > 0 && !isClosing) {
                    isReading = true
                    var shouldWaitWrite = false

                    lock.read {
                        if (attachmentContents.size > contentIndex) {
                            val source = attachmentContents[contentIndex]
                            val readableSize = source.size - contentPosition
                            val readSize = Math.min(readableSize, leftSizeInBytes)

                            System.arraycopy(
                                source,
                                contentPosition,
                                bytes,
                                dstOffsetInBytes,
                                readSize
                            )

                            contentPosition += readSize
                            if (contentPosition == source.size) {
                                contentPosition = 0
                                contentIndex++
                            }

                            dstOffsetInBytes += readSize
                            leftSizeInBytes -= readSize
                        } else if (reachEnd) {
                            // 끝까지 읽었음.
                            isReading = false
                            if (leftSizeInBytes == sizeInBytes) {
                                return -1
                            } else {
                                return sizeInBytes - leftSizeInBytes
                            }
                        } else {
                            // 읽을 데이터가 하나도 준비되있지 않음. (UNDERRUN)
                            shouldWaitWrite = true
                        }
                    }

                    if (shouldWaitWrite && !isClosing) {
                        synchronized(waitLock) {
                            if (!isClosing && !reachEnd) {
                                waitLock.wait(50)
                            }
                        }
                    }
                }

                isReading = false

                Logger.d(TAG, "[read] $sizeInBytes, $leftSizeInBytes / id: $attachmentId")
                return sizeInBytes - leftSizeInBytes
            }

            override fun close() {
                Logger.d(TAG, "[close]")
                if (!isClosing) {
                    isClosing = true
                    synchronized(bufferEventListeners) {
                        bufferEventListeners.remove(this)
                    }
                }
                wakeLock()
            }

            override fun isClosed(): Boolean {
                return !isReading && isClosing
            }

            private fun wakeLock() {
                synchronized(waitLock) {
                    waitLock.notifyAll()
                }
            }

            override fun onBufferFilled() {
                wakeLock()
            }

            override fun onBufferFullFilled() {
                wakeLock()
            }
        }
    }

    override fun hasCreatedReader(): Boolean = hasCreatedReader

    override fun hasCreatedWriter(): Boolean = hasCreatedWriter

    private fun notifyBufferFilled() {
        synchronized(bufferEventListeners) {
            bufferEventListeners.forEach {
                it.onBufferFilled()
            }
        }
    }

    private fun notifyBufferFullFilled() {
        synchronized(bufferEventListeners) {
            bufferEventListeners.forEach {
                it.onBufferFullFilled()
            }
        }
    }
}