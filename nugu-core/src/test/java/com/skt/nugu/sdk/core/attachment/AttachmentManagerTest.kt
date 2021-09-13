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

package com.skt.nugu.sdk.core.attachment

import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

class AttachmentManagerTest {
    private val attachment_0 = AttachmentMessage(
        ByteBuffer.allocate(1),
        Header("dialogRequestId", "messageId_0","","","",""),
        false,
        "1234",
        0,
        ""
    )

    private val attachment_1 = AttachmentMessage(
        ByteBuffer.allocate(1),
        Header("dialogRequestId", "messageId_1","","","",""),
        false,
        "1234",
        1,
        ""
    )

    private val attachment_2 = AttachmentMessage(
        ByteBuffer.allocate(1),
        Header("dialogRequestId", "messageId_2","","","",""),
        true,
        "1234",
        2,
        ""
    )

    @Test
    fun testCreateReader() {
        val manager = AttachmentManager(1)
        Assert.assertNotNull(manager.createReader("test_id"))
    }

    @Test
    fun testRemoveAttachment() {
        val manager = AttachmentManager(1).also {
            it.onAttachment(attachment_0)
            it.onAttachment(attachment_1)
            it.onAttachment(attachment_2)
            it.removeAttachment(attachment_0.parentMessageId)
        }

        Assert.assertNull(manager.createReader(attachment_0.parentMessageId))
    }

    @Test
    fun testOnAttachment() {
        val manager = AttachmentManager(1).also {
            it.onAttachment(attachment_0)
            it.onAttachment(attachment_1)
            it.onAttachment(attachment_2)
        }

        manager.createReader(attachment_0.parentMessageId)?.let {
            Assert.assertEquals(it.readChunk(), attachment_0.content)
            Assert.assertEquals(it.readChunk(), attachment_1.content)
            Assert.assertEquals(it.readChunk(), attachment_2.content)
        }
    }

    @Test
    fun testOnAttachmentTimeout() {
        val manager = AttachmentManager(0).also {
            it.onAttachment(attachment_0)
            Thread.sleep(5)
            it.onAttachment(attachment_1)
            it.onAttachment(attachment_2)
        }

        Assert.assertNull(manager.createReader(attachment_0.parentMessageId))
    }
}