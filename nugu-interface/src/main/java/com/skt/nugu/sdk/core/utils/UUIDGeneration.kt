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
package com.skt.nugu.sdk.core.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*

/**
 * A class that represents an immutable universally unique identifier.
 * A UUID represents a 128-bit value.
 */
class UUIDGeneration {
    private var mostSigBits: Long = 0
    private var leastSigBits: Long = 0

    private constructor (data: ByteArray) {
        var msb: Long = 0
        var lsb: Long = 0

        assert(data.size == 16 || data.size == 8) { "data must be 16 or 8 bytes in length" }

        for (i in 0..7) {
            if (data.size > i) {
                msb = msb shl 8 or (data[i].toLong() and 0xff)
            }
        }
        for (i in 8..15) {
            if (data.size > i) {
                lsb = lsb shl 8 or (data[i].toLong() and 0xff)
            }
        }

        this.mostSigBits = msb
        this.leastSigBits = lsb
    }

    /**
     * Returns val represented by the specified number of hex digits.
     **/
    private fun digits(value: Long, digits: Int): String {
        val hi = 1L shl digits * 4
        return java.lang.Long.toHexString(hi or (value and hi - 1)).substring(1)
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        val msb = digits(mostSigBits shr 32, 8) + "" +
                digits(mostSigBits shr 16, 4) + "" +
                digits(mostSigBits, 4)
        if (leastSigBits == 0L) {
            return msb
        }

        val lsb = digits(leastSigBits shr 48, 4) + "" +
                digits(leastSigBits, 12)

        return msb + lsb
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    override fun equals(obj: Any?): Boolean {
        if (null == obj || obj.javaClass != UUIDGeneration::class.java)
            return false
        val id = obj as UUIDGeneration
        return mostSigBits == id.mostSigBits && leastSigBits == id.leastSigBits
    }

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int {
        val hilo = mostSigBits xor leastSigBits
        return (hilo shr 32).toInt() xor hilo.toInt()
    }


    companion object {
        private const val TAG = "UUIDGeneration"

        private val VERSION = 0
        // 2019/1/1 AM 12:00:00.000
        private val BASE_TIME = 1546300800000L

        private val numberGenerator = SecureRandom()

        /**
         * the key is unique id.
         * generate random if key is null
         */
        var key: String? = null
        /**
         * Phases that can be used
         */
        var phase: Int = 0

        /** 16byte base16 hexa string (8bytes binary) */
        fun shortUUID(): UUIDGeneration {
            UUID.randomUUID()
            val bytes = ByteArray(8)
            numberGenerator.nextBytes(bytes)
            return UUIDGeneration(bytes)
        }

        /** 32byte base16 hexa string (16bytes binary) */
        fun timeUUID(): UUIDGeneration {
            val uuidBytes = ByteArray(16)

            val header = (VERSION shl 6 or phase).toByte()
            val random = numberGenerator.nextInt()

            val randomWithHeader = random and 0x00FFFFFF or (header.toInt() shl 24)
            val time = (Date().time - BASE_TIME) / 100
            var msb = time shl 32 or (randomWithHeader.toLong() and 0xFFFFFFFFL)

            System.arraycopy(
                getHash(
                    key
                ), 0, uuidBytes, 8, 8)

            for (i in 7 downTo 0) {
                uuidBytes[i] = (msb and 0xFF).toByte()
                msb = msb shr 8
            }
            return UUIDGeneration(
                uuidBytes
            )
        }

        /**
         * Generate a hash string.
         * @return a hash as an ByteArray.
         * */
        private fun getHash(key: String?): ByteArray {
            if (key == null) {
                // if deviceId null, Makes a random
                val bytes = ByteArray(8)
                numberGenerator.nextBytes(bytes)
                return bytes
            }
            val data = key.toByteArray(charset("UTF-8"))
            val md: MessageDigest
            try {
                md = MessageDigest.getInstance("SHA-1")
            } catch (e: NoSuchAlgorithmException) {
                throw Exception("SHA-1 not supported", e)
            }
            return md.digest(data)
        }
    }
}