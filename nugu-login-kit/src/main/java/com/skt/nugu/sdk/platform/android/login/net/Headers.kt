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
package com.skt.nugu.sdk.platform.android.login.net

import java.util.*


/**
 * Provide for Headers
 */
@Suppress("unused")
class Headers {
    private val namesAndValues: MutableList<String> = ArrayList(20)
    /** Add new key-value pair.  */
    fun add(name: String, value: String): Headers {
        namesAndValues.add(name)
        namesAndValues.add(value.trim())
        return this
    }

    private val size: Int
        get() = namesAndValues.size / 2

    fun size(): Int = size
    fun name(index: Int): String = namesAndValues[index * 2]
    fun value(index: Int): String = namesAndValues[index * 2 + 1]
    fun names(): Set<String> {
        val result = TreeSet(String.CASE_INSENSITIVE_ORDER)
        for (i in 0 until size) {
            result.add(name(i))
        }
        return Collections.unmodifiableSet(result)
    }
    fun values(name: String): List<String> {
        var result: MutableList<String>? = null
        for (i in 0 until size) {
            if (name.equals(name(i), ignoreCase = true)) {
                if (result == null) result = ArrayList(2)
                result.add(value(i))
            }
        }
        return if (result != null) {
            Collections.unmodifiableList(result)
        } else {
            emptyList()
        }
    }
}