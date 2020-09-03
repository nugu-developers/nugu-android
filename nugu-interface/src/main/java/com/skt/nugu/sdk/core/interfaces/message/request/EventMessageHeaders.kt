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
package com.skt.nugu.sdk.core.interfaces.message.request

import com.skt.nugu.sdk.core.interfaces.message.MessageHeaders

class EventMessageHeaders : MessageHeaders {
    internal val namesAndValues: MutableList<String> = ArrayList()

    /** Add new key-value pair.  */
    fun add(name: String, value: String?): EventMessageHeaders {
        if(null == value) {
            return this
        }
        namesAndValues.add(name)
        namesAndValues.add(value.trim())
        return this
    }

    private val size: Int
        get() = namesAndValues.size / 2

    fun size(): Int = size
    fun name(index: Int): String = namesAndValues[index * 2]
    fun value(index: Int): String = namesAndValues[index * 2 + 1]
}