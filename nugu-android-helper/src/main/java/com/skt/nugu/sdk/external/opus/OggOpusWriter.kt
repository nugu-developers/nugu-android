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

import com.skt.nugu.jademarblelib.Logger
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

class OggOpusWriter(private val audioFormat: AudioFormat) {
    companion object {
        private const val TAG = "OggOpusWriter"
        val PACKETS_PER_OGG_PAGE = 50
    }

    private var streamSerialNumber = 0
    private var dataBuffer: ByteArray
    private var dataBufferPtr = 0
    private var headerBuffer: ByteArray
    private var headerBufferPtr = 0
    private var pageCount = 0
    private var packetCount = 0
    private var granulepos: Long = 0
    private var frameSize = 0

    init {
        if (streamSerialNumber == 0) {
            streamSerialNumber = Random().nextInt()
        }

        dataBuffer = ByteArray(65565)
        dataBufferPtr = 0
        headerBuffer = ByteArray(255)
        headerBufferPtr = 0
        pageCount = 0
        packetCount = 0
        granulepos = 0L
        frameSize = audioFormat.sampleRateHz / 1000 * 10
    }

    fun writeHeader(comment: String): ByteArray {
        ByteArrayOutputStream().use {
            writeOpusHeader(it)
            writeCommentHeader(it, comment)

            return it.toByteArray().apply {
                Logger.d(TAG, "[writeHeader] header size : $size")
            }
        }
    }

    private fun writeOpusHeader(os: OutputStream) {
        val data = OpusHeader(
            channelCount = audioFormat.numChannels.toByte(),
            sampleRate = audioFormat.sampleRateHz
        ).toByteArray()

        val header = OggPageHeader(
            headerType = 2,
            granulepos = 0,
            streamSerialNumber = streamSerialNumber,
            pageCount = pageCount++,
            packetCount = 1,
            packets = byteArrayOf(data.size.toByte())
        ).toByteArray()

        var chkSum = OggCrc.checksum(0, header, 0, header.size)
        chkSum = OggCrc.checksum(chkSum, data, 0, data.size)
        EndianUtils.writeIntLE(header, 22, chkSum)

        os.write(header)
        os.write(data)
    }

    private fun writeCommentHeader(os: OutputStream, comment: String) {
        val data = OpusTags(comment = comment).toByteArray()

        val header = OggPageHeader(
            headerType = 0,
            granulepos = 0,
            streamSerialNumber = streamSerialNumber,
            pageCount = pageCount++,
            packetCount = 1,
            packets = byteArrayOf(data.size.toByte())
        ).toByteArray()

        var chkSum = OggCrc.checksum(0, header, 0, header.size)
        chkSum = OggCrc.checksum(chkSum, data, 0, data.size)
        EndianUtils.writeIntLE(header, 22, chkSum)

        os.write(header)
        os.write(data)
    }

    @Throws(IOException::class)
    fun writePacket(data: ByteArray, offset: Int, len: Int): ByteArray {
        ByteArrayOutputStream().use {
            if (len > 0) {
                if (packetCount > PACKETS_PER_OGG_PAGE) {
                    it.write(this.flush(false))
                }
                System.arraycopy(data, offset, dataBuffer, dataBufferPtr, len)
                dataBufferPtr += len
                headerBuffer[headerBufferPtr++] = len.toByte()
                ++packetCount
                granulepos += frameSize * (48000 / audioFormat.sampleRateHz) // 480
            }
            return it.toByteArray()
        }
    }

    @Throws(IOException::class)
    fun flush(eos: Boolean): ByteArray {
        ByteArrayOutputStream().use {
            Logger.d(
                TAG,
                "[flush] $granulepos, $streamSerialNumber, $pageCount, $packetCount, $headerBuffer"
            )
            val header = OggPageHeader(
                headerType = if (eos) 4 else 0,
                granulepos = granulepos,
                streamSerialNumber = streamSerialNumber,
                pageCount = pageCount++,
                packetCount = packetCount.toByte(),
                packets = headerBuffer
            ).toByteArray()

            var chksum = OggCrc.checksum(0, header, 0, header.size)
            chksum = OggCrc.checksum(chksum, dataBuffer, 0, dataBufferPtr)
            EndianUtils.writeIntLE(header, 22, chksum)
            it.write(header)
            it.write(dataBuffer, 0, dataBufferPtr)

            dataBufferPtr = 0
            headerBufferPtr = 0
            packetCount = 0
            return it.toByteArray().apply {
                Logger.d(TAG, "[flush] size: $size eos: $eos")
            }
        }
    }
}