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

package com.skt.nugu.sdk.agent.ext.message.payload

import com.skt.nugu.sdk.agent.ext.message.Contact

data class SendCandidatesPayload(
    val playServiceId: String,
    val recipientName: String,
    val candidates: Array<Contact>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendCandidatesPayload

        if (playServiceId != other.playServiceId) return false
        if (recipientName != other.recipientName) return false
        if (candidates != null) {
            if (other.candidates == null) return false
            if (!candidates.contentEquals(other.candidates)) return false
        } else if (other.candidates != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + recipientName.hashCode()
        result = 31 * result + (candidates?.contentHashCode() ?: 0)
        return result
    }
}