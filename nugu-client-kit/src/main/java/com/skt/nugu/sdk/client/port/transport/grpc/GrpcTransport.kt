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

import com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.DeviceGatewayClient
import com.skt.nugu.sdk.client.port.transport.grpc.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.auth.AuthStateListener
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PolicyResponse
import io.grpc.Status

/**
 * Class to create and manage an grpc transport
 */
class GrpcTransport private constructor(
    private val registryServerOption: Options,
    private val authDelegate: AuthDelegate,
    private var messageConsumer: MessageConsumer,
    transportObserver: TransportListener
) : Transport, AuthStateListener, TransportListener {
    private var transportObserver: TransportListener? = transportObserver
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
                opts,
                authDelegate,
                messageConsumer,
                transportObserver
            )
        }
    }

    /**
     * Enum to Connection State of Transport
     */
    private enum class State {
        /** initialize */
        INIT,
        /** Awaiting response from Registry in order to receive policy **/
        POLICY_WAIT,
        /** Currently connecting to DeviceGateway **/
        CONNECTING,
        /** DeviceGateway should be available **/
        CONNECTED,
        /** Tearing down the connection **/
        DISCONNECTING,
        /** The connection is closed */
        DISCONNECTED,
        /** not available */
        SHUTDOWN
    }

    private var state: Enum<State> = State.INIT
        set(value) {
            Logger.d(TAG, "state changed : $field -> $value ")
            field = value
        }
    private val registryClient = RegistryClient.newClient()
    private var deviceGatewayClient: DeviceGatewayClient? = null

    /**
     * Transport Initialize.
     */
    init {
        authDelegate.addAuthStateListener(this)
    }

    override fun connect(): Boolean {
        if (state == State.CONNECTED || state == State.CONNECTING || state == State.SHUTDOWN) {
            return false
        }

        val authorization = authDelegate.getAuthorization()
        if (authorization.isNullOrBlank()) {
            Logger.w(TAG, "empty authorization")
            onDisconnected(this@GrpcTransport,
                ConnectionStatusListener.ChangedReason.INVALID_AUTH
            )
            return false
        }

        val policy = registryClient.policy
        return if (policy == null) {
            tryGetPolicy(authorization)
        } else {
            tryConnectToDeviceGateway(policy, authorization)
        }

    }

    private fun tryGetPolicy(authorization: String): Boolean {
        if(state == State.POLICY_WAIT) {
            return false
        }
        state = State.POLICY_WAIT

        transportObserver?.onConnecting(this)

        val registryChannel =
            ChannelBuilderUtils.createChannelBuilderWith(registryServerOption, authorization)
                .build()
        registryClient.getPolicy(registryChannel, object : RegistryClient.Observer {
            override fun onCompleted() {
                connect()
            }

            override fun onError(code: Status.Code) {
                when (code) {
                    Status.Code.UNAUTHENTICATED -> {
                        onDisconnected(this@GrpcTransport,
                            ConnectionStatusListener.ChangedReason.INVALID_AUTH
                        )
                    }
                    else -> {
                        onDisconnected(this@GrpcTransport,
                            ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR
                        )
                    }
                }
            }
        })

        return true
    }

    private fun tryConnectToDeviceGateway(policy: PolicyResponse, authorization: String): Boolean {
        DeviceGatewayClient(
            policy,
            messageConsumer,
            this,
            authorization
        ).let {
            deviceGatewayClient = it
            return@tryConnectToDeviceGateway it.connect()
        }
    }

    override fun disconnect() {
        state = State.DISCONNECTING
        deviceGatewayClient?.disconnect()
    }

    override fun isConnected(): Boolean = (state == State.CONNECTED)

    override fun send(request: MessageRequest) : Boolean {
        if (state != State.CONNECTED) {
            Logger.d(TAG, "send failed, Status : ($state), request : $request")
            return false
        }
        return deviceGatewayClient?.send(request) ?: false
    }

    override fun shutdown() {
        deviceGatewayClient?.shutdown()
        deviceGatewayClient = null

        registryClient.shutdown()

        authDelegate.removeAuthStateListener(this)
        transportObserver = null

        state = State.SHUTDOWN
    }

    override fun onHandoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    ) {
        val transport = deviceGatewayClient
        transport?.onHandoffConnection(
            protocol,
            domain,
            hostname,
            port,
            retryCountLimit,
            connectionTimeout,
            charge
        )

        registryClient.policy = PolicyResponse.newBuilder()
            .addServerPolicy(
                PolicyResponse.ServerPolicy.newBuilder()
                    .setPort(port)
                    .setHostName(domain)
                    .setAddress(hostname)
                    .setRetryCountLimit(retryCountLimit)
                    .setConnectionTimeout(connectionTimeout)
            ).build()

        disconnect()
        connect()
    }

    override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
        when (newState) {
            AuthStateListener.State.UNINITIALIZED,
            AuthStateListener.State.EXPIRED -> {
                disconnect()
            }
            AuthStateListener.State.REFRESHED -> {
                if (isConnected()) {
                    disconnect()
                }
                connect()
            }
            AuthStateListener.State.UNRECOVERABLE_ERROR -> {
                // Please wait, retry manually in the app.
            }
        }
        return true
    }

    override fun onConnecting(transport: Transport) {
        state = State.CONNECTING
        transportObserver?.onConnecting(transport)
    }

    override fun onConnected(transport: Transport) {
        state = State.CONNECTED
        transportObserver?.onConnected(transport)
    }

    override fun onDisconnected(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        state = State.DISCONNECTED
        transportObserver?.onDisconnected(transport, reason)

        if(reason == ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR) {
            shutdown()
            Logger.e(TAG, "UNRECOVERABLE_ERROR occurs, retry manually in app")
        } else if(reason == ConnectionStatusListener.ChangedReason.INVALID_AUTH) {
            authDelegate.onAuthFailure(authDelegate.getAuthorization())
        }
    }
}
