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
package com.skt.nugu.sdk.core.interfaces.message

/**
 * data class for attachment message
 * @param content the binary stream data
 * @param header the header
 * @param isEnd the end flag of attachment
 * @param parentMessageId the parent message id which associated with attachment
 * @param seq the sequence number
 */
data class AttachmentMessage(
    val content: ByteArray,
    val header: Header,
    val isEnd: Boolean,
    val parentMessageId: String,
    val seq: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttachmentMessage

        if (!content.contentEquals(other.content)) return false
        if (header != other.header) return false
        if (isEnd != other.isEnd) return false
        if (parentMessageId != other.parentMessageId) return false
        if (seq != other.seq) return false

        return true
    }

    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + isEnd.hashCode()
        result = 31 * result + parentMessageId.hashCode()
        result = 31 * result + seq
        return result
    }
}