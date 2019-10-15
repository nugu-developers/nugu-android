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

import java.util.concurrent.ConcurrentHashMap
import kotlin.text.StringBuilder
import java.net.URLEncoder


/**
 * Provide for Form Encoding.
 */
class FormEncodingBuilder {
    private val map = ConcurrentHashMap<String, String>()
    /** Add new key-value pair.  */
    fun add(name: String, value: String): FormEncodingBuilder {
        map[name] = value
        return this
    }

    /**
     * Returns a string representation of map
     */
    override fun toString(): String {
        val builder = StringBuilder()

        map.forEach {
            if (builder.isNotEmpty()) {
                builder.append('&')
            }
            builder.append(URLEncoder.encode(it.key, "UTF-8"))
                .append('=')
                .append(URLEncoder.encode(it.value, "UTF-8"))
        }
        return builder.toString()
    }
}