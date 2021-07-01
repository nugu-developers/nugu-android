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
package com.skt.nugu.sdk.agent.asr.audio

import com.skt.nugu.sdk.agent.asr.audio.AudioFormat

/**
 * Provide a encoder interface
 */
interface Encoder {
    /**
     * Start an encoding.
     *
     * @param audioFormat format of audio to encode
     * @return true if new encoding started, false otherwise
     */
    fun startEncoding(audioFormat: AudioFormat): Boolean

    /**
     * Encode an [input]
     *
     * @param input the data to encoding. also must be follow the audioFormat provided at [startEncoding]
     * @param offset the start offset in [input]
     * @param size the number of bytes to encode
     *
     * @return encoded data
     */
    fun encode(input: ByteArray, offset: Int, size: Int): ByteArray?

    fun flush(): ByteArray?

    /**
     * Stop the current encoding
     */
    fun stopEncoding()

    fun getMimeType(): String
    fun getCodecName(): String
}