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

import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsStreaming
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsUnauthorizedRequestException
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.DirectivesRequest
import devicegateway.grpc.Downstream
import devicegateway.grpc.VoiceServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is designed to manage downstream of DeviceGateway
 */
internal class DirectivesService(
    private val streamCalls: ConcurrentHashMap<String, Call>,
    private val channel: ManagedChannel,
    private val observer: DeviceGatewayTransport
) {
    companion object {
        private const val TAG = "DirectivesService"
        val name = DirectivesService::class.java.simpleName
    }

    private val isShutdown = AtomicBoolean(false)

    private val deadlineCancellationExecutor: ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }
    private var deadlineCancellationFuture : ScheduledFuture<*>? = null

    private val isStarted = AtomicBoolean(false)

    private fun cancelDeadlineTimer() {
        if(deadlineCancellationFuture?.isCancelled == false) {
            deadlineCancellationFuture?.cancel(true)
            deadlineCancellationFuture = null
        }
    }

    private fun startDeadlineTimer() {
        deadlineCancellationFuture = deadlineCancellationExecutor.schedule( {
            if (!isShutdown.get()) {
                observer.onError(Status.DEADLINE_EXCEEDED, name)
            }
        }, 10, TimeUnit.SECONDS)
    }


    fun start() {
        if(!isStarted.compareAndSet(false, true)) {
            Logger.d(TAG, "[start] skip already started")
            return
        }
        Logger.d(TAG, "[start] directives called.")
        startDeadlineTimer()
        VoiceServiceGrpc.newStub(channel).withWaitForReady().directives(
                DirectivesRequest.newBuilder().build(),
                object : StreamObserver<Downstream> {
                    override fun onNext(downstream: Downstream) {
                        Logger.d(TAG, "[DirectivesService] onNext : ${downstream.messageCase}")
                        cancelDeadlineTimer()
                        when (downstream.messageCase) {
                            Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                                downstream.directiveMessage?.let {
                                    if (it.directivesCount > 0) {
                                        if(!isShutdown.get()) {

                                            val log = StringBuilder()
                                            log.append("[DirectivesService] ")
                                            log.append(it.directivesList.joinToString(separator = ", ", prefix = "event=") { directive ->
                                                "${directive.header.namespace}.${directive.header.name}"
                                            })
                                            Logger.d(TAG, log.toString())
                                            observer.onReceiveDirectives(downstream.directiveMessage)
                                        } else {
                                            Logger.w(TAG, "[DirectivesService] This message is not dispatched (${downstream.directiveMessage})")
                                        }
                                    }
                                    if (it.checkIfDirectiveIsUnauthorizedRequestException()) {
                                        observer.onError(Status.UNAUTHENTICATED, name)
                                    }
                                    it.checkIfDirectiveIsStreaming { asyncKey ->
                                        streamCalls[asyncKey.eventDialogRequestId]?.let { call ->
                                            observer.onAsyncKeyReceived(call, asyncKey)
                                        }
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
                        Logger.w(TAG, "[onError]", t)
                        if (!isShutdown.get()) {
                            isStarted.set(false)
                            cancelDeadlineTimer()
                            val status = Status.fromThrowable(t)
                            Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
                            observer.onError(status, name)
                        }
                    }

                    override fun onCompleted() {
                        if (!isShutdown.get()) {
                            isStarted.set(false)
                            cancelDeadlineTimer()
                            Logger.d(TAG, "[onCompleted] Stream is completed")
                            observer.onError(Status.UNKNOWN, name)
                        }
                    }
                })
    }

    fun shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            Logger.w(TAG, "[shutdown] already shutdown")
        } else {
            deadlineCancellationFuture?.cancel(true)
            deadlineCancellationExecutor.shutdown()
        }
    }
}
