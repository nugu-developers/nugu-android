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

package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.ByteBuffer

class MessageDispatcherTest {
    @Test
    fun testReceiveDirectives() {
        val directiveGroupHandler: DirectiveGroupHandler = mock()
        val attachmentManager: AttachmentManagerInterface = mock()
        val dispatcher = MessageDispatcher(directiveGroupHandler, attachmentManager)

        val directives = arrayListOf(
            DirectiveMessage(
                Header(
                    "dialogRequestId",
                    "messageId",
                    "name",
                    "namespace",
                    "1.0",
                    ""
                ), "{}"
            )
        )
        dispatcher.receiveDirectives(directives)

        verify(directiveGroupHandler).onReceiveDirectives(eq(directives.map {
            Directive(attachmentManager, it.header, it.payload)
        }))
    }

    @Test
    fun testReceiveAttachment() {
        val attachmentManager: AttachmentManagerInterface = mock()
        val dispatcher = MessageDispatcher(mock(), attachmentManager)

        val attachmentMessage = AttachmentMessage(
            ByteBuffer.allocate(0) ,Header(
            "dialogRequestId",
            "messageId",
            "name",
            "namespace",
            "1.0",
            ""
        ), false, "", 1, "mediaType")

        dispatcher.receiveAttachment(attachmentMessage)

        verify(attachmentManager).onAttachment(eq(attachmentMessage))
    }
}