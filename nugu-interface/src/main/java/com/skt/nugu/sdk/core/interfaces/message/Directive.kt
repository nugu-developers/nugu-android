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

import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import java.lang.StringBuilder

/**
 * The data class for Directive
 * @param attachmentManager the attachment manager
 * @param header the header
 * @param payload the payload (json formatted string).
 */
data class Directive (
    private val attachmentManager: AttachmentManagerInterface?,
    val header: Header,
    val payload: String
) {
    private var attachmentReader: Attachment.Reader? = null

    fun getMessageId(): String = header.messageId

    fun getDialogRequestId(): String = header.dialogRequestId

    fun getNamespace(): String = header.namespace

    fun getName(): String = header.name

    fun getNamespaceAndName(): NamespaceAndName =
        NamespaceAndName(getNamespace(), getName())

    fun getAttachmentReader(): Attachment.Reader? {
        var reader = attachmentReader
        if (reader == null) {
            reader = attachmentManager?.createReader(header.messageId)
            attachmentReader = reader
        }

        return reader
    }

    fun destroy() {
//        Logger.d(TAG, "[destroy]")
        attachmentReader?.close()
        attachmentManager?.removeAttachment(header.messageId)
    }
}