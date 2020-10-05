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
package com.skt.nugu.sdk.client.port.transport.http2.devicegateway

import com.skt.nugu.sdk.client.port.transport.http2.*
import com.skt.nugu.sdk.client.port.transport.http2.Status
import com.skt.nugu.sdk.client.port.transport.http2.utils.BackOff
import com.skt.nugu.sdk.client.port.transport.http2.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.client.port.transport.http2.utils.ChannelBuilderUtils.Companion.createChannelBuilderWith
import com.skt.nugu.sdk.client.port.transport.http2.utils.MessageRequestConverter.toStringMessage
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.skt.nugu.sdk.core.interfaces.message.Status as SDKStatus

/**
 *  Implementation of DeviceGateway with http2
 **/
internal class DeviceGatewayClient(
    private val policy: Policy,
    private val keepConnection: Boolean,
    private var messageConsumer: MessageConsumer?,
    private var transportObserver: DeviceGatewayTransport.TransportObserver?,
    private val authDelegate: AuthDelegate
) : DeviceGatewayTransport {
    companion object {
        private const val TAG = "HTTP2DeviceGatewayClient"
        private const val maxAsyncCallsSize = 100

        fun create(
            policy: Policy,
            keepConnection: Boolean,
            messageConsumer: MessageConsumer?,
            transportObserver: DeviceGatewayTransport.TransportObserver?,
            authDelegate: AuthDelegate
        ) =
            DeviceGatewayClient(
                policy, keepConnection,
                messageConsumer, transportObserver, authDelegate
            )
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val policies = ConcurrentLinkedQueue(policy.serverPolicy)
    private var backoff : BackOff = BackOff.DEFAULT()

    private var directivesService: DirectivesService? = null
    private var eventsService: EventsService? = null
    private var pingService: PingService? = null
    private var currentPolicy : ServerPolicy? = nextPolicy()
    private var healthCheckPolicy = policy.healthCheckPolicy

    private val isConnected = AtomicBoolean(false)

    private var asyncCalls = Collections.synchronizedMap(object : LinkedHashMap<String, Call>() {
        private val serialVersionUID = 301077066599181567L
        override fun removeEldestEntry(p0: MutableMap.MutableEntry<String, Call>?): Boolean {
            return size > maxAsyncCallsSize
        }
    })
    /**
     * Set a policy.
     * @return the ServerPolicy
     */
    private fun nextPolicy(): ServerPolicy? {
        backoff.reset()
        currentPolicy = policies.poll()
        currentPolicy?.let {
            backoff = BackOff.Builder(maxAttempts = it.retryCountLimit).build()
        }
        return currentPolicy
    }

    lateinit var client: OkHttpClient
    /**
     * Connect to DeviceGateway.
     * @return true is success, otherwise false
     */
    override fun connect(): Boolean {
        if (isConnected.get()) {
            return false
        }

        val policy = currentPolicy ?: run {
            Logger.w(TAG, "[connect] no more policy")
            transportObserver?.onError(
                ChangedReason.UNRECOVERABLE_ERROR
            )
            return false
        }

        policy.apply {
            client = createChannelBuilderWith(this,authDelegate)
            if(keepConnection) {
                directivesService =
                    DirectivesService.create(
                        this,
                        client,
                        this@DeviceGatewayClient
                    )
            }

            pingService =
                PingService.create(
                    this,
                    client,
                    healthCheckPolicy,
                    keepConnection,
                    this@DeviceGatewayClient
                )
            eventsService =
                EventsService.create(
                    this,
                    client,
                    this@DeviceGatewayClient
                )

        }
        return true
    }

    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        directivesService?.shutdown()
        directivesService = null
        eventsService?.shutdown()
        eventsService = null
        pingService?.shutdown()
        pingService = null

        ChannelBuilderUtils.shutdown(executor, client)
        isConnected.set(false)
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    override fun isConnected(): Boolean = isConnected.get()

    override fun isConnectedOrConnecting(): Boolean {
        throw NotImplementedError("not implemented")
    }

    /**
     * Sends an message request.
     * @param request the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun send(call: Call): Boolean {
        val event = eventsService ?: return false

        val request = call.request()
        val result = when(request) {
            is AttachmentMessageRequest -> {
                val result = event.sendAttachmentMessage(request)
                if(result) {
                    call.onComplete(SDKStatus.OK)
                    asyncCalls[request.parentMessageId]?.reschedule()
                }
                result
            }
            is EventMessageRequest -> {
                asyncCalls[request.messageId] = call
                event.sendEventMessage(call)
            }
            is CrashReportMessageRequest ->  true /* Deprecated */
            else -> false
        }
        Logger.d(TAG, "sendMessage : ${request.toStringMessage()}, result : $result")
        return result
    }

    /**
     * Receive an error.
     * @param the status of grpc
     */
    override fun onError(status: Status) {
        Logger.w(TAG, "[onError] Error : ${status.code}")

        when(status.code) {
            Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED -> {
                // nothing to do
            }
            else -> {
                transportObserver?.onReconnecting( when(status.code) {
                    Status.Code.OK -> ChangedReason.SUCCESS
                    Status.Code.UNAVAILABLE -> {
                        var cause = status.cause
                        var reason =
                            if (isConnected.get()) ChangedReason.SERVER_SIDE_DISCONNECT
                            else ChangedReason.CONNECTION_TIMEDOUT
                        while (cause != null) {
                            if (cause is UnknownHostException) {
                                reason = ChangedReason.DNS_TIMEDOUT
                            }  else if(cause is SocketTimeoutException) {
                                reason = ChangedReason.CONNECTION_TIMEDOUT
                            } else if( cause is ConnectException) {
                                reason = ChangedReason.CONNECTION_ERROR
                            }
                            cause = cause.cause
                        }
                        reason
                    }
                    Status.Code.UNKNOWN -> ChangedReason.SERVER_SIDE_DISCONNECT
                    Status.Code.DEADLINE_EXCEEDED -> {
                        if (isConnected.get()) {
                            if(pingService?.isStop() == false) {
                                ChangedReason.PING_TIMEDOUT
                            } else {
                                ChangedReason.REQUEST_TIMEDOUT
                            }
                        }
                        else ChangedReason.CONNECTION_TIMEDOUT
                    }
                    Status.Code.UNIMPLEMENTED -> ChangedReason.FAILURE_PROTOCOL_ERROR
                    Status.Code.NOT_FOUND ,
                    Status.Code.ALREADY_EXISTS ,
                    Status.Code.RESOURCE_EXHAUSTED ,
                    Status.Code.FAILED_PRECONDITION ,
                    Status.Code.ABORTED ,
                    Status.Code.INTERNAL -> ChangedReason.SERVER_INTERNAL_ERROR
                    Status.Code.OUT_OF_RANGE,
                    Status.Code.DATA_LOSS,
                    Status.Code.CANCELLED,
                    Status.Code.INVALID_ARGUMENT -> ChangedReason.INTERNAL_ERROR
                    else -> {
                        throw NotImplementedError()
                    }
                })
            }
        }

        disconnect()
        backoff.awaitRetry(status.code, object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                Logger.w(TAG, "[awaitRetry] Error : $error")

                when (status.code) {
                    Status.Code.PERMISSION_DENIED,
                    Status.Code.UNAUTHENTICATED -> {
                        transportObserver?.onError(ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        nextPolicy()
                        disconnect()
                        connect()
                    }
                }
            }

            override fun onRetry(retriesAttempted: Int) {
                if (isConnected.get()) {
                    Logger.w(TAG, "[awaitRetry] connected")
                } else {
                    disconnect()
                    connect()
                }
            }
        })
    }

    override fun shutdown() {
        Logger.d(TAG, "[shutdown]")
        messageConsumer = null
        transportObserver = null

        disconnect()
        backoff.reset()

        asyncCalls.forEach{
            it.value.cancel()
        }
        asyncCalls.clear()
    }

    /**
     * Connected event received
     * @return boolean value, true if the connection has changed, false otherwise.
     */
    private fun handleConnectedIfNeeded() {
        if(isConnected.compareAndSet(false, true)) {
            backoff.reset()
            transportObserver?.onConnected()
        }
    }

    /**
     * Notification that sending a ping to DeviceGateway has been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged() {
        handleConnectedIfNeeded()
        Logger.d(TAG, "onPingRequestAcknowledged")
    }

    /**
     * Directive received
     * @param directiveMessage
     */
    override fun onReceiveDirectives(directiveMessage: List<DirectiveMessage>) {
        handleConnectedIfNeeded()
        messageConsumer?.consumeDirectives(directiveMessage)
    }

    /**
     * Attachment received
     * @param attachmentMessage
     */
    override fun onReceiveAttachment(attachmentMessage: AttachmentMessage) {
        messageConsumer?.consumeAttachment(attachmentMessage)
    }

    override fun newCall(
        activeTransport: Transport?,
        request: MessageRequest,
        headers: Map<String, String>?,
        listener: MessageSender.OnSendMessageListener
    ): Call {
        throw NotImplementedError()
    }
}