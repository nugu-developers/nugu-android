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
        private const val ATTACHMENT_PRESERVE_DURATION = 1000L * 60 * 60L
    }

    private data class AttachmentStatus(
        var readerCreated: Boolean = false,
        var writerCreated: Boolean = false,
        var writerClosed: Boolean = false,
        var receiveEndAttachment: Boolean = false,
        var isLocked: Boolean = false,
        var onTimeout: (() -> Unit)? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val lock: Lock = ReentrantLock()
    private val attachments = HashMap<String, Attachment>()
    private val attachmentsStatus = HashMap<String, AttachmentStatus>()
    private val attachmentTimeoutFutureMap = HashMap<String, Future<*>>()
    private val scheduleExecutor = ScheduledThreadPoolExecutor(1)

    override fun createReader(attachmentId: String): Attachment.Reader? {
        lock.withLock {
            val status = attachmentsStatus[attachmentId]
            return if (status != null) {
                val reader = attachments[attachmentId]?.createReader()
                if (reader != null) {
                    status.readerCreated = true

//                    if(status.writerClosed) {
//                        // remove attachment if writer closed.
//                        attachments.remove(attachmentId)
//                    }
                }

                Logger.d(
                    TAG,
                    "[createReader] attachmentId: $attachmentId, status: $status, created reader: $reader"
                )
                reader
            } else {
                val attachment = StreamAttachment(attachmentId)
                val reader = attachment.createReader()
                attachments[attachmentId] = attachment
                val newStatus = AttachmentStatus(readerCreated = true)
                attachmentsStatus[attachmentId] = newStatus

                val timeoutFuture = attachmentTimeoutFutureMap[attachmentId]
                if(timeoutFuture == null) {
                    newStatus.onTimeout = {
                        Logger.d(TAG, "[createReader] attachment timeout: $attachmentId")
                        lock.withLock {
                            attachment.createWriter().close(true)
                            newStatus.writerCreated = true
                            newStatus.writerClosed = true
                            removeAttachment(attachmentId)
                        }
                    }
                    scheduleTimeoutLocked(attachmentId)
                }

                Logger.d(
                    TAG,
                    "[createReader] New Attachment Created - attachmentId: $attachmentId, status: $status, created reader: $reader"
                )
                reader
            }
        }
    }

    private fun scheduleTimeoutLocked(attachmentId: String) {
        val status = attachmentsStatus[attachmentId] ?: return
        attachmentTimeoutFutureMap[attachmentId] = scheduleExecutor.schedule({
            if (status.isLocked) return@schedule
            status.onTimeout?.invoke()
        }, timeoutInSeconds, TimeUnit.SECONDS)
    }

    override fun removeAttachment(attachmentId: String) {
        lock.withLock {
            val attachmentRemoved = attachments.remove(attachmentId)
            Logger.d(
                TAG,
                "[removeAttachment] attachmentId: $attachmentId, attachmentRemoved: $attachmentRemoved"
            )
            val removedStatus = removeStatusIfNoMoreNeedLocked(attachmentId)

            trimTimeoutAttachmentLocked()

            if (attachmentRemoved != null || removedStatus != null) {
                Logger.d(
                    TAG,
                    "[removeAttachment] attachment size: ${attachments.size}, status size: ${attachmentsStatus.size}"
                )
            }
        }
    }

    private fun removeStatusIfNoMoreNeedLocked(attachmentId: String): AttachmentStatus? {
        val status = attachmentsStatus[attachmentId]

        val removed = if (status != null) {
            if (status.readerCreated && status.receiveEndAttachment) {
                attachmentsStatus.remove(attachmentId)
            } else {
                null
            }
        } else {
            null
        }

        if(removed != null) {
            Logger.d(
                TAG,
                "[removeStatusIfNoMoreNeedLocked] removed - id: $attachmentId, status: $status"
            )
        } else {
            Logger.d(
                TAG,
                "[removeStatusIfNoMoreNeedLocked] not removed - id: $attachmentId, status: $status"
            )
        }
        return removed
    }

    override fun onAttachment(attachment: AttachmentMessage) {
        val attachmentId = attachment.parentMessageId
        lock.withLock {
            attachmentTimeoutFutureMap.remove(attachmentId)?.cancel(true)

            var status = attachmentsStatus[attachmentId]
            val writer = if (status != null) {
                val attachmentStream = attachments[attachmentId]
                if (attachmentStream != null && !status.writerClosed) {
                    attachmentStream.createWriter()
                } else {
                    null
                }
            } else {
                val attachmentStream = StreamAttachment(attachmentId)
                attachments[attachmentId] = attachmentStream
                status = AttachmentStatus(writerCreated = true)
                attachmentsStatus[attachmentId] = status
                Logger.d(TAG, "[onAttachment] New Attachment Created- attachmentId: $attachmentId")
                attachmentStream.createWriter()
            }

            if (writer != null) {
                writer.write(attachment.content)
                if (attachment.isEnd) {
                    writer.close()
                } else {
                    if(!status.isLocked) {
                        status.onTimeout = {
                            Logger.d(TAG, "[onAttachment] attachment timeout: $attachmentId")
                            lock.withLock {
                                writer.close(true)
                                status.writerClosed = true
                                removeAttachment(attachmentId)
                            }
                        }
                        scheduleTimeoutLocked(attachmentId)
                    }
                }
            }

            if (attachment.isEnd) {
                Logger.d(TAG, "[onAttachment] receive end attachment: $attachmentId")
                status.writerClosed = true
                status.receiveEndAttachment = true
                tryRemoveAttachmentAndStatusLocked(attachmentId)
            }
        }
    }

    override fun ensureAttachmentPreservation(attachmentId: String, ensure: Boolean) {
        lock.withLock {
            Logger.d(TAG, "[ensureAttachmentPreservation] attachmentId: $attachmentId, ensure: $ensure")
            if(ensure) {
                var status = attachmentsStatus[attachmentId]
                if (status == null) {
                    status = AttachmentStatus(isLocked = true)
                    attachmentsStatus[attachmentId] = status
                } else {
                    status.isLocked = true
                }

                attachmentTimeoutFutureMap.remove(attachmentId)?.cancel(true)
            } else {
                val status = attachmentsStatus[attachmentId] ?: return@withLock
                status.isLocked = false
                scheduleTimeoutLocked(attachmentId)
            }
        }
    }

    private fun tryRemoveAttachmentAndStatusLocked(attachmentId: String) {
        val removedStatus = removeStatusIfNoMoreNeedLocked(attachmentId)
        val removedAttachment = if (removedStatus != null) {
            attachments.remove(attachmentId)
        } else {
            null
        }
        trimTimeoutAttachmentLocked()

        Logger.d(
            TAG,
            "[tryRemoveAttachmentAndStatusLocked] attachmentId: $attachmentId, removedAttachment: $removedAttachment, removedStatus: $removedStatus"
        )
        if (removedStatus != null || removedAttachment != null) {
            Logger.d(
                TAG,
                "[tryRemoveAttachmentAndStatusLocked] attachment size: ${attachments.size}, status size: ${attachmentsStatus.size}"
            )
        }
    }

    private fun trimTimeoutAttachmentLocked() {
        val current = System.currentTimeMillis()
        val removeTargets = attachmentsStatus.filter {
            current - it.value.createdAt > ATTACHMENT_PRESERVE_DURATION
        }
        removeTargets.forEach {
            attachmentsStatus.remove(it.key)
            attachments.remove(it.key)
        }
        Logger.d(TAG, "[trimTimeoutStatusLocked] removeTargets: $removeTargets")
    }
}
