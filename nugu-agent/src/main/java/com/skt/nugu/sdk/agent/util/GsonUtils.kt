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
package com.skt.nugu.sdk.agent.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject

@Throws(Exception::class)
fun JsonObject.deepMerge(source: JsonObject) {
    for ((key, value) in source.entrySet()) {
        if (!has(key)) { //target does not have the same key, so perhaps it should be added to target
            if (!value.isJsonNull) //well, only add if the source value is not null
                add(key, value)
        } else {
            if (!value.isJsonNull) {
                if (value.isJsonObject) { //source value is json object, start deep merge
                    get(key).asJsonObject.deepMerge(value.asJsonObject)
                } else if(value.isJsonArray) {
                    get(key).asJsonArray.deepMerge(value.asJsonArray)
                } else {
                    add(key, value)
                }
            } else {
                remove(key)
            }
        }
    }
}

@Throws(Exception::class)
fun JsonArray.deepMerge(source: JsonArray) {
    if(size() != source.size()) {
        removeAll { true }
        addAll(source)
        return
    }

    source.forEachIndexed { index, jsonElement ->
        if(jsonElement.isJsonObject) {
            val item = JsonObject().apply {
                deepMerge(jsonElement.asJsonObject)
            }
            set(index, item)
        } else if(jsonElement.isJsonArray) {
            val item = JsonArray().apply {
                deepMerge(jsonElement.asJsonArray)
            }
            set(index, item)
        } else {
            set(index, jsonElement)
        }
    }
}

fun JsonObject.removeJsonNull() {
    val removeKeys = HashSet<String>()
    keySet().forEach {
        val elem = get(it)
        if(elem.isJsonNull) {
            removeKeys.add(it)
        } else if(elem.isJsonObject) {
            elem.asJsonObject.removeJsonNull()
        } else if(elem.isJsonArray) {
            elem.asJsonArray.removeJsonNull()
        }
    }

    removeKeys.forEach {
        remove(it)
    }
}

fun JsonArray.removeJsonNull() {
    forEach {
        if(it.isJsonObject) {
            it.asJsonObject.removeJsonNull()
        }
    }
}