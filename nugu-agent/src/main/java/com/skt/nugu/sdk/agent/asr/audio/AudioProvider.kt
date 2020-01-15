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

import com.skt.nugu.sdk.agent.sds.SharedDataStream

/**
 * Provide interface for management of audio input stream
 */
interface AudioProvider {
    /** Acquire audio input stream
     * @param consumer the object which consume audio stream
     * @return audio data stream
     */
    fun acquireAudioInputStream(consumer: Any): SharedDataStream?

    /** Release audio input stream
     * @param consumer the object which consume audio stream
     */
    fun releaseAudioInputStream(consumer: Any)

    /**
     * Return an audio format of audio input stream which acquired from [acquireAudioInputStream]
     */
    fun getFormat(): AudioFormat
}