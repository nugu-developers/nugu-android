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
package com.skt.nugu.sdk.external.silvertray

import android.util.Log
import com.skt.nugu.silvertray.codec.MediaFormat
import com.skt.nugu.silvertray.source.DataSource
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class RawCBRStreamSource(private val attachmentReader: Attachment.Reader): DataSource {
    companion object {
        private const val TAG = "RawCBRStreamSource"
        private const val SAMPLE_RATE = 24000
        private const val FRAME_SIZE = 960
        private const val MAX_FRAME_SIZE = FRAME_SIZE * 3
        private const val HEADER_BUFFER_SIZE = 8
    }

    private val inputStream = PipedInputStream()
    private val outputStream = PipedOutputStream()

    private val readAttachmentThread = Executors.newSingleThreadExecutor()
    private var readAttachmentFuture: Future<*>? = null

    init {
        outputStream.connect(inputStream)
    }

    private val format: MediaFormat by lazy {
        object : MediaFormat {
            override fun getMimeType(): String {
                return "audio/opus"
            }

            override fun getSampleRate(): Int {
                return SAMPLE_RATE
            }

            override fun getFrameSize(): Int {
                return FRAME_SIZE
            }

            override fun getMaxFrameSize(): Int {
                return MAX_FRAME_SIZE
            }

            override fun getChannelCount(): Int {
                return 1
            }
        }
    }

    private var currentFrame: ByteArray? = null

    override fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
        // start read thread
        if (readAttachmentFuture == null) {
            readAttachmentFuture = readAttachmentThread.submit {
                outputStream.use { outputStream ->
                    val temp = ByteArray(1024)
                    var readCount = 0
                    try {
                        while (Thread.currentThread().isInterrupted == false &&
                            kotlin.run {  readCount = attachmentReader.read(temp, 0, temp.size); readCount }  > 0) {
                            outputStream.write(temp, 0, readCount)
                        }
                    } catch (e: InterruptedException){
                        Log.d(TAG, "[readSampleData] read attachment tread has been interrupted")
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }

        // return current frame if exist
        currentFrame?.let {
            buffer.put(it)
            return it.size
        }

        // load new frame
        val headerBuffer = ByteArray(HEADER_BUFFER_SIZE)
        var headerReadTotal = 0
        var headerReadCount = 0
        while (run { headerReadCount = inputStream.read(headerBuffer, headerReadTotal, HEADER_BUFFER_SIZE - headerReadTotal)
                headerReadTotal += headerReadCount
                headerReadTotal } < HEADER_BUFFER_SIZE) {
            if (headerReadCount < 0) {
                Log.d(TAG, "[readSampleData] end of stream")
                return 0
            }
            Log.w(TAG, "[readSampleData] Failed to read header. retry ($headerReadCount)")
        }

        val inputSize = ((headerBuffer[0].toInt() and 0xFF) shl 24) or ((headerBuffer[1].toInt() and 0xFF) shl 16) or
                ((headerBuffer[2].toInt() and 0xFF) shl 8) or ((headerBuffer[3].toInt() and 0xFF) shl 0)
        if (inputSize <= 0) {
            Log.e(TAG, "[readSampleData] Failed to get input size ($inputSize)")
            return 0
        }

        var totalCount = 0
        var readCount = 0
        val payloadBuffer = ByteArray(inputSize)
        while ( run { readCount = inputStream.read(payloadBuffer, totalCount, inputSize - totalCount)
                totalCount += readCount
                totalCount }  < inputSize) {
            if (readCount < 0) {
                Log.e(TAG, "[readSampleData] Input size is $inputSize and read count $readCount, but reached to end of stream")
                return 0
            }
        }
        // return received data
        ByteArrayOutputStream().use {
            it.write(headerBuffer)
            it.write(payloadBuffer)
            it.toByteArray().also { frame ->
                buffer.put(frame)
                currentFrame = frame
                return frame.size
            }
        }
        return 0
    }

    override fun advance() {
        currentFrame = null
    }

    override fun release() {
        readAttachmentFuture?.cancel(true) // interrupt
        readAttachmentThread.shutdown()
        inputStream.close()
    }

    override fun getMediaFormat(): MediaFormat {
        return format
    }
}