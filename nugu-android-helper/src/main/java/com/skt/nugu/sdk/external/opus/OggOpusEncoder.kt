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

import com.skt.nugu.keensense.tyche.Logger
import com.skt.nugu.opus.wrapper.JniBridge
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer

class OggOpusEncoder : Encoder {
    companion object {
        private const val TAG = "OggOpusEncoder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1

        private const val OPUS_APPLICATION_VOIP = 2048
    }

    private val jniBridge = JniBridge()
    private var opusEncoder: Long? = null
    private var writer: OggOpusWriter? = null
    private var firstEncoding = true
    private var frameSize: Int = -1

    override fun startEncoding(audioFormat: AudioFormat): Boolean {
        Logger.d(TAG, "[startEncoding] $audioFormat")

        if (audioFormat.numChannels != CHANNELS) {
            Logger.d(TAG, "[startEncoding] failed: only mono channel allowed.")
            return false
        }

        if (audioFormat.sampleRateHz != SAMPLE_RATE) {
            Logger.d(TAG, "[startEncoding] failed: 16000hz only allowed.")
            return false
        }

        opusEncoder = createNativeEncoder()
        if (opusEncoder == null) {
            Logger.d(TAG, "[startEncoding] failed to create native encoder.")
            return false
        }

        firstEncoding = true
        writer = OggOpusWriter(audioFormat)
        frameSize = audioFormat.sampleRateHz / 1000 * 10 // maybe 160 (means 10ms)

        return true
    }

    private fun createNativeEncoder(): Long? {
        val encoder = jniBridge.createOpusEncoder(SAMPLE_RATE, CHANNELS, OPUS_APPLICATION_VOIP)

        if (encoder < 0) {
            return null
        }

        return encoder
    }

    override fun encode(input: ByteArray, offset: Int, size: Int): ByteArray? {
        ByteArrayOutputStream().use {
            if (firstEncoding) {
                it.write(writer?.writeHeader("encoder=Lavc57.107.100 libopus"))
                firstEncoding = false
            }
            it.write(encodeAndWrite(input, offset, size))
            val result = it.toByteArray()
            Logger.d(TAG, "[encode] encoded size : ${result.size}")
            return result
        }
    }

    override fun flush(): ByteArray? {
        val result = writer?.flush(true)
        Logger.d(TAG, "[flush] encoded size : ${result?.size}")
        return result
    }

    override fun stopEncoding() {
        Logger.d(TAG, "[stopEncoding]")
        close()
    }

    override fun getMimeType(): String {
        return "audio/ogg; codecs=opus"
    }

    override fun getCodecName(): String {
        return "ogg_opus"
    }

    @Throws(IOException::class)
    fun encodeAndWrite(input: ByteArray, offset: Int, size: Int): ByteArray? {
        ByteArrayOutputStream().use { os ->
            ByteArrayInputStream(input, offset, size).use { ios ->
                val data = ByteArray(frameSize * 2 * CHANNELS) // framesize * short size * channels
                var read: Int
                while (ios.read(data).also { read = it } > 0) {
                    val inputBuffer = ByteBuffer.allocateDirect(read)
                    var i = 0
                    while (i < read) {
                        inputBuffer.put(data[i])
                        i++
                    }
                    inputBuffer.flip()

                    var opus_encoded: Int = -1
                    val opusEncodedBuffer = ByteBuffer.allocateDirect(read)
                    opusEncoder?.let {
                        opus_encoded = jniBridge.encode(
                            it,
                            inputBuffer,
                            frameSize,
                            opusEncodedBuffer,
                            read
                        )
                    }

                    if (opus_encoded > 0) {
                        (opusEncodedBuffer as Buffer).position(opus_encoded)
                        opusEncodedBuffer.flip()
                        val opusData = ByteArray(opusEncodedBuffer.remaining())
                        opusEncodedBuffer[opusData, 0, opusData.size]
                        os.write(writer?.writePacket(opusData, 0, opusData.size))
                    }
                }
            }

            return os.toByteArray().apply {
                Logger.d(
                    TAG,
                    "[encodeAndWrite] $size"
                )
            }
        }
    }

    private fun close() {
        Logger.d(TAG, "[close]")
        try {
            opusEncoder?.let {
                jniBridge.destroyOpusEncoder(it)
            }
            opusEncoder = null
            writer = null
            frameSize = -1

        } catch (var2: IOException) {
            var2.printStackTrace()
        }
    }
}