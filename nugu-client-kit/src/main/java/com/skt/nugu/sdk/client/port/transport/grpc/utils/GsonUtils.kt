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
package com.skt.nugu.sdk.client.port.transport.grpc.utils

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import java.lang.reflect.Field

object GsonUtils {
    fun toJson(src: Any): String {
        return GsonBuilder().setFieldNamingStrategy(UnderscoresNamingStrategy())
            .addSerializationExclusionStrategy(UnknownFieldsExclusionStrategy())
            .create().toJson(src)
    }

    // directives_ to directives
    internal class UnderscoresNamingStrategy : FieldNamingStrategy {
        override fun translateName(f: Field): String {
            val index = f.name.lastIndexOf("_")
            return if (index == -1 || index != f.name.lastIndex) {
                f.name
            } else {
                f.name.substring(0, index)
            }
        }
    }

    internal class UnknownFieldsExclusionStrategy : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes): Boolean {
            return when (f.name) {
                "unknownFields",
                "memoizedSerializedSize",
                "memoizedHashCode" -> true
                else -> false
            }
        }

        override fun shouldSkipClass(clazz: Class<*>): Boolean {
            return false
        }
    }
}