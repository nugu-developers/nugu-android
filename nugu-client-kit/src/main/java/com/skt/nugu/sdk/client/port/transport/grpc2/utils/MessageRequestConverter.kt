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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.google.protobuf.unsafeWrap
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import devicegateway.grpc.*

object MessageRequestConverter {
    fun AttachmentMessageRequest.toProtobufMessage(): AttachmentMessage {
        with(this) {
            val attachment = Attachment.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .setReferrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
                .setParentMessageId(parentMessageId)
                .setSeq(seq)
                .setIsEnd(isEnd)
                .setMediaType(mediaType)
                .setContent(byteArray.unsafeWrap())
                .build()

            return AttachmentMessage.newBuilder()
                .setAttachment(attachment).build()
        }
    }

    fun EventMessageRequest.toProtobufMessage(): EventMessage {
        with(this) {
            val event = Event.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .setReferrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
                .setPayload(payload)
                .build()

            return EventMessage.newBuilder()
                .setContext(context)
                .setEvent(event)
                .build()
        }
    }

    fun MessageRequest.toStringMessage() : String {
        return when (this) {
            is AttachmentMessageRequest -> {
                StringBuilder().apply {
                    append(this.javaClass.simpleName)
                    append("(")
                    append("messageId=${messageId}")
                    append(",dialogRequestId=${dialogRequestId}")
                    append(",context=${context}")
                    append(",namespace=${namespace}")
                    append(",name=${name}")
                    append(",version=${version}")
                    append(",referrerDialogRequestId=${referrerDialogRequestId}")
                    append(",seq=${seq}")
                    append(",isEnd=${isEnd}")
                    append(",parentMessageId=${parentMessageId}")
                    append(",mediaType=${mediaType}")
                    append(",content size=${byteArray?.size}")
                    append(")")
                }.toString()
            }
            else -> this.toString()
        }
    }

    fun DirectiveMessage.toDirectives(): List<com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage> {
        val directives = ArrayList<com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage>()

        this.directivesList.forEach {
            directives.add(com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage(
                convertHeader(
                    it.header
                ), it.payload))
        }

        return directives
    }

    fun AttachmentMessage.toAttachmentMessage(): com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage {
        return with(this.attachment) {
            com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage(
                content.asReadOnlyByteBuffer(),
                convertHeader(
                    header
                ),
                isEnd,
                parentMessageId,
                seq,
                mediaType
            )
        }
    }

    private fun convertHeader(header: Header): com.skt.nugu.sdk.core.interfaces.message.Header = with(header) {
        com.skt.nugu.sdk.core.interfaces.message.Header(
            dialogRequestId,
            messageId,
            name,
            namespace,
            version,
            referrerDialogRequestId
        )
    }
}