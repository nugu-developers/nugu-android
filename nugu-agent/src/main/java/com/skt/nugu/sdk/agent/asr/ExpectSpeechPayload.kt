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
package com.skt.nugu.sdk.agent.asr

import com.google.gson.annotations.SerializedName

data class ExpectSpeechPayload private constructor(
    @SerializedName("timeoutInMilliseconds")
    val timeoutInMilliseconds: Long,
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("playServiceId")
    val playServiceId: String?,
    @SerializedName("domainTypes")
    val domainTypes: Array<String>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExpectSpeechPayload

        if (timeoutInMilliseconds != other.timeoutInMilliseconds) return false
        if (sessionId != other.sessionId) return false
        if (playServiceId != other.playServiceId) return false
        if (domainTypes != null) {
            if (other.domainTypes == null) return false
            if (!domainTypes.contentEquals(other.domainTypes)) return false
        } else if (other.domainTypes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timeoutInMilliseconds.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + (playServiceId?.hashCode() ?: 0)
        result = 31 * result + (domainTypes?.contentHashCode() ?: 0)
        return result
    }
}