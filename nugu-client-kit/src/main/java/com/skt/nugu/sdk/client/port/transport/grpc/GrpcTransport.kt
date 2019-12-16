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
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.auth.AuthStateListener
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.client.port.transport.grpc.TransportState.*
import devicegateway.grpc.PolicyResponse
import java.util.concurrent.Executors


/**
 * Class to create and manage an gRPC transport
 */
internal class GrpcTransport private constructor(
    registryOptions: Options,
    private val authDelegate: AuthDelegate,
    private val messageConsumer: MessageConsumer,
    private var transportObserver: TransportListener?
) : Transport, AuthStateListener {
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

    private var state: TransportState = TransportState()
    private fun getDetailedState() = state.getDetailedState()
    private var deviceGatewayClient: DeviceGatewayClient? = null
    private var registryClient: RegistryClient = RegistryClient(registryOptions)

    /** @return the bearer token **/
    private fun getAuthorization() = authDelegate.getAuthorization()?:""
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Transport Initialize.
     */
    init {
        authDelegate.addAuthStateListener(this)
    }

    /**
     * connect from deviceGatewayClient and registryClient.
     */
    override fun connect(): Boolean {
        if(state.isConnectedOrConnecting()) {
            when(getDetailedState()) {
                DetailedState.AUTHENTICATING,
                DetailedState.RECONNECTING -> {}
                else  -> return false
            }
        }

        setState(DetailedState.CONNECTING)

        return tryGetPolicy()
    }

    /**
     * Get a policy from the Registry.
     * @return true is success, otherwise false
     */
    private fun tryGetPolicy(): Boolean {
        checkAuthorizationIfEmpty {
            setState(DetailedState.AUTHENTICATING,ChangedReason.INVALID_AUTH)
        } ?: return false

        if(DetailedState.CONNECTING_REGISTRY == getDetailedState()) {
            return false
        }
        setState(DetailedState.CONNECTING_REGISTRY)
        registryClient.getPolicy(getAuthorization(), object : RegistryClient.Observer {
            override fun onCompleted(policy: PolicyResponse?) {
                // succeeded, then it should be connection to DeviceGateway
                policy?.let {
                    tryConnectToDeviceGateway(it)
                } ?: setState(DetailedState.FAILED, ChangedReason.UNRECOVERABLE_ERROR)
            }

            override fun onError(reason: ChangedReason) {
                when (reason) {
                    ChangedReason.INVALID_AUTH -> {
                        setState(DetailedState.AUTHENTICATING,ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        setState(DetailedState.FAILED, reason)
                    }
                }
            }
        })

        return true
    }

    private val deviceGatewayObserver = object : DeviceGatewayClient.Observer {
        override fun onConnected() {
            setState(DetailedState.CONNECTED)
        }

        override fun onError(reason: ChangedReason) {
            when (reason) {
                ChangedReason.SUCCESS -> { /* nothing to do */}
                ChangedReason.INVALID_AUTH -> setState(DetailedState.AUTHENTICATING,reason)
                else -> {
                    // If registryClient is not shutdown, try again. Otherwise fail
                    // because if the handoffConnection fails, the registry must be retry.
                    if(RegistryClient.cachedPolicy != null) {
                        setState(DetailedState.FAILED, reason)
                    } else {
                        setState(DetailedState.RECONNECTING, reason)
                        connect()
                    }
                }
            }
        }

        override fun onReconnecting(reason: ChangedReason) {
            setState(DetailedState.RECONNECTING, reason)
        }
    }

    /**
     * Connect to DeviceGateway.
     * @param policy Policy received from the registry server
     * @return true is success, otherwise false
     */
    private fun tryConnectToDeviceGateway(policy: PolicyResponse): Boolean {
        checkAuthorizationIfEmpty {
            setState(DetailedState.AUTHENTICATING,ChangedReason.INVALID_AUTH)
        } ?: return false

        setState(DetailedState.CONNECTING_DEVICEGATEWAY)

        deviceGatewayClient?.shutdown()

        DeviceGatewayClient(
            policy,
            messageConsumer,
            deviceGatewayObserver,
            getAuthorization()
        ).let {
            deviceGatewayClient = it
            return it.connect()
        }
    }

    /**
     * Check for Authorization
     * @param block Invoked only when authorization is NullOrBlank.
     * @return the authorization
     */
    private fun checkAuthorizationIfEmpty(block: () -> Unit) : String? {
        val authorization = getAuthorization()
        if (authorization.isBlank()) {
            block.invoke()
            return null
        }
        return authorization
    }

    /**
     * Disconnect from deviceGatewayClient and registryClient.
     */
    override fun disconnect() {
        if (!state.isConnectedOrConnecting()) {
            Logger.d(TAG, "[disconnect], Status : ($state)")
            return
        }
        deviceGatewayClient?.disconnect()
        registryClient.disconnect()
    }

    /**
     * Returns whether this object is currently connected.
     * @return true is CONNECTED.
     */
    override fun isConnected(): Boolean = state.isConnected()

    override fun send(request: MessageRequest) : Boolean {
        if (!state.isConnected()) {
            Logger.d(TAG, "[send], Status : ($state), request : $request")
            return false
        }
        return deviceGatewayClient?.send(request) ?: false
    }

    /**
     *  Explicitly clean up client resources.
     */
    override fun shutdown() {
        Logger.d(TAG, "[shutdown] $this")
        // remove observer
        transportObserver = null
        // remove AuthStateListener
        authDelegate.removeAuthStateListener(this)

        executor.execute {
            registryClient.shutdown()

            deviceGatewayClient?.shutdown()
            deviceGatewayClient = null
            // only internal
            setState(DetailedState.DISCONNECTED)
            executor.shutdown()
        }
    }

    /**
     *  handoff connection from SystemCapability
     */
    override fun handoffConnection(
        protocol: String,
        hostname: String,
        address: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String) {
        setState(DetailedState.HANDOFF, ChangedReason.SERVER_ENDPOINT_CHANGED)

        val healthCheckPolicy = RegistryClient.cachedPolicy?.healthCheckPolicy
        if(healthCheckPolicy == null) {
            Logger.d(TAG, "[handoffConnection] healthCheckPolicy is null")
            setState(DetailedState.FAILED, ChangedReason.UNRECOVERABLE_ERROR)
            return
        }
        // Important: Should be clear the cached to retry the connection to the registry after a handoff failure.
        RegistryClient.cachedPolicy = null

        // create PolicyResponse
        val policy = PolicyResponse.newBuilder()
            .setHealthCheckPolicy(healthCheckPolicy)
            .addServerPolicy(
                PolicyResponse.ServerPolicy.newBuilder()
                    .setPort(port)
                    .setHostName(hostname)
                    .setAddress(address)
                    .setRetryCountLimit(retryCountLimit)
                    .setConnectionTimeout(connectionTimeout)
            ).build()
        executor.submit{
            tryConnectToDeviceGateway(policy)
        }
    }

    /**
     * Notification that an authorization state has changed.
     */
    override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
        when (newState) {
            AuthStateListener.State.UNINITIALIZED,
            AuthStateListener.State.EXPIRED -> {
                setState(DetailedState.AUTHENTICATING)
                disconnect()
            }
            AuthStateListener.State.REFRESHED -> {
                setState(DetailedState.AUTHENTICATING)
                disconnect()
                connect()
            }
            AuthStateListener.State.UNRECOVERABLE_ERROR -> {
                disconnect()
                setState(DetailedState.FAILED, ChangedReason.INVALID_AUTH)
            }
        }
        return true
    }

    /**
     * Set the state to a new state.
     */
    private fun setState(newDetailedState: DetailedState,
                         reason: ChangedReason = ChangedReason.NONE): Boolean {
        if (newDetailedState == getDetailedState()) {
            Logger.d(TAG, "[setState] Already in state ($state)")
            return true
        }

        var allowed = false
        when (newDetailedState) {
            DetailedState.IDLE -> {
                allowed = false
            }
            DetailedState.CONNECTING -> {
                allowed = DetailedState.IDLE == getDetailedState() || DetailedState.AUTHENTICATING == getDetailedState()
            }
            DetailedState.AUTHENTICATING -> {
                allowed = DetailedState.FAILED != getDetailedState() && DetailedState.DISCONNECTED != getDetailedState()
            }
            DetailedState.CONNECTING_REGISTRY -> {
                allowed = DetailedState.CONNECTING == getDetailedState() || DetailedState.AUTHENTICATING == getDetailedState()
            }
            DetailedState.CONNECTING_DEVICEGATEWAY -> {
                allowed = getDetailedState() == DetailedState.CONNECTING_REGISTRY || getDetailedState() == DetailedState.HANDOFF
            }
            DetailedState.HANDOFF -> {
                allowed = DetailedState.IDLE == getDetailedState()
            }
            DetailedState.CONNECTED -> {
                allowed = getDetailedState() == DetailedState.CONNECTING_DEVICEGATEWAY || getDetailedState() == DetailedState.RECONNECTING
            }
            DetailedState.DISCONNECTING -> {
                allowed = getDetailedState() == DetailedState.CONNECTING || getDetailedState() == DetailedState.CONNECTED
            }
            DetailedState.DISCONNECTED -> {
                allowed = getDetailedState() == DetailedState.CONNECTED
            }
            DetailedState.RECONNECTING -> {
                allowed = getDetailedState() == DetailedState.CONNECTING_DEVICEGATEWAY || getDetailedState() == DetailedState.CONNECTED
            }
            DetailedState.FAILED -> {
                allowed = true
            }
        }
        // State change not allowed
        if (!allowed) {
            return false
        }
        Logger.d(TAG, "[setState] ${getDetailedState()} -> $newDetailedState")

        // Perform status processing for Observer delivery
        when (TransportState.fromDetailedState(newDetailedState)) {
            ConnectionStatusListener.Status.CONNECTING -> {
                var newReason = reason
                if(DetailedState.AUTHENTICATING == newDetailedState) {
                    newReason = ChangedReason.INVALID_AUTH
                }
                transportObserver?.onConnecting(this, newReason)
            }
            ConnectionStatusListener.Status.CONNECTED -> {
                transportObserver?.onConnected(this)
            }
            ConnectionStatusListener.Status.DISCONNECTED -> {
                transportObserver?.onDisconnected(this, reason)
            }
        }

        // Update state
        state.setDetailedState(newDetailedState, reason)

        // If new state is AUTHENTICATING, call onAuthFailure
        if(DetailedState.AUTHENTICATING == newDetailedState && reason == ChangedReason.INVALID_AUTH) {
            authDelegate.onAuthFailure(getAuthorization())
        }
        return true
    }
}
