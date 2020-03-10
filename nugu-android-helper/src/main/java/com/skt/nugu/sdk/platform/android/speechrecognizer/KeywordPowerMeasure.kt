/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.platform.android.speechrecognizer

import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.AccumulateBufferPowerMeasure
import com.skt.nugu.sdk.platform.android.speechrecognizer.measure.PowerMeasure
import java.nio.ByteBuffer
import java.util.*

class KeywordPowerMeasure(private val powerMeasure: PowerMeasure) :
    AccumulateBufferPowerMeasure {
    companion object {
        private const val WINDOW_SIZE = 100
        private const val MAX_DECIBEL = 0f
        private const val MIN_DECIBEL = -99f
    }

    private val window: Queue<Float> = LinkedList()

    override fun accumulate(buffer: ByteBuffer) {
        powerMeasure.measure(buffer).apply {
            synchronized(window) {
                if (window.size >= WINDOW_SIZE) {
                    window.poll()
                }
                window.offer(this)
            }
        }
    }

    override fun getEstimatedPower(): WakeupInfo.Power {
        var speechDecibel = MIN_DECIBEL
        var noiseDecibel = MAX_DECIBEL


        synchronized(window) {
            window.forEach {
                if(it > speechDecibel) {
                    speechDecibel = Math.min(MAX_DECIBEL, it)
                }
                if(it < noiseDecibel) {
                    noiseDecibel = Math.max(MIN_DECIBEL, it)
                }
            }
        }

        return WakeupInfo.Power(speechDecibel, noiseDecibel)
    }
}