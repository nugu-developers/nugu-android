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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.skt.nugu.sdk.client.port.transport.grpc.utils.DirectivePreconditions.checkIfDirectiveIsUnauthorizedRequestException
import com.skt.nugu.sdk.client.port.transport.grpc.utils.GsonUtils
import com.skt.nugu.sdk.client.port.transport.grpc.utils.MessageRequestConverter.toProtobufMessage
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * This class is designed to manage upstream of DeviceGateway
 */
internal class EventsService(
    private val channel: ManagedChannel,
    private val observer: DeviceGatewayTransport
) {
    companion object {
        private const val TAG = "EventsService"
        private const val defaultTimeout: Long = 1000 * 14L
    }

    private val isShutdown = AtomicBoolean(false)
    private var activeUpstream: StreamObserver<Upstream>? = null
    private val streamLock = ReentrantLock()

    private fun obtainUpstream(): StreamObserver<Upstream>? {
        if (isShutdown.get()) {
            return null
        }

        return run {
            if (activeUpstream != null) {
                return activeUpstream
            }
            VoiceServiceGrpc.newStub(channel)
                .withDeadlineAfter(defaultTimeout, TimeUnit.MILLISECONDS)
                .withWaitForReady()?.events(responseObserver).apply {
                    activeUpstream = this
                }
        }
    }

    private val responseObserver = object : StreamObserver<Downstream> {
        override fun onNext(downstream: Downstream) {
            Logger.d(TAG, "[EventsService] onNext : ${downstream.messageCase}")
            when (downstream.messageCase) {
                Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                    downstream.directiveMessage?.let {
                        if (it.directivesCount > 0) {
                            observer.onReceiveDirectives(downstream.directiveMessage)
                        }
                        if (it.checkIfDirectiveIsUnauthorizedRequestException()) {
                            observer.onError(Status.UNAUTHENTICATED)
                        }
                    }
                }
                Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                    downstream.attachmentMessage?.let {
                        if (it.hasAttachment()) {
                            observer.onReceiveAttachment(downstream.attachmentMessage)
                        }
                    }
                }
                else -> {
                    // nothing
                }
            }
        }

        override fun onError(t: Throwable) {
            if (!isShutdown.get()) {
                val status = Status.fromThrowable(t)
                Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
                if(status.code == Status.Code.DEADLINE_EXCEEDED) {
                    // // // // 
                }
                halfClose()
                observer.onError(status)
            }
        }

        override fun onCompleted() {
            Logger.d(TAG, "[onCompleted] Stream is completed")
            halfClose()
        }
    }

    private fun halfClose() {
        streamLock.withLock {
            try {
                activeUpstream?.onCompleted()
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "[close] Exception : ${e.cause} ${e.message}")
            } finally {
                activeUpstream = null
            }
        }
    }

    fun sendAttachmentMessage(attachment: AttachmentMessageRequest): Boolean {
        if (isShutdown.get()) {
            Logger.w(TAG, "[sendAttachmentMessage] already shutdown")
            return false
        }
        try {
            streamLock.withLock {
                obtainUpstream()?.apply {
                    onNext(
                        Upstream.newBuilder()
                            .setAttachmentMessage(attachment.toProtobufMessage())
                            .build()
                    )
                }
            }
        } catch (e: IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            Logger.w(TAG, "[sendAttachmentMessage] Exception : ${e.cause} ${e.message}")
            return false
        }
        return true
    }

    fun sendEventMessage(event: EventMessageRequest): Boolean {
        if (isShutdown.get()) {
            Logger.w(TAG, "[sendEventMessage] already shutdown")
            return false
        }

        try {
            streamLock.withLock {
                obtainUpstream()?.apply {
                    onNext(
                        Upstream.newBuilder()
                            .setEventMessage(event.toProtobufMessage())
                            .build()
                    )
                }
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
            halfClose()
        } else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}