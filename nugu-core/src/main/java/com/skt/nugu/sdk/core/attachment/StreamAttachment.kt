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
import java.io.EOFException
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer
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

    private var exceptionOnReadEnd: IOException? = null

    private val bufferEventListeners = HashSet<BufferEventListener>()
    private val attachmentContents = ArrayList<ByteBuffer>()
    private var reachEnd = false
    private val lock = ReentrantReadWriteLock()
    private val writer: Attachment.Writer = object : Attachment.Writer {
        override fun write(buffer: ByteBuffer) {
            lock.write {
                attachmentContents.add(buffer)
                Logger.d(TAG, "[write] limit: ${buffer.limit()} / capacity: ${buffer.capacity()} / id: $attachmentId")
            }

            notifyBufferFilled()
        }

        override fun close(error: Boolean) {
            lock.write {
                if(error) {
                    exceptionOnReadEnd = if (attachmentContents.isEmpty()) {
                        EOFException("Unexpectedly reach end: no attachment")
                    } else {
                        EOFException("Unexpectedly reach end: partially received but not completed.")
                    }
                }

                reachEnd = true
            }

            notifyBufferFullFilled()
        }

        override fun isClosed(): Boolean = reachEnd
    }

    override fun createWriter(): Attachment.Writer {
        Logger.d(TAG, "[createWriter]")
        return writer
    }

    override fun createReader(): Attachment.Reader {
        Logger.d(TAG, "[createReader]")
        return object : Attachment.Reader, BufferEventListener {
            private var contentIndex = 0
            private var chunkIndex = 0
            private var isClosing = false
            private var isReading = false
            private val waitLock = Object()

            init {
                synchronized(bufferEventListeners) {
                    bufferEventListeners.add(this)
                }
            }

            override fun read(bytes: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
                return readInternal(offsetInBytes, sizeInBytes) {src, dstOffsetInBytes ->
                    src.get(bytes, dstOffsetInBytes, src.remaining())
                }
            }

            override fun read(byteBuffer: ByteBuffer, offsetInBytes: Int, sizeInBytes: Int): Int {
                (byteBuffer as Buffer).position(offsetInBytes)

                return readInternal(offsetInBytes, sizeInBytes) {src,_ ->
                    byteBuffer.put(src)
                }
            }

            override fun readChunk(): ByteBuffer? {
                while(!isClosing) {
                    lock.read {
                        if(attachmentContents.size > chunkIndex) {
                            return attachmentContents[chunkIndex++]
                        }

                        if(reachEnd) {
                            val e = exceptionOnReadEnd
                            if(e == null) {
                                return null
                            } else {
                                throw e
                            }
                        }
                    }

                    if (!isClosing && !reachEnd) {
                        synchronized(waitLock) {
                            if (!isClosing && !reachEnd) {
                                waitLock.wait(50)
                            }
                        }
                    }
                }

                return null
            }

            private fun readInternal(
                offsetInBytes: Int,
                sizeInBytes: Int,
                readFunction: (ByteBuffer, Int) -> Unit
            ): Int {
                var dstOffsetInBytes = offsetInBytes
                var leftSizeInBytes = sizeInBytes

                while (leftSizeInBytes > 0 && !isClosing) {
                    isReading = true
                    var shouldWaitWrite = false

                    lock.read {
                        if (attachmentContents.size > contentIndex) {
                            val source = attachmentContents[contentIndex]
                            val readSize = Math.min(source.remaining(), leftSizeInBytes)
                            val sourceLimit = source.limit()
                            source.limit(source.position() + readSize)
                            readFunction.invoke(source, dstOffsetInBytes)
                            source.limit(sourceLimit)

                            if (!source.hasRemaining()) {
                                contentIndex++
                            }

                            dstOffsetInBytes += readSize
                            leftSizeInBytes -= readSize
                        } else if (reachEnd) {
                            // 끝까지 읽었음.
                            isReading = false
                            if (leftSizeInBytes == sizeInBytes) {
                                val e = exceptionOnReadEnd
                                if(e == null) {
                                    return -1
                                } else {
                                    throw e
                                }
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

                val read = sizeInBytes - leftSizeInBytes

                return if(read == 0 && isClosing) {
                    val e = exceptionOnReadEnd
                    if(e == null) {
                        return -1
                    } else {
                        throw e
                    }
                } else {
                    read
                }
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