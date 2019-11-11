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
    private val messageConsumer: MessageConsumer,
    private val transportObserver: TransportListener
) : Transport, AuthStateListener, TransportListener {
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
        /** Ready to start data connection setup. */
        INIT,
        /** Awaiting response from Registry in order to receive policy **/
        POLICY_WAIT,
        /** Currently connecting to DeviceGateway **/
        CONNECTING,
        /** DeviceGateway should be available **/
        CONNECTED,
        /** Tearing down the connection. **/
        DISCONNECTING,
        /** not available. */
        DISCONNECTED,
        /** Attempt to connect failed. */
        FAILED
    }

    private var state: Enum<State> = State.INIT
        set(value) {
            Logger.d(TAG, "state changed : $field -> $value ")
            field = value
        }
    private val registryClient = RegistryClient.newClient()
    private var deviceGatewayClient: DeviceGatewayClient? = null

    override fun connect(): Boolean {
        if (state == State.CONNECTED || state == State.CONNECTING || registryClient.isConnecting()) {
            return false
        }

        val authorization = authDelegate.getAuthorization()
        if (authorization.isNullOrBlank()) {
            Logger.w(TAG, "empty authorization")
            authDelegate.onAuthFailure(authorization)
            return false
        }

        val policy = registryClient.policy
        if (policy == null) {
            tryGetPolicy(authorization)
        } else {
            tryConnectToDeviceGateway(policy, authorization)
        }
        return true
    }


    private fun tryGetPolicy(authorization: String) {
        state = State.POLICY_WAIT

        val registryChannel =
            ChannelBuilderUtils.createChannelBuilderWith(registryServerOption, authorization)
                .build()
        registryClient.getPolicy(registryChannel, object : RegistryClient.Observer {
            override fun onCompleted() {
                connect()
            }

            override fun onError(code: Status.Code) {
                registryClient.shutdown()

                when (code) {
                    Status.Code.UNAUTHENTICATED -> {
                        authDelegate.onAuthFailure(authorization)
                    }
                    else -> {
                        state = State.FAILED

                        transportObserver.onDisconnected(this@GrpcTransport,
                            ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR
                        )
                    }
                }
            }
        })
    }

    private fun tryConnectToDeviceGateway(policy: PolicyResponse, authorization: String): Boolean {
        state = State.CONNECTING

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
        deviceGatewayClient = null
    }

    override fun isConnected(): Boolean = deviceGatewayClient?.isConnected() ?: false

    override fun send(request: MessageRequest) : Boolean {
        if (state != State.CONNECTED) {
            Logger.d(TAG,
                "send failed, Status : ($state), request : $request"
            )
            return false
        }
        return deviceGatewayClient?.send(request) ?: false
    }

    override fun shutdown() {
        deviceGatewayClient?.shutdown()
        deviceGatewayClient = null

        state = State.DISCONNECTED
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
        transportObserver.onConnecting(transport)
    }

    override fun onConnected(transport: Transport) {
        state = State.CONNECTED
        transportObserver.onConnected(transport)
    }

    override fun onDisconnected(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        state = State.DISCONNECTED

        transportObserver.onDisconnected(transport, reason)

        if(reason == ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR) {
            registryClient.shutdown()
            disconnect()
            connect()
        }
    }
}
