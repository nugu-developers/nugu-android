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

/**
 * A class that represents an immutable universally unique identifier.
 * A UUID represents a 128-bit value.
 */
class UUIDGeneration {
    private var mostSigBits: Long = 0
    private var leastSigBits: Long = 0

    private constructor(data: ByteArray) {
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

    private constructor(msb: Long, lsb: Long)  {
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
    override fun equals(other: Any?): Boolean {
        if (null == other || other.javaClass != UUIDGeneration::class.java)
            return false
        val id = other as UUIDGeneration
        return mostSigBits == id.mostSigBits && leastSigBits == id.leastSigBits
    }

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int {
        val hilo = mostSigBits xor leastSigBits
        return (hilo shr 32).toInt() xor hilo.toInt()
    }

    fun getTime(): Long {
        return (mostSigBits shr 24 and 0xffffffffffL) + BASE_TIME
    }

    companion object {
        private const val TAG = "UUIDGeneration"

        private val VERSION = 0x01
        // 2019/1/1 AM 12:00:00.000
        private val BASE_TIME = 1546300800000L

        private val numberGenerator = SecureRandom()

        /**
         * the key is unique id.
         * generate random if key is null
         */
        private var key: String? = null

        /**
         * 64-bit(8bytes) base16 hexa string  (16-digit string)
         */
        private fun shortUUID(): UUIDGeneration {
            val bytes = ByteArray(8)
            numberGenerator.nextBytes(bytes)
            return UUIDGeneration(bytes)
        }
        /**
         * 128-bit(16 bytes) base16 hexa string (32-digit string)
        +------------+-------+
        | field name | bytes |
        +--------------------+
        | time       |   5   |
        | version    |   1   |
        | hash       |   6   |
        | random     |   4   |
        +--------------------+
         **/
        fun timeUUID(): UUIDGeneration {
            val uuidBytes = ByteArray(16)

            /* time : 5 byte  */
            var time = System.currentTimeMillis() - BASE_TIME
            for (i in 4 downTo 0) {
                uuidBytes[i] = (time and 0xFF).toByte()
                time = time shr 8
            }

            /* version : 1 byte  */
            uuidBytes[5] = VERSION.toByte()

            /* hash : 6 byte  */
            System.arraycopy(hashKey(), 0, uuidBytes, 6, 6)

            /* random : 4 byte  */
            var random = numberGenerator.nextInt()
            for (i in 15 downTo 12) {
                uuidBytes[i] = (random and 0xFF).toByte()
                random = random shr 8
            }

            return UUIDGeneration(uuidBytes)
        }

        fun fromString( name : String ) : UUIDGeneration {
            var mostSigBits = java.lang.Long.decode("0x" + name.substring(0,8)).toLong()
            mostSigBits = mostSigBits shl 16
            mostSigBits = mostSigBits or java.lang.Long.decode("0x" + name.substring(8,12)).toLong()
            mostSigBits = mostSigBits shl 16
            mostSigBits = mostSigBits or java.lang.Long.decode("0x" + name.substring(12,16)).toLong()

            var leastSigBits = java.lang.Long.decode("0x" + name.substring(16,20)).toLong()
            leastSigBits = leastSigBits shl 48
            leastSigBits = leastSigBits or java.lang.Long.decode("0x" + name.substring(20,32)).toLong()

            return UUIDGeneration(mostSigBits, leastSigBits)
        }

        /**
         * Generate a hash string.
         * @return a hash as an ByteArray.
         * */
        private fun hashKey(): ByteArray {
            this.key = this.key ?: shortUUID().toString()
            try {
                val md = MessageDigest.getInstance("SHA-1")
                val bytes = key!!.toByteArray(charset("UTF-8"))
                return md.digest(bytes)
            } catch (e: NoSuchAlgorithmException) {
                throw Exception("SHA-1 not supported", e)
            }
        }
    }
}