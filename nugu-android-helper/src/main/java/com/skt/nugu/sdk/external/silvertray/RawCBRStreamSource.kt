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

import com.skt.nugu.silvertray.codec.MediaFormat
import com.skt.nugu.silvertray.source.DataSource
import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.utils.Logger
import java.nio.ByteBuffer

internal class RawCBRStreamSource(private val attachmentReader: Attachment.Reader): DataSource {
    companion object {
        private const val TAG = "RawCBRStreamSource"
        private const val SAMPLE_RATE = 24000
        private const val FRAME_SIZE = 960
        private const val MAX_FRAME_SIZE = FRAME_SIZE * 3
        private const val HEADER_BUFFER_SIZE = 8
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

    private var currentBuffer: ByteBuffer? = null
    private var currentFrameSize: Int? = null

    override fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
        // return current frame if exist
        currentBuffer?.let {
            currentFrameSize?.let { size ->
                buffer.put(it)
                return size
            }
        }

        // load new frame
        var headerReadTotal = 0
        var headerReadCount: Int
        while (run { headerReadCount = attachmentReader.read(buffer, headerReadTotal, HEADER_BUFFER_SIZE - headerReadTotal)
                headerReadTotal += headerReadCount
                headerReadTotal } < HEADER_BUFFER_SIZE) {
            if (headerReadCount < 0) {
                Logger.d(TAG, "[readSampleData] end of stream")
                return 0
            }
            Logger.w(TAG, "[readSampleData] Failed to read header. retry ($headerReadCount)")
        }

        val inputSize = ((buffer[0].toInt() and 0xFF) shl 24) or ((buffer[1].toInt() and 0xFF) shl 16) or
                ((buffer[2].toInt() and 0xFF) shl 8) or ((buffer[3].toInt() and 0xFF) shl 0)
        if (inputSize <= 0) {
            Logger.e(TAG, "[readSampleData] Failed to get input size ($inputSize)")
            return 0
        }

        var totalCount = 0
        var readCount: Int
        while ( run { readCount = attachmentReader.read(buffer, buffer.position(), inputSize - totalCount)
                totalCount += readCount
                totalCount }  < inputSize) {
            if (readCount < 0) {
                Logger.e(TAG, "[readSampleData] Input size is $inputSize and read count $readCount, but reached to end of stream")
                return 0
            }
        }

        currentBuffer = buffer
        currentFrameSize = HEADER_BUFFER_SIZE + totalCount

        return totalCount + HEADER_BUFFER_SIZE
    }

    override fun advance() {
        currentBuffer = null
    }

    override fun release() {
        attachmentReader.close()
    }

    override fun getMediaFormat(): MediaFormat {
        return format
    }
}