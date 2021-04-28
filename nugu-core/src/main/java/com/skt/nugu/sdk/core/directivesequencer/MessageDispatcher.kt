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
package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.utils.Logger


/**
 * Class that dispatch messages.
 * Directives will be passed to [DirectiveGroupProcessor],
 * Attachments will be handled by [AttachmentManagerInterface].
 */
class MessageDispatcher(
    private val directiveGroupHandler: DirectiveGroupHandler,
    private val attachmentManager: AttachmentManagerInterface
) : MessageObserver {
    companion object {
        private const val TAG = "MessageDispatcher"
    }

    override fun receiveDirectives(directives: List<DirectiveMessage>) {
        Logger.d(TAG, "[receiveDirectives] $directives")
        val directiveList = ArrayList<Directive>()

        directives.forEach {
            directiveList.add(createDirective(attachmentManager, it))
        }

        directiveGroupHandler.onReceiveDirectives(directiveList)
    }

    override fun receiveAttachment(attachment: AttachmentMessage) {
        Logger.d(TAG, "[receiveAttachment] ${attachment.header}, content: ${attachment.content.capacity()}, seq: ${attachment.seq}, isEnd: ${attachment.isEnd}, mediaType: ${attachment.mediaType}, parentMessageId: ${attachment.parentMessageId}")
        attachmentManager.onAttachment(attachment)
    }

    private fun createDirective(attachmentManager: AttachmentManagerInterface?, directive: DirectiveMessage): Directive = Directive(attachmentManager, directive.header, directive.payload)
}