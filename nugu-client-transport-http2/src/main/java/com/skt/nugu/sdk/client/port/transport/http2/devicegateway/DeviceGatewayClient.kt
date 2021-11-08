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
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLHandshakeException
import com.skt.nugu.sdk.core.interfaces.message.Status as SDKStatus

/**
 *  Implementation of DeviceGateway with http2
 **/
internal class DeviceGatewayClient(
    private val policy: Policy,
    private var messageConsumer: MessageConsumer?,
    private var transportObserver: DeviceGatewayTransport.TransportObserver?,
    private val authDelegate: AuthDelegate,
    private val isStartReceiveServerInitiatedDirective: () -> Boolean) : DeviceGatewayTransport {
    companion object {
        private const val TAG = "HTTP2DeviceGatewayClient"
        private const val maxAsyncCallsSize = 100

        fun create(
            policy: Policy,
            messageConsumer: MessageConsumer?,
            transportObserver: DeviceGatewayTransport.TransportObserver?,
            authDelegate: AuthDelegate,
            isStartReceiveServerInitiatedDirective: () -> Boolean) = DeviceGatewayClient(policy,
                messageConsumer, transportObserver, authDelegate, isStartReceiveServerInitiatedDirective)
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
    private var firstResponseReceived = AtomicBoolean(false)

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
        Logger.d(TAG, "[connect] isConnected = ${isConnected()}, isStartReceiveServerInitiatedDirective = ${isStartReceiveServerInitiatedDirective()}")

        if (isConnected()) {
            Logger.w(TAG, "[connect] already connected")
            return false
        }
        return processConnection()
    }

    private fun processConnection(): Boolean {
        val policy = currentPolicy ?: run {
            Logger.w(TAG, "[connect] no more policy")
            transportObserver?.onError(
                ChangedReason.UNRECOVERABLE_ERROR
            )
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

    private fun createChannel(policy: ServerPolicy, onError: ((Throwable) -> Unit)? = null) : Boolean {
        synchronized(this) {
            client = try {
                createChannelBuilderWith(policy, authDelegate)
            } catch (th: Throwable) {
                onError?.invoke(th)
                return false
            }
            if(!isStartReceiveServerInitiatedDirective()) {
                handleOnConnected()
                return true
            }
            buildDirectivesService()
        }
        return true
    }

    private fun buildDirectivesService() {
        Logger.d(TAG, "[buildDirectivesService] currentChannel=$client")

        pingService?.shutdown()
        directivesService?.shutdown()

        currentPolicy?.let {
            directivesService =
                DirectivesService.create(
                    it,
                    client,
                    this@DeviceGatewayClient
                )

            pingService =
                PingService.create(
                    it,
                    client,
                    healthCheckPolicy,
                    this@DeviceGatewayClient
                )
        }
    }

    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        Logger.d(TAG, "[disconnect]")
        processDisconnect()
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
            ChannelBuilderUtils.shutdown(executor, client)
        }
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Sends an message request.
     * @param request the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun send(call: Call): Boolean {
        val request = call.request()
        return when(request) {
            is AttachmentMessageRequest -> {
                getEventsService()?.sendAttachmentMessage(request)?.also { result ->
                    if(result) {
                        call.onComplete(SDKStatus.OK)
                        asyncCalls[request.parentMessageId]?.reschedule()
                    }
                } ?: false
            }
            is EventMessageRequest -> {
                asyncCalls[request.messageId] = call
                getEventsService()?.sendEventMessage(call) ?: false
            }
            is CrashReportMessageRequest -> true /* Deprecated */
            else -> false
        }.also { result ->
            Logger.d(TAG, "sendMessage : ${request.toStringMessage()}, result : $result")
        }
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
                            if (isConnected.get()) ChangedReason.SERVER_SIDE_DISCONNECT
                            else ChangedReason.CONNECTION_TIMEDOUT
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
                if(isStartReceiveServerInitiatedDirective()) {
                    pingService?.newPing()
                } else {
                    handleOnConnected()
                }
            }
        })
    }

    override fun shutdown() {
        messageConsumer = null
        transportObserver = null
        backoff.shutdown()
        processDisconnect()
        asyncCalls.forEach{
            it.value.cancel()
        }
        asyncCalls.clear()
        Logger.d(TAG, "[shutdown]")
    }

    /**
     * Connected event received
     * @return boolean value, true if the connection has changed, false otherwise.
     */
    private fun handleOnConnected() {
        if (isConnected.compareAndSet(false, true)) {
            Logger.d(TAG, "[handleOnConnected] isConnected is changed")
        }
        transportObserver?.onConnected()
    }

    /**
     * Notification that sending a ping to DeviceGateway has been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged() = handleOnConnected().also {
        Logger.d(TAG, "onPingRequestAcknowledged, isConnected:${isConnected()}, isStartReceiveServerInitiatedDirective=${isStartReceiveServerInitiatedDirective()}")
    }

    /**
     * Directive received
     * @param directiveMessage
     */
    override fun onReceiveDirectives(directiveMessage: List<DirectiveMessage>) {
        // The backoff reset is at the point of receiving the first directive from the server.
        if(firstResponseReceived.compareAndSet(false, true)) {
            backoff.reset()
        }
        messageConsumer?.consumeDirectives(directiveMessage)
    }

    /**
     * Attachment received
     * @param attachmentMessage
     */
    override fun onReceiveAttachment(attachmentMessage: AttachmentMessage) {
        messageConsumer?.consumeAttachment(attachmentMessage)
    }

    override fun startDirectivesService() {
        Logger.d(TAG, "[startDirectivesService] isConnected=${isConnected()}")
        buildDirectivesService()
    }

    override fun stopDirectivesService() {
        Logger.d(TAG, "[stopDirectivesService]")
        processDisconnect()
        processConnection()
    }

    private fun getEventsService() : EventsService? {
        if(eventsService == null) {
            currentPolicy?.let {
                eventsService =
                    EventsService.create(
                        it,
                        client,
                        this@DeviceGatewayClient
                    )
            }
        }
        return eventsService
    }
}