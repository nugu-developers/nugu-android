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
package com.skt.nugu.sdk.client.port.transport.grpc

import com.google.protobuf.ByteString
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.auth.AuthStateListener
import java.util.concurrent.ConcurrentLinkedQueue
import com.skt.nugu.sdk.client.port.transport.grpc.core.GrpcServiceListener
import com.skt.nugu.sdk.client.port.transport.grpc.core.GrpcServiceManager
import com.skt.nugu.sdk.core.network.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.network.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import devicegateway.grpc.*
import io.grpc.ConnectivityState
import java.util.*

/**
 * Class to create and manage an GRPC connection to DeviceGateway.
 */
internal class GrpcTransport(
    private val channel: Channels,
    private val authDelegate: AuthDelegate,
    private val messageConsumer: MessageConsumer,
    private val transportObserver: TransportListener
) : Transport, GrpcServiceListener, AuthStateListener {
    private var requestQueue = ConcurrentLinkedQueue<MessageRequest>()
    private var state = State.INIT
    private var registryFinished: Boolean = false
    private val services = GrpcServiceManager()

    var reconnecting: Boolean = false

    /**
     * Transport Constructor.
     */
    companion object {
        private const val TAG = "GrpcTransport"
        fun create(
            opts: Options,
            authDelegate: AuthDelegate,
            messageConsumer: MessageConsumer,
            transportObserver: TransportListener
        ): Transport {
            return GrpcTransport(
                Channels.newChannel(opts),
                authDelegate,
                messageConsumer,
                transportObserver
            )
        }
    }

    /**
     * Enum to Connection State of Transport
     */
    enum class State {
        /// Initial state
        INIT,
        /// Waiting for authorization to complete.
        AUTHORIZING,
        /// Waiting for connected
        CONNECTING,
        /// Waiting for connected, retrying to connect to DeviceGateway
        WAITING_TO_RETRY_CONNECTING,
        /// Perform connect
        POST_CONNECTING,
        /// Connected to DeviceGateway
        CONNECTED,
        /// disconnected by DeviceGateway
        SERVER_SIDE_DISCONNECT,
        /// disconnected
        DISCONNECTING,
        /// shutdown
        SHUTDOWN
    }

    /**
     * Transport Initialize.
     */
    init {
        authDelegate.addAuthStateListener(this)
    }

    /**
     * Set the state to a new state.
     */
    private fun setState(newState: State): Boolean {
        if (newState == state) {
            // not changed
            return true
        }
        Logger.d(TAG, "[setState] $state / $newState")

        var allowed = false
        when (newState) {
            State.INIT -> {
                allowed = false
            }
            State.AUTHORIZING -> allowed =
                State.INIT == state || State.WAITING_TO_RETRY_CONNECTING == state
            State.CONNECTING -> allowed =
                State.INIT == state || State.AUTHORIZING == state || State.WAITING_TO_RETRY_CONNECTING == state
            State.WAITING_TO_RETRY_CONNECTING -> allowed = State.CONNECTING == state
            State.POST_CONNECTING -> allowed = State.CONNECTING == state
            State.CONNECTED -> allowed = true
            State.SERVER_SIDE_DISCONNECT -> allowed =
                state != State.DISCONNECTING && state != State.SHUTDOWN
            State.DISCONNECTING -> allowed = state != State.SHUTDOWN
            State.SHUTDOWN -> allowed = true
        }

        if (!allowed) {
            return false
        }

        when (newState) {
            State.INIT,
            State.AUTHORIZING,
            State.CONNECTING,
            State.WAITING_TO_RETRY_CONNECTING,
            State.POST_CONNECTING -> {
                transportObserver.onConnecting(this)
            }
            State.CONNECTED -> {
                reconnecting = false
                transportObserver.onConnected(this)
                performSendMessage()
            }
            State.SERVER_SIDE_DISCONNECT -> {
                if (state == State.CONNECTED) {
                    transportObserver.onDisconnected(
                        this,
                        ConnectionStatusListener.ChangedReason.SERVER_SIDE_DISCONNECT
                    )
                } else if (state == State.POST_CONNECTING) {
                    transportObserver.onDisconnected(
                        this,
                        ConnectionStatusListener.ChangedReason.CONNECTION_TIMEDOUT
                    )
                }
            }
            State.DISCONNECTING,
            State.SHUTDOWN -> {
                transportObserver.onDisconnected(this, ConnectionStatusListener.ChangedReason.NONE)
            }
        }

        state = newState
        return true
    }

    /**
     * connect from DeviceGateway.
     */
    override fun connect(): Boolean {
        setState(State.CONNECTING)

        val authorization = authDelegate.getAuthorization()
        if (authorization.isNullOrEmpty()) {
            Logger.d(TAG, "token is empty")
            setState(State.WAITING_TO_RETRY_CONNECTING)
            authDelegate.onAuthFailure(authorization)
            // TODO : false, not yet working
            return true
        }

        shutdownService()
        channel.shutdown()
        channel.connect(Runnable {
            val connectivityState = channel.getState(false)
            when (connectivityState) {
                ConnectivityState.TRANSIENT_FAILURE -> reconnect()
                ConnectivityState.CONNECTING -> {
                    setState(State.POST_CONNECTING)
                    connectService()
                }
            }
        }, authorization)

        return true
    }

    /**
     * Run the registry if not already running, when it is connected run devicegateway connect.
     * Registry is loadBalancer for DeviceGateway
     * DeviceGateway is a realtime server like router capabilities.
     * @return true is start, false is already start
     */
    private fun connectService(): Boolean {
        val server =
            if (this.registryFinished) GrpcServiceManager.SERVER.DEVICEGATEWAY else GrpcServiceManager.SERVER.REGISTRY
        if (!services.hasService(server)) {
            services.addServices(this, server)
        }
        services.connect(channel)
        return true
    }

    private fun shutdownService() {
        this.services.shutdown()
    }

    /**
     * reconnect from DeviceGateway.
     */
    private fun reconnect() {
        if (this.reconnecting) return

        setState(State.WAITING_TO_RETRY_CONNECTING)

        val delay = this.channel.getBackoff().duration()
        Logger.d(
            TAG,
            String.format("will wait ${delay}ms before reconnect attempt ${this.channel.getBackoff().getAttempts()}")
        )

        if (this.channel.getBackoff().hasAttemptRemaining()) {
            this.channel.getBackoff().attempt()
            this.reconnecting = true

            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    timer.cancel()
                    reconnecting = false

                    if (!isConnected()) {
                        connect()
                    }
                }
            }, delay)
        } else {
            if (!this.channel.nextChannel()) {
                Logger.d(TAG, "reconnect failed")
                shutdown()
                return
            }
            Logger.d(TAG, "reconnect : next server!")
            // recursive call
            reconnect()
        }
    }

    /**
     * Disconnect from DeviceGateway.
     */
    override fun disconnect() {
        performSendMessage()
        this.requestQueue.clear()

        if (State.SHUTDOWN != state) {
            setState(State.DISCONNECTING)
        }
        shutdownService()

        this.channel.shutdown()
        this.channel.resetChannel()
    }

    /**
     *  Explicitly clean up client resources.
     */
    override fun shutdown() {
        // Prevent reconnection during shutdown
        this.disconnect()
        this.registryFinished = false
        this.reconnecting = false
        this.channel.getBackoff().reset()
        transportObserver.onDisconnected(
            this,
            ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
        )
        authDelegate.removeAuthStateListener(this)
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     * @return true is [State.CONNECTED].
     */
    override fun isConnected(): Boolean {
        return State.CONNECTED == state
    }

    override fun onServerSideDisconnect() {
        if (isConnected()) {
            setState(State.SERVER_SIDE_DISCONNECT)
            reconnect()
        }
    }

    /*unused code*/
    override fun sendPostConnectMessage(request: MessageRequest) {
        enqueueRequest(request, true)
    }

    /**
     * Send a message request. it blocks until the message can be sent.
     */
    override fun send(request: MessageRequest): Boolean {
        enqueueRequest(request, false)
        return true
    }

    /*unused code*/
    override fun sendCompleted() {
        services.getEvent()?.sendCompleted()
    }

    /**
     * Perform sending from queue
     */
    private fun performSendMessage() {
        while (!requestQueue.isEmpty()) {
            val next = requestQueue.poll() ?: null ?: break
            when (next) {
                is EventMessageRequest -> {
                    services.getEvent()?.sendEventMessage(toProtobufMessage(next))
                }
                is AttachmentMessageRequest -> {
                    services.getEvent()?.sendAttachmentMessage(toProtobufMessage(next))
                }
                is CrashReportMessageRequest -> {
                    services.getCrashReport()?.sendCrashReport(next.level.value, next.message)
                }
                else -> {
                    Logger.d(TAG, "unknown format")
                }
            }
        }
    }

    private fun toProtobufMessage(request: AttachmentMessageRequest): AttachmentMessage {
        with(request) {
            val attachment = Attachment.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .build()
                )
                .setSeq(seq)
                .setIsEnd(isEnd)
                .setContent(
                    if (byteArray != null) {
                        ByteString.copyFrom(byteArray)
                    } else {
                        ByteString.EMPTY
                    }
                )
                .build()

            return AttachmentMessage.newBuilder()
                .setAttachment(attachment).build()
        }
    }

    private fun toProtobufMessage(request: EventMessageRequest): EventMessage {
        with(request) {
            val event = Event.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .also {
                            if(referrerDialogRequestId != null) {
                                it.referrerDialogRequestId = referrerDialogRequestId
                            }
                        }
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

    /**
     * Notification that an authorization state has changed.
     */
    override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
        when (newState) {
            AuthStateListener.State.UNINITIALIZED,
            AuthStateListener.State.EXPIRED -> {
                if (State.WAITING_TO_RETRY_CONNECTING == state) {
                    setState(State.AUTHORIZING)
                }
            }
            AuthStateListener.State.REFRESHED -> {
                when (state) {
                    State.CONNECTED -> setState(State.DISCONNECTING)
                    State.AUTHORIZING -> setState(State.CONNECTING)
                    else -> {}
                }
                reconnect()
            }
            AuthStateListener.State.UNRECOVERABLE_ERROR -> {
                transportObserver.onDisconnected(
                    this,
                    ConnectionStatusListener.ChangedReason.INVALID_AUTH
                )
            }
        }
        return true
    }

    /**
     * Enqueue a message for sending.
     */
    private fun enqueueRequest(request: MessageRequest, beforeConnected: Boolean) {
        var allowed = false
        when (state) {
            State.INIT,
            State.AUTHORIZING,
            State.CONNECTING,
            State.WAITING_TO_RETRY_CONNECTING,
            State.POST_CONNECTING -> {
                allowed = beforeConnected
            }
            State.CONNECTED -> {
                allowed = !beforeConnected
            }
            State.SERVER_SIDE_DISCONNECT,
            State.SHUTDOWN,
            State.DISCONNECTING -> {
                allowed = false
            }
        }

//        if (request is CertifiedMessageRequest) {
//            database?.insert(request)
//        }

        requestQueue.offer(request)

        if (allowed) {
            performSendMessage()
        }
    }

    /**
     * Notification that sending a ping to DeviceGateway has failed or been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged(success: Boolean) {
        if (!success) {
            val connecting = state == State.POST_CONNECTING
            if (isConnected() || connecting) {
                setState(State.SERVER_SIDE_DISCONNECT)
                disconnect()
                reconnect()
            }
        } else {
            setState(State.CONNECTED)
        }
        Logger.d(TAG, "onPingRequestAcknowledged $success, $state")
    }

    /**
     * Notification that a connection timed out.
     */
    override fun onConnectTimeout() {
        Logger.d(TAG, "onConnectTimeout")
        if (!reconnecting) {
            setState(State.SHUTDOWN)
            reconnect()
        }
    }

    /**
     * Notification that a ping request timed out.
     */
    override fun onPingTimeout() {
        Logger.d(TAG, "onPingTimeout")
        if (!reconnecting) {
            setState(State.SHUTDOWN)
            reconnect()
        }
    }

    /**
     * Notification that a directive
     * this method receives directives via its {@code EventStreamService} class
     */
    override fun onDirectives(directive: String) {
        messageConsumer.consumeMessage(directive)
    }

    /**
     * Registry Connection Handoff from [SystemCapabilityAgent#handleHandoffConnection]
     */
    override fun onHandoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    ) {
        Logger.d(TAG, "onHandoffConnection $protocol, $domain, $hostname, $port, $retryCountLimit, $connectionTimeout, $charge")

        val server = PolicyResponse.ServerPolicy.newBuilder()
            .setPort(port)
            .setHostName(domain)
            .setAddress(hostname)
            .setRetryCountLimit(retryCountLimit)
            .setConnectionTimeout(connectionTimeout)

        val response = PolicyResponse.newBuilder()
            .addServerPolicy(server).build()

        onRegistryConnected(response)
    }

    /**
     * Registry Connection succeeded, then it should be connection to DeviceGateway
     */
    override fun onRegistryConnected(policy: PolicyResponse) {
        Logger.d(TAG, "onRegistryConnected $policy")

        this.channel.setPolicy(policy)
        if (!this.channel.nextChannel()) {
            shutdown()
            return
        }

        this.registryFinished = true
        setState(State.WAITING_TO_RETRY_CONNECTING)
        connect()
    }

    override fun onUnAuthenticated() {
        setState(State.WAITING_TO_RETRY_CONNECTING)
        authDelegate.onAuthFailure(authDelegate.getAuthorization())
    }
}