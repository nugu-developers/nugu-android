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

package com.skt.nugu.sdk.core.interfaces.dialog

data class DialogAttribute(
    val playServiceId: String?,
    val domainTypes: Array<String>?,
    val asrContext: String? // json formatted string
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DialogAttribute

        if (playServiceId != other.playServiceId) return false
        if (domainTypes != null) {
            if (other.domainTypes == null) return false
            if (!domainTypes.contentEquals(other.domainTypes)) return false
        } else if (other.domainTypes != null) return false
        if (asrContext != other.asrContext) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId?.hashCode() ?: 0
        result = 31 * result + (domainTypes?.contentHashCode() ?: 0)
        result = 31 * result + (asrContext?.hashCode() ?: 0)
        return result
    }
}
