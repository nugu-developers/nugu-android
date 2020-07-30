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
package com.skt.nugu.sdk.core.interfaces.attachment

import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage

/**
 * This provides interfaces to manage attachment received from NUGU
 */
interface AttachmentManagerInterface {
    /**
     * Create an reader for attachment
     * @param attachmentId the attachment Id for attachment
     */
    fun createReader(attachmentId: String): Attachment.Reader?
    /**
     * Called when the attachmentManager no longer need to manage attachment.
     * @param attachmentId the attachment Id
     */
    fun removeAttachment(attachmentId: String)
    /**
     * Called when attachment arrived
     * @param attachment the received attachment
     */
    fun onAttachment(attachment: AttachmentMessage)
}