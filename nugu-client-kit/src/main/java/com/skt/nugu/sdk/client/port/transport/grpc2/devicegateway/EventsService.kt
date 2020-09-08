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
package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsUnauthorizedRequestException
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfEventMessageIsAsrRecognize
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toProtobufMessage
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.Downstream
import devicegateway.grpc.Upstream
import devicegateway.grpc.VoiceServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.skt.nugu.sdk.core.interfaces.message.Status as SDKStatus
import java.util.concurrent.ScheduledExecutorService


/**
 * This class is designed to manage upstream of DeviceGateway
 */
internal class EventsService(
    private val channel: ManagedChannel,
    private val observer: DeviceGatewayTransport,
    private val scheduler: ScheduledExecutorService
) {
    companion object {
        private const val TAG = "EventsService"
        private const val defaultTimeout: Long = 1000 * 10L
    }

    private val isShutdown = AtomicBoolean(false)
    private val streamLock = ReentrantLock()

    private val requestStreamMap = ConcurrentHashMap<String, ClientChannel?>()

    internal data class ClientChannel (
        val clientCall : StreamObserver<Upstream>,
        var scheduledFuture: ScheduledFuture<*>?,
        val responseObserver : ClientCallStreamObserver
    )
    private fun buildChannel(streamId: String, call: Call, expectedAttachment: Boolean): ClientChannel? {
        if (!isShutdown.get()) {
            val responseObserver = ClientCallStreamObserver(streamId, call, expectedAttachment)
            VoiceServiceGrpc.newStub(channel).withWaitForReady()?.events(responseObserver)?.apply {
                return ClientChannel(
                    this, scheduleTimeout(streamId, call),
                    responseObserver
                )
            }
        }
        return null
    }
    private fun obtainChannel(streamId: String): ClientChannel? {
        if (isShutdown.get()) {
            return null
        }
        return requestStreamMap[streamId]
    }

    private fun scheduleTimeout(streamId: String, call: Call) : ScheduledFuture<*>? {
        return scheduler.schedule({
            requestStreamMap[streamId]?.apply {
                if(this.responseObserver.isReceivedDownstream.compareAndSet(true, false)) {
                    Logger.w(TAG,"[scheduleTimeout] Renew the schedule, It occurs because the downstream is too slow. $streamId")
                    scheduleTimeout(streamId, call)
                } else {
                    this.responseObserver.onError(
                        Status.DEADLINE_EXCEEDED.withDescription(
                            "Client callTimeout(${call.callTimeout()}ms)"
                        ).asException())
                }
            }
        }, call.callTimeout(), TimeUnit.MILLISECONDS)
    }

    private fun cancelScheduledTimeout(streamId: String) {
        requestStreamMap[streamId]?.apply {
            scheduledFuture?.cancel(true)
        }
    }

    inner class ClientCallStreamObserver(val streamId: String, val call: Call, val expectedAttachment: Boolean) : StreamObserver<Downstream> {
        private var startAttachmentTimeMillis = 0L
        var isReceivedDownstream = AtomicBoolean(false)
        var isSendingAttachmentMessage = false

        override fun onNext(downstream: Downstream) {
            isReceivedDownstream.set(true)
            call.onStart()

            when (downstream.messageCase) {
                Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                    downstream.directiveMessage?.let {
                        if (it.directivesCount > 0) {
                            val log = StringBuilder()
                            val beginTimeStamp = System.currentTimeMillis()
                            if(!call.isCanceled()) {
                                observer.onReceiveDirectives(it)
                            }
                            log.append("[onNext] directive, messageId=")
                            val elapsed = System.currentTimeMillis() - beginTimeStamp
                            it.directivesList.forEach {
                                log.append(it.header.messageId)
                                log.append(", ")
                            }
                            if(elapsed  > 100) {
                                log.append("elapsed=$elapsed")
                            }
                            Logger.d(TAG, log.toString())
                        }
                        if (it.checkIfDirectiveIsUnauthorizedRequestException()) {
                            call.onComplete(SDKStatus.UNAUTHENTICATED)
                            observer.onError(Status.UNAUTHENTICATED)
                        }
                    }
                }
                Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                    downstream.attachmentMessage?.let {
                        if (it.hasAttachment()) {
                            val currentTimeMillis =  System.currentTimeMillis()
                            if (it.attachment.seq == 0) {
                                startAttachmentTimeMillis = currentTimeMillis
                                Logger.d(TAG, "[onNext] attachment start, seq=${it.attachment.seq}, parentMessageId=${it.attachment.parentMessageId}")
                            }
                            if (it.attachment.isEnd) {
                                val elapsed = currentTimeMillis - startAttachmentTimeMillis
                                Logger.d(TAG, "[onNext] attachment end, seq=${it.attachment.seq}, parentMessageId=${it.attachment.parentMessageId}, elapsed=${elapsed}ms")
                            }
                            if(!call.isCanceled()) {
                                observer.onReceiveAttachment(it)
                            }

                            val dispatchTimestamp = currentTimeMillis - System.currentTimeMillis()
                            if(dispatchTimestamp  > 100) {
                                Logger.w(TAG, "[onNext] attachment, operation has been delayed (${dispatchTimestamp}ms), messageId=${it.attachment.header.messageId} ")
                            }
                        }
                    }
                }
                else -> {
                    Logger.e(TAG, "[onNext] unknown messageCase : ${downstream.messageCase}")
                }
            }
        }

        override fun onError(t: Throwable) {
            val status = Status.fromThrowable(t)
            call.onComplete(SDKStatus.fromCode(status.code.value()).apply {
                description = status.description
            })

            if (!isShutdown.get()) {
                val log = StringBuilder()
                log.append("[onError] ${status.code}, ${status.description}, $streamId")
                if(status.code == Status.Code.DEADLINE_EXCEEDED) {
                    if(expectedAttachment && !isSendingAttachmentMessage) {
                        log.append(", It occurs because the attachment was not sent after Asr.Recognize.")
                    }
                }
                Logger.e(TAG, log.toString())
                observer.onError(status)
            }
        }

        override fun onCompleted() {
            cancelScheduledTimeout(streamId)
            requestStreamMap.remove(streamId)
            Logger.d(TAG, "[onCompleted] messageId=$streamId, numRequests=${requestStreamMap.size}")
            call.onComplete(SDKStatus.OK)
        }
    }

    private fun halfClose(streamId : String) {
        streamLock.withLock {
            try {
                requestStreamMap[streamId]?.apply {
                    this.clientCall.onCompleted()
                }
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "[close] cause:${e.cause}, message:${e.message}")
            }
        }
    }

    fun sendAttachmentMessage(call: Call): Boolean {
        if (isShutdown.get()) {
            Logger.w(TAG, "[sendAttachmentMessage] already shutdown")
            return false
        }
        val attachment = call.request() as AttachmentMessageRequest

        try {
            streamLock.withLock {
                obtainChannel(attachment.parentMessageId)?.apply {
                    this.responseObserver.isSendingAttachmentMessage = true
                    cancelScheduledTimeout(attachment.parentMessageId)
                    this.scheduledFuture = scheduleTimeout(attachment.parentMessageId, call)
                    this.clientCall.onNext(
                        Upstream.newBuilder()
                            .setAttachmentMessage(attachment.toProtobufMessage())
                            .build()
                    )
                }
            }

            if(attachment.isEnd) {
                halfClose(attachment.parentMessageId)
            }
        } catch (e: IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            Logger.w(TAG, "[sendAttachmentMessage] Exception : ${e.cause} ${e.message}")
            return false
        }
        return true
    }

    fun sendEventMessage(call: Call) : Boolean {
        if (isShutdown.get()) {
            Logger.w(TAG, "[sendEventMessage] already shutdown")
            return false
        }
        val event = call.request() as EventMessageRequest
        try {
            val expectedAttachment = event.checkIfEventMessageIsAsrRecognize()
            streamLock.withLock {
                buildChannel(event.messageId, call, expectedAttachment)?.apply {
                    requestStreamMap[event.messageId] = this
                    Logger.d(TAG, "[onNext] event=${event.namespace}.${event.name}, messageId=${event.messageId}")
                    this.clientCall.onNext(
                        Upstream.newBuilder()
                            .setEventMessage(event.toProtobufMessage())
                            .build()
                    )
                }
            }
            if(!expectedAttachment) {
                halfClose(event.messageId)
            }
        } catch (e: IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            Logger.w(TAG, "[sendEventMessage] Exception : ${e.cause} ${e.message}")
            return false
        }
        return true
    }

    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            requestStreamMap.forEach {
                cancelScheduledTimeout(it.key)
                halfClose(it.key)
            }
            requestStreamMap.clear()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}