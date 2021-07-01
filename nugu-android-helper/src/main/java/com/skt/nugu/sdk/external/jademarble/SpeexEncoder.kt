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
package com.skt.nugu.sdk.external.jademarble

import com.skt.nugu.jademarblelib.EpdEngine
import com.skt.nugu.jademarblelib.TycheSpeexEncoder
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.audio.Encoder

/**
 * Porting class for [TycheSpeexEncoder] to use in NUGU SDK
 */
class SpeexEncoder: Encoder {
    companion object {
        private const val TAG = "SpeexEncoder"
    }

    private val speexEncoder = TycheSpeexEncoder()

    override fun startEncoding(audioFormat: AudioFormat): Boolean {
        val dataType = when(audioFormat.bitsPerSample){
            16 -> EpdEngine.DataType.DATA_LINEAR_PCM16
            8 -> EpdEngine.DataType.DATA_LINEAR_PCM8
            else -> null
        }
        if(dataType != null) {
            return speexEncoder.init(audioFormat.sampleRateHz, dataType)
        }

        return false
    }

    override fun encode(input: ByteArray, offset: Int, size: Int): ByteArray? {
        return speexEncoder.encode(input, offset, size)
    }

    override fun flush(): ByteArray? = null

    override fun stopEncoding() {
        speexEncoder.release()
    }

    override fun getMimeType(): String {
        return "audio/speex"
    }

    override fun getCodecName(): String {
        return "speex"
    }
}