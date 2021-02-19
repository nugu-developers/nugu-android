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
package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.skt.nugu.sdk.client.port.transport.grpc2.HeaderClientInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc2.Policy
import com.skt.nugu.sdk.client.port.transport.grpc2.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.BackOff
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toAttachmentMessage
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toDirectives
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.MessageRequestConverter.toStringMessage
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.CallOptions
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.AttachmentMessage
import devicegateway.grpc.DirectiveMessage
import io.grpc.ManagedChannel
import io.grpc.Status
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLHandshakeException

/**
 *  Implementation of DeviceGateway
 **/
internal class DeviceGatewayClient(policy: Policy,
                                   private val keepConnection : Boolean,
                                   private var messageConsumer: MessageConsumer?,
                                   private var transportDelegate: DeviceGatewayTransport.TransportDelegate?,
                                   private var transportObserver: DeviceGatewayTransport.TransportObserver?,
                                   private val authDelegate: AuthDelegate,
                                   private val callOptions: CallOptions?,
                                   var isHandOff: Boolean)
    :
    DeviceGatewayTransport, HeaderClientInterceptor.Delegate {
    companion object {
        private const val TAG = "DeviceGatewayClient"
    }

    private val policies = ConcurrentLinkedQueue(policy.serverPolicy)
    private var backoff : BackOff = BackOff.DEFAULT()

    private var currentChannel: ManagedChannel? = null

    private var pingService: PingService? = null
    private var eventsService: EventsService? = null
    private var directivesService: DirectivesService? = null

    private var currentPolicy : ServerPolicy? = nextPolicy()
    private var healthCheckPolicy = policy.healthCheckPolicy

    private val isConnected = AtomicBoolean(false)

    private val scheduler  = Executors.newSingleThreadScheduledExecutor()

    private var eventMessageHeaders: Map<String, String>? = null
    /**
     * Set a policy.
     * @return the ServerPolicy
     */
    private fun nextPolicy(): ServerPolicy? {
        Logger.d(TAG, "[nextPolicy]")
        backoff.reset()
        currentPolicy = policies.poll()
        currentPolicy?.let {
            backoff = BackOff.Builder(maxAttempts = it.retryCountLimit).build()
        }
        return currentPolicy
    }

    private fun createChannel(policy: ServerPolicy?, onError: ((Throwable) -> Unit)? = null) : Boolean {
        synchronized(this) {
            if(policy == null) {
                onError?.invoke(Throwable("no more policy"))
                return false
            }

            val channel = try {
                ChannelBuilderUtils.createChannelBuilderWith(
                    policy,
                    authDelegate,
                    this@DeviceGatewayClient
                ).build()
            } catch (th: Throwable) {
                onError?.invoke(th)
                return false
            }

            channel.apply {
                eventsService =
                    EventsService(
                        this,
                        this@DeviceGatewayClient,
                        scheduler,
                        callOptions
                    )
                if (keepConnection) {
                    directivesService =
                        DirectivesService(
                            this,
                            this@DeviceGatewayClient
                        )
                    pingService =
                        PingService(
                            this,
                            healthCheckPolicy,
                            this@DeviceGatewayClient
                        )
                }
                currentChannel = this
            }
        }
        return true
    }
    /**
     * Connect to DeviceGateway.
     * @return true is success, otherwise false
     */
    override fun connect(): Boolean {
        Logger.d(TAG, "[connect] isConnected = ${isConnected()}, keepConnection = $keepConnection")

        if (isConnected()) {
            Logger.w(TAG, "[connect] already connected")
            return false
        }
        if (!keepConnection) {
            handleConnectedIfNeeded()
            return true
        }

        val policy = currentPolicy ?: run {
            Logger.w(TAG, "[connect] no more policy")
            val reason = ChangedReason.UNRECOVERABLE_ERROR
            reason.cause = Throwable("no more policy")
            transportObserver?.onError(reason)
            return false
        }

        return createChannel(policy) {
            Logger.w(TAG, "[connect] Can't create a new channel. Exception: $it")
            val reason = ChangedReason.CONNECTION_ERROR
            reason.cause = it
            transportObserver?.onError(reason)
        }
    }

    private fun stopServerInitiated() {
        if(!keepConnection) {
            return
        }
        synchronized(this) {
            pingService?.shutdown()
            pingService = null
            directivesService?.shutdown()
            directivesService = null
        }
    }
    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        Logger.d(TAG, "[disconnect]")
        stopServerInitiated()

        synchronized(this) {
            eventsService?.shutdown()
            eventsService = null
            ChannelBuilderUtils.shutdown(currentChannel)
            currentChannel = null
            eventMessageHeaders = null
            isConnected.set(false)
        }
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    override fun isConnected(): Boolean = isConnected.get()

    override fun isConnectedOrConnecting(): Boolean {
        throw NotImplementedError("not implemented")
    }

    /**
     * Sends a message request.
     * @param request the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun send(call: Call): Boolean {
        if(!keepConnection && currentChannel == null) {
            createChannel(transportDelegate?.newServerPolicy()) {
                Logger.w(TAG, "[send] Can't create a new channel. Exception: $it")
            }
        }

        val event = eventsService
        val request = call.request()
        val result = when(request) {
            is AttachmentMessageRequest -> {
                event?.sendAttachmentMessage(call)?.also { result ->
                    if(result) {
                        call.onComplete(com.skt.nugu.sdk.core.interfaces.message.Status.OK)
                    }
                }
            }
            is EventMessageRequest -> {
                call.headers()?.let {
                    eventMessageHeaders = it
                }
                event?.sendEventMessage(call)
            }
            is CrashReportMessageRequest -> true /* Deprecated */
            else -> false
        } ?: false

        Logger.d(TAG, "sendMessage : ${call.request().toStringMessage()}, result : $result")
        return result
    }

    /**
     * Receive an error.
     * @param the status of grpc
     */
    override fun onError(status: Status) {
        Logger.w(TAG, "[onError] Error : ${status.code}")

        when(status.code) {
            Status.Code.UNAUTHENTICATED -> {
                // nothing to do
            }
            else -> {
                transportObserver?.onReconnecting( when(status.code) {
                    Status.Code.OK -> ChangedReason.SUCCESS
                    Status.Code.UNAVAILABLE -> {
                        var cause = status.cause
                        var reason =
                            if (isConnected()) ChangedReason.SERVER_SIDE_DISCONNECT
                            else ChangedReason.CONNECTION_ERROR
                        reason.cause = cause
                        while (cause != null) {
                            if (cause is UnknownHostException) {
                                reason = ChangedReason.DNS_TIMEDOUT
                            }  else if(cause is SocketTimeoutException) {
                                reason = ChangedReason.CONNECTION_TIMEDOUT
                            } else if( cause is ConnectException || cause is SSLHandshakeException) {
                                reason = ChangedReason.CONNECTION_ERROR
                            }
                            cause = cause.cause
                        }
                        reason
                    }
                    Status.Code.UNKNOWN -> ChangedReason.SERVER_SIDE_DISCONNECT
                    Status.Code.DEADLINE_EXCEEDED -> {
                        if (isConnected()) {
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
                    Status.Code.PERMISSION_DENIED,
                    Status.Code.INTERNAL -> ChangedReason.SERVER_INTERNAL_ERROR
                    Status.Code.OUT_OF_RANGE,
                    Status.Code.DATA_LOSS,
                    Status.Code.INVALID_ARGUMENT -> ChangedReason.INTERNAL_ERROR
                    Status.Code.CANCELLED -> {
                        Logger.w(TAG, "skip CANCELLED")
                        return
                    }
                    else -> {
                        throw NotImplementedError()
                    }
                })
            }
        }

        // Only stop ServerInitiated and wait for events to be sent.
        stopServerInitiated()
        isConnected.set(false)

        backoff.awaitRetry(status.code, object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                Logger.w(TAG, "[awaitRetry] Error : $error")

                when (status.code) {
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
                Logger.w(TAG, "[awaitRetry] onRetry : $retriesAttempted")
                if (isConnected()) {
                    Logger.w(TAG, "[awaitRetry] It is currently connected, but will try again.")
                }
                disconnect()
                connect()
            }
        })
    }

    override fun shutdown() {
        Logger.d(TAG, "[shutdown]")
        messageConsumer = null
        transportObserver = null
        disconnect()
        backoff.reset()
        scheduler.shutdown()
    }

    /**
     * Connected event received
     * @return boolean value, true if the connection has changed, false otherwise.
     */
    private fun handleConnectedIfNeeded()  {
        if (isConnected.compareAndSet(false, true)) {
            handoffConnectionEnd()
            backoff.reset()
            transportObserver?.onConnected()
        }
    }

    private fun handoffConnectionEnd() {
        if (isHandOff) {
            Logger.d(TAG, "[handoffConnectionEnd] $isHandOff -> false")
            isHandOff = false
        }
    }

    /**
     * Notification that sending a ping to DeviceGateway has been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged() {
        Logger.d(TAG, "onPingRequestAcknowledged, isConnected:${isConnected()}")
        handleConnectedIfNeeded()
    }

    /**

     * attachment received
     * @param attachmentMessage
     */
    override fun onReceiveAttachment(attachmentMessage: AttachmentMessage) {
        messageConsumer?.consumeAttachment(attachmentMessage.toAttachmentMessage())
    }

    /**
     * directive received
     * @param directiveMessage
     */
    override fun onReceiveDirectives(directiveMessage: DirectiveMessage) {
        messageConsumer?.consumeDirectives(directiveMessage.toDirectives())
    }

    override fun newCall(
        activeTransport: Transport?,
        request: MessageRequest,
        headers: Map<String, String>?,
        listener: MessageSender.OnSendMessageListener
    ): Call {
        throw NotImplementedError()
    }

    override fun getHeaders(): Map<String, String>? {
        return eventMessageHeaders
    }
}
