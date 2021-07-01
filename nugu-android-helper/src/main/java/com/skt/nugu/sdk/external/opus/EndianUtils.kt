/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.external.opus

object EndianUtils {
    fun writeIntLE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (0xff and value).toByte()
        data[offset + 1] = (0xff and (value ushr 8)).toByte()
        data[offset + 2] = (0xff and (value ushr 16)).toByte()
        data[offset + 3] = (0xff and (value ushr 24)).toByte()
    }
}