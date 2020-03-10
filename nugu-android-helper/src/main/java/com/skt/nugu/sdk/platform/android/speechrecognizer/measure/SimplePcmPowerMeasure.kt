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

package com.skt.nugu.sdk.platform.android.speechrecognizer.measure

import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

class SimplePcmPowerMeasure(
    private val bias: Float = 3f // default: bias 3db for mobile phone
) : PowerMeasure{
    companion object {
        private const val TAG = "SimplePcmPowerMeasure"
    }
    override fun measure(buffer: ByteBuffer): Float {
        buffer.rewind()
        val count = buffer.capacity() / 2
        var sum = 0.0
        var sample:Short
        var left: Int
        var right: Int
        while(buffer.hasRemaining()) {
            right = (buffer.get().toInt() and 0xFF)
            left = (buffer.get().toInt() shl 8)
            sample = (left or right).toShort()
            sum += (sample * sample)
        }
        val energy = Math.max(1.0, sqrt(sum/count))
        val power = (20.0*(log10(energy) - log10(32768.0)) - bias).toFloat()
        //Logger.d(TAG, "[measure] count: $count, sum: $sum, energy: $energy, power: $power")

        return power
    }
}