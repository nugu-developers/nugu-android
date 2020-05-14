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
package com.skt.nugu.sdk.core.attachment

import com.skt.nugu.sdk.core.interfaces.attachment.Attachment
import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AttachmentManager(private val timeoutInSeconds: Long = 10) : AttachmentManagerInterface {
    companion object {
        private const val TAG = "AttachmentManager"
    }

    private val lock: Lock = ReentrantLock()
    private val timeoutAttachments = LinkedHashSet<String>()
    private val attachmentsMap = HashMap<String, Attachment>()
    private val attachmentTimeoutFutureMap = HashMap<String, Future<*>>()
    private val scheduleExecutor = ScheduledThreadPoolExecutor(1)

    private fun createWriter(attachmentId: String): Attachment.Writer? {
        lock.withLock {
            return getAttachmentLocked(attachmentId)?.createWriter()
        }
    }

    override fun createReader(attachmentId: String): Attachment.Reader? {
        lock.withLock {
            return getAttachmentLocked(attachmentId)?.createReader()
        }
    }

    private fun getAttachmentLocked(attachmentId: String): Attachment? {
        if(timeoutAttachments.contains(attachmentId)) {
            return null
        }

        var attachment = attachmentsMap[attachmentId]

        if (attachment == null) {
            attachment = StreamAttachment(attachmentId)
            attachmentsMap[attachmentId] = attachment
        }

        return attachment
    }

    override fun removeAttachmentIfConsumed(attachmentId: String) {
        val attachment = attachmentsMap[attachmentId]
        if (attachment != null) {
            if (attachment.hasCreatedReader()) {
                Logger.d(TAG, "[removeAttachmentIfConsumed] removed : $attachmentId")
                attachmentsMap.remove(attachmentId)
            }
        }
    }

    override fun onAttachment(attachment: AttachmentMessage) {
        with(attachment) {
            attachmentTimeoutFutureMap.remove(parentMessageId)?.cancel(true)

            val writer = createWriter(parentMessageId)
            if(writer == null) {
                Logger.d(TAG, "[onAttachment] receive timeout attachment: $parentMessageId")
                return@with
            }

            writer.write(content)
            if (isEnd) {
                Logger.d(TAG, "[onAttachment] receive end attachment: $parentMessageId")
                writer.close()
                removeAttachmentIfConsumed(parentMessageId)
            } else {
                attachmentTimeoutFutureMap[parentMessageId] = scheduleExecutor.schedule({
                    Logger.d(TAG, "[onAttachment] attachment timeout: $parentMessageId")
                    lock.withLock {
                        timeoutAttachments.add(parentMessageId)
                        if(timeoutAttachments.size > 50) {
                            timeoutAttachments.remove(timeoutAttachments.first())
                        }
                    }
                    writer.close()
                    removeAttachmentIfConsumed(parentMessageId)
                }, timeoutInSeconds, TimeUnit.SECONDS)
            }
        }
    }
}