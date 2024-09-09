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

import com.google.common.annotations.VisibleForTesting
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsStreaming
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsUnauthorizedRequestException
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
import kotlin.math.max
import kotlin.system.measureTimeMillis


/**
 * This class is designed to manage upstream of DeviceGateway
 */
internal class EventsService(
    private val channel: ManagedChannel,
    private val observer: DeviceGatewayTransport,
    private val scheduler: ScheduledExecutorService,
    internal val channels: ConcurrentHashMap<String, ClientChannel?> = ConcurrentHashMap()
) {
    companion object {
        private const val TAG = "EventsService"
        val name = EventsService::class.java.simpleName
    }

    @VisibleForTesting
    internal val isShutdown = AtomicBoolean(false)
    private val streamLock = ReentrantLock()

    private fun buildChannel(
        streamId: String,
        call: Call,
        expectedAttachment: Boolean
    ): ClientChannel? {
        if (!isShutdown.get()) {
            val responseObserver = ClientCallStreamObserver(streamId, call, expectedAttachment)
            val newStub = if (call.waitForReady()) {
                VoiceServiceGrpc.newStub(channel).withWaitForReady()
            } else {
                VoiceServiceGrpc.newStub(channel)
            }
            newStub?.events(responseObserver)?.apply {
                return ClientChannel(
                    this, scheduleTimeout(streamId, call.callTimeout()),
                    responseObserver
                )
            }
        }
        return null
    }

    @VisibleForTesting
    internal fun obtainChannel(streamId: String): ClientChannel? {
        if (isShutdown.get()) {
            return null
        }
        return channels[streamId]
    }

    @VisibleForTesting
    internal fun scheduleTimeout(
        streamId: String,
        callTimeout: Long,
        remainingTimeout: Long? = null
    ): ScheduledFuture<*>? {
        return scheduler.schedule({
            channels[streamId]?.apply {
                val currentTimeMillis = System.currentTimeMillis()
                val waitTimeRemaining =
                    responseObserver.getRemainingTimeoutMillis(currentTimeMillis)
                if (waitTimeRemaining >= callTimeout) {
                    this.responseObserver.onError(
                        Status.DEADLINE_EXCEEDED.withDescription(
                            "Client callTimeout(${callTimeout}ms)"
                        ).asException()
                    )
                } else {
                    val downstreamTimeMillis =
                        currentTimeMillis - responseObserver.getLastResponseTimeMillis()
                    val upstreamTimeMillis =
                        currentTimeMillis - responseObserver.getLastRequestTimeMillis()

                    Logger.w(TAG, StringBuilder().apply {
                        append("[scheduleTimeout] Renew the schedule")
                        append("(streamId=$streamId), callTimeout=${callTimeout}ms, downstream=${downstreamTimeMillis}ms, upstream=${upstreamTimeMillis}ms)\n")
                        append("The next timeout is scheduled in ${callTimeout - waitTimeRemaining}ms.")
                    }.toString())

                    scheduleTimeout(streamId, callTimeout, callTimeout - waitTimeRemaining)
                }
            }
        }, remainingTimeout ?: callTimeout, TimeUnit.MILLISECONDS)
    }

    @VisibleForTesting
    fun cancelScheduledTimeout(streamId: String) {
        channels[streamId]?.apply {
            scheduledFuture?.cancel(true)
        }
    }

    inner class ClientCallStreamObserver(
        private val streamId: String,
        private val call: Call,
        private val expectedAttachment: Boolean
    ) : StreamObserver<Downstream> {
        private var startAttachmentTimeMillis = 0L
        private var isSendingAttachmentMessage = false

        fun sendMessage(clientCall: StreamObserver<Upstream>?, build: Upstream) {
            updateLastRequestTimeMillis()
            if (build.hasAttachmentMessage()) {
                isSendingAttachmentMessage = true
            }
            clientCall?.onNext(build)
        }


        fun updateLastRequestTimeMillis() = call.updateLastRequestTimeMillis()
        fun getLastRequestTimeMillis() = call.getLastRequestTimeMillis()
        fun getLastResponseTimeMillis() = call.getLastResponseTimeMillis()
        fun getRemainingTimeoutMillis(currentTimeMillis: Long) =
            currentTimeMillis - max(call.getLastResponseTimeMillis(), call.getLastRequestTimeMillis())

        override fun onNext(downstream: Downstream) {
            call.onStart()

            when (downstream.messageCase) {
                Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                    downstream.directiveMessage?.let {
                        if (it.directivesCount > 0) {
                            val isDispatchNeeded =
                                !call.isCanceled() && !call.isCompleted() && !isShutdown.get()
                            val elapsed = measureTimeMillis {
                                if (isDispatchNeeded) {
                                    observer.onReceiveDirectives(it)
                                }
                            }

                            val log = StringBuilder()
                            log.append("[onNext] directive, requestMessageId=$streamId, ")
                            log.append(
                                it.directivesList.joinToString(
                                    separator = ", ",
                                    prefix = "event="
                                ) { directive ->
                                    "${directive.header.namespace}.${directive.header.name}"
                                })
                            if (!isDispatchNeeded) {
                                log.append(", dispatched=$isDispatchNeeded")
                            }
                            if (elapsed > 100) {
                                log.append(", elapsed=$elapsed")
                            }
                            Logger.d(TAG, log.toString())
                        }
                        if (it.checkIfDirectiveIsUnauthorizedRequestException()) {
                            call.onComplete(SDKStatus.UNAUTHENTICATED)
                            observer.onError(Status.UNAUTHENTICATED, name)
                        }
                        it.checkIfDirectiveIsStreaming { asyncKey ->
                            observer.onAsyncKeyReceived(call, asyncKey)
                        }
                    }
                }

                Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                    downstream.attachmentMessage?.let {
                        if (it.hasAttachment()) {
                            if (it.attachment.seq == 0) {
                                startAttachmentTimeMillis = System.currentTimeMillis()
                                Logger.d(
                                    TAG,
                                    "[onNext] attachment start, seq=${it.attachment.seq}, parentMessageId=${it.attachment.parentMessageId}, requestMessageId={$streamId}"
                                )
                            }
                            val elapsed = measureTimeMillis {
                                if (!call.isCanceled()
                                /** && !call.isCompleted() **/
                                ) {
                                    observer.onReceiveAttachment(it)
                                }
                            }
                            if (it.attachment.isEnd) {
                                val totalElapsed =
                                    System.currentTimeMillis() - startAttachmentTimeMillis
                                Logger.d(
                                    TAG,
                                    "[onNext] attachment end, seq=${it.attachment.seq}, parentMessageId=${it.attachment.parentMessageId}, requestMessageId={$streamId}, elapsed=${totalElapsed}ms"
                                )
                            }

                            if (elapsed > 100) {
                                Logger.w(
                                    TAG,
                                    "[onNext] attachment, operation has been delayed (${elapsed}ms), messageId=${it.attachment.header.messageId} "
                                )
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
                try {
                    if (status.code == Status.Code.DEADLINE_EXCEEDED) {
                        if (expectedAttachment && !isSendingAttachmentMessage) {
                            log.append(", It occurs because the attachment was not sent after [EventMessageRequest::isStreaming == true]")
                            return
                        }
                    }
                } finally {
                    cancelScheduledTimeout(streamId)
                    channels.remove(streamId)
                    observer.onRequestCompleted()
                    Logger.e(TAG, log.toString())
                }
                observer.onError(status, name)
            }
        }

        override fun onCompleted() {
            cancelScheduledTimeout(streamId)
            channels.remove(streamId)
            observer.onRequestCompleted()
            Logger.d(TAG, "[onCompleted] messageId=$streamId, numRequests=${channels.size}")
            call.onComplete(SDKStatus.OK)
        }
    }

    @VisibleForTesting
    fun halfClose(streamId: String) {
        streamLock.withLock {
            try {
                channels[streamId]?.apply {
                    this.clientCall?.onCompleted()
                    this.clientCall = null
                }
            } catch (e: Throwable) {
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
                    this.responseObserver.sendMessage(
                        this.clientCall, Upstream.newBuilder()
                            .setAttachmentMessage(attachment.toProtobufMessage())
                            .build()
                    )
                }
            }

            if (attachment.isEnd) {
                halfClose(attachment.parentMessageId)
            }
        } catch (e: Throwable) {
            // Perhaps, Stream is already completed, no further calls are allowed
            Logger.w(TAG, "[sendAttachmentMessage] Exception : ${e.cause} ${e.message}")
            val status = Status.fromThrowable(e)
            call.onComplete(SDKStatus.fromCode(status.code.value()).apply {
                description = status.description ?: status.cause?.message
                cause = status.cause
            })
            return false
        }
        return true
    }

    fun sendEventMessage(call: Call): Boolean {
        if (isShutdown.get()) {
            Logger.w(TAG, "[sendEventMessage] already shutdown")
            return false
        }
        val event = call.request() as EventMessageRequest
        try {
            val expectedAttachment = event.isStreaming
            streamLock.withLock {
                buildChannel(event.messageId, call, expectedAttachment)?.apply {
                    channels[event.messageId] = this
                    Logger.d(
                        TAG,
                        "[onNext] event=${event.namespace}.${event.name}, messageId=${event.messageId}"
                    )
                    this.responseObserver.sendMessage(
                        this.clientCall, Upstream.newBuilder()
                            .setEventMessage(event.toProtobufMessage())
                            .build()
                    )
                }
            }
            if (!expectedAttachment) {
                halfClose(event.messageId)
            }
        } catch (e: Throwable) {
            // Perhaps, Stream is already completed, no further calls are allowed
            Logger.w(TAG, "[sendEventMessage] Exception : ${e.cause} ${e.message}")
            val status = Status.fromThrowable(e)
            call.onComplete(SDKStatus.fromCode(status.code.value()).apply {
                description = status.description ?: status.cause?.message
                cause = status.cause
            })
            return false
        }
        return true
    }

    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            channels.forEach {
                cancelScheduledTimeout(it.key)
                halfClose(it.key)
            }
            channels.clear()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }

    /**
     * @return The number of streams in the request.
     */
    fun requests() = channels.size
}