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

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.common.InteractionControl
import com.skt.nugu.sdk.agent.ext.message.Contact
import com.skt.nugu.sdk.agent.ext.message.RecipientIntended

data class GetMessagePayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    @SerializedName("recipientIntended")
    val recipientIntended: RecipientIntended?,
    @SerializedName("candidates")
    val candidates: Array<Contact>,
    @SerializedName("interactionControl")
    val interactionControl: InteractionControl?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetMessagePayload

        if (playServiceId != other.playServiceId) return false
        if (recipientIntended != other.recipientIntended) return false
        if (!candidates.contentEquals(other.candidates)) return false
        if (interactionControl != other.interactionControl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + (recipientIntended?.hashCode() ?: 0)
        result = 31 * result + candidates.contentHashCode()
        result = 31 * result + (interactionControl?.hashCode() ?: 0)
        return result
    }
}