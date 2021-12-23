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

import com.google.common.annotations.VisibleForTesting
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
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.CallOptions
import com.skt.nugu.sdk.core.interfaces.transport.ChannelOptions
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
                                   private var messageConsumer: MessageConsumer?,
                                   private var transportObserver: DeviceGatewayTransport.TransportObserver?,
                                   private val authDelegate: AuthDelegate,
                                   private val callOptions: CallOptions?,
                                   private val channelOptions: ChannelOptions?,
                                   private val isStartReceiveServerInitiatedDirective: () -> Boolean)
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
    private var firstResponseReceived = AtomicBoolean(false)

    private val scheduler  = Executors.newSingleThreadScheduledExecutor()

    private var pendingHeaders: Map<String, String>? = null
    /**
     * Set a policy.
     * @return the ServerPolicy
     */
    private fun nextPolicy(): ServerPolicy? {
        Logger.d(TAG, "[nextPolicy]")
        currentPolicy = policies.poll()
        currentPolicy?.let {
            backoff = BackOff.Builder(maxAttempts = it.retryCountLimit).build()
        }
        return currentPolicy
    }

    private fun createChannel(policy: ServerPolicy, onError: ((Throwable) -> Unit)? = null) : Boolean {
        synchronized(this) {
            if(currentChannel == null) {
                currentChannel = try {
                    ChannelBuilderUtils.createChannelBuilderWith(
                        policy,
                        channelOptions,
                        authDelegate,
                        this@DeviceGatewayClient,
                        isStartReceiveServerInitiatedDirective
                    ).build()
                } catch (th: Throwable) {
                    onError?.invoke(th)
                    return false
                }
            }
            if(!isStartReceiveServerInitiatedDirective()) {
                handleOnConnected()
                return true
            }
            buildDirectivesService()
        }
        return true
    }
    /**
     * Connect to DeviceGateway.
     * @return true is success, otherwise false
     */
    override fun connect(): Boolean {
        Logger.d(TAG, "[connect] isConnected = ${isConnected()}, isStartReceiveServerInitiatedDirective = ${isStartReceiveServerInitiatedDirective()}")

        if (isConnected()) {
            Logger.w(TAG, "[connect] already connected")
            return false
        }
        return processConnection()
    }

    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        Logger.d(TAG, "[disconnect]")
        processDisconnect()
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Sends a message request.
     * @param request the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun send(call: Call): Boolean {
        val request = call.request()
        return when (request) {
            is AttachmentMessageRequest -> {
                getEventsService()?.sendAttachmentMessage(call)?.also { result ->
                    if (result) {
                        call.onComplete(com.skt.nugu.sdk.core.interfaces.message.Status.OK)
                    }
                } ?: false
            }
            is EventMessageRequest -> {
                getEventsService()?.sendEventMessage(call.also {
                    it.headers()?.let { headers ->
                        pendingHeaders = headers
                    }
                }) ?: false
            }
            else -> false
        }.also { result ->
            Logger.d(TAG, "sendMessage : ${call.request().toStringMessage()}, result : $result")
        }
    }

    /**
     * Receive an error.
     * @param the status of grpc
     */
    override fun onError(status: Status, who: String) {
        Logger.w(TAG, "[onError] Error=${status.code}, who=$who")

        when(status.code) {
            Status.Code.UNAUTHENTICATED -> {
                // nothing to do
            }
            Status.Code.CANCELLED -> {
                // This is not an error because it is generated by the caller.
                Logger.w(TAG, "The operation was cancelled")
                return
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
                            if(PingService.name == who) {
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
                    else -> {
                        throw NotImplementedError()
                    }
                })
            }
        }

        isConnected.set(false)
        firstResponseReceived.set(false)

        backoff.awaitRetry(status.code, object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                Logger.w(TAG, "[awaitRetry] error=$error, code=${status.code}")
                when(error) {
                    BackOff.BackoffError.AlreadyShutdown,
                    BackOff.BackoffError.AlreadyStarted,
                    BackOff.BackoffError.ScheduleCancelled -> return
                    else -> Unit
                }
                when (status.code) {
                    Status.Code.UNAUTHENTICATED -> {
                        transportObserver?.onError(ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        processDisconnect()
                        nextPolicy()
                        processConnection()
                    }
                }
            }

            override fun onRetry(retriesAttempted: Int) {
                Logger.w(TAG, "[awaitRetry] onRetry count=$retriesAttempted, connected=${isConnected()}, tid=${Thread.currentThread().id}")
                processDisconnect()
                processConnection()
            }
        })
    }

    override fun shutdown() {
        messageConsumer = null
        transportObserver = null
        backoff.shutdown()
        scheduler.shutdown()
        processDisconnect()
        Logger.d(TAG, "[shutdown]")
    }

    /**
     * Handler for connection completed
     */
    @VisibleForTesting
    internal fun handleOnConnected() {
        if (isConnected.compareAndSet(false, true)) {
            Logger.d(TAG, "[handleOnConnected] isConnected is changed")
        }
        transportObserver?.onConnected()
        directivesService?.start()
    }

    private fun processDisconnect() {
        isConnected.set(false)
        firstResponseReceived.set(false)

        synchronized(this) {
            pingService?.shutdown()
            pingService = null
            directivesService?.shutdown()
            directivesService = null
            eventsService?.shutdown()
            eventsService = null
            ChannelBuilderUtils.shutdown(currentChannel)
            currentChannel = null
            pendingHeaders = null
        }
    }

    private fun processConnection(): Boolean {
        val policy = currentPolicy ?: run {
            Logger.w(TAG, "[connect] no more policy")
            val reason = ChangedReason.UNRECOVERABLE_ERROR
            reason.cause = Throwable("no more policy")
            transportObserver?.onError(reason)
            return false
        }
        Logger.d(TAG, "[connect] policy = $policy")
        return createChannel(policy) {
            Logger.w(TAG, "[connect] Can't create a new channel. Exception: $it")
            val reason = ChangedReason.CONNECTION_ERROR
            reason.cause = it
            transportObserver?.onError(reason)
        }
    }

    /**
     * Notification that sending a ping to DeviceGateway has been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged() = handleOnConnected().also {
        Logger.d(TAG, "onPingRequestAcknowledged, isConnected:${isConnected()}")
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
        // The backoff reset is at the point of receiving the first directive from the server.
        if(firstResponseReceived.compareAndSet(false, true)) {
            backoff.reset()
        }
        messageConsumer?.consumeDirectives(directiveMessage.toDirectives())
    }

    override fun getHeaders() = pendingHeaders

    override fun startDirectivesService() {
        Logger.d(TAG, "[startDirectivesService] isConnected=${isConnected()}")
        buildDirectivesService()
    }

    private fun getEventsService() : EventsService? {
        if(eventsService == null) {
            currentChannel?.let {
                eventsService =
                    EventsService(
                        it,
                        this@DeviceGatewayClient,
                        scheduler,
                        callOptions
                    )
            }
        }
        return eventsService
    }

    private fun buildDirectivesService() {
        Logger.d(TAG, "[buildDirectivesService] currentChannel=$currentChannel")

        pingService?.shutdown()
        directivesService?.shutdown()
        currentChannel?.apply {
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
    }

    override fun stopDirectivesService() {
        Logger.d(TAG, "[stopDirectivesService]")
        processDisconnect()
        processConnection()
    }
}
