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
package com.skt.nugu.sdk.client.port.transport.http2

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.client.port.transport.http2.TransportState.*
import com.skt.nugu.sdk.client.port.transport.http2.devicegateway.DeviceGatewayClient
import com.skt.nugu.sdk.client.port.transport.http2.devicegateway.DeviceGatewayTransport
import com.skt.nugu.sdk.core.interfaces.transport.DnsLookup
import java.util.concurrent.Executors

/**
 * Class to create and manage a transport
 */
internal class HTTP2Transport(
    private val serverInfo: NuguServerInfo,
    private val dnsLookup: DnsLookup?,
    private val authDelegate: AuthDelegate,
    private val messageConsumer: MessageConsumer,
    private var transportObserver: TransportListener?
) : Transport {
    /**
     * Transport Constructor.
     */
    companion object {
        private const val TAG = "Transport"

        fun create(
            serverInfo: NuguServerInfo,
            dnsLookup: DnsLookup?,
            authDelegate: AuthDelegate,
            messageConsumer: MessageConsumer,
            transportObserver: TransportListener
        ): Transport {
            return HTTP2Transport(
                serverInfo,
                dnsLookup,
                authDelegate,
                messageConsumer,
                transportObserver
            )
        }
    }

    private var state: TransportState = TransportState()
    private var deviceGatewayClient: DeviceGatewayTransport? = null
    private var isHandOff = false
    private var registryClient = RegistryClient(serverInfo, dnsLookup)
    private val executor = Executors.newSingleThreadExecutor()

    /** @return the detail state **/
    private fun getDetailedState() = state.getDetailedState()

    init {
        serverInfo.checkServerSettings()
    }

    /**
     * connect from deviceGatewayClient and registryClient.
     */
    override fun connect(): Boolean {
        if (state.isConnectedOrConnecting()) {
            return false
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
            setState(DetailedState.FAILED, ChangedReason.INVALID_AUTH)
        } ?: return false

        if (DetailedState.CONNECTING_REGISTRY == getDetailedState()) {
            Logger.w(TAG, "[tryGetPolicy] Duplicate status")
            return false
        }
        setState(DetailedState.CONNECTING_REGISTRY)

        if(!serverInfo.keepConnection) {
            Logger.d(TAG,"[tryGetPolicy] Skip getPolicy call because keepConnection is false.")
            return tryConnectToDeviceGateway(RegistryClient.DefaultPolicy(serverInfo))
        }

        registryClient.getPolicy(authDelegate, object :
            RegistryClient.Observer {
            override fun onCompleted(policy: Policy?) {
                // succeeded, then it should be connection to DeviceGateway
                policy?.let {
                    tryConnectToDeviceGateway(it)
                } ?: setState(DetailedState.FAILED, ChangedReason.UNRECOVERABLE_ERROR)
            }

            override fun onError(reason: ChangedReason) {
                when (reason) {
                    ChangedReason.INVALID_AUTH -> {
                        setState(DetailedState.FAILED, ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        setState(DetailedState.FAILED, reason)
                    }
                }
            }
        })

        return true
    }

    private val deviceGatewayObserver = object :
        DeviceGatewayTransport.TransportObserver {
        override fun onConnected() {
            setState(DetailedState.CONNECTED)

            if (isHandOff) {
                isHandOff = false
                Logger.d(TAG, "[onConnected] Handoff changed : $isHandOff")
            }
        }

        override fun onError(reason: ChangedReason) {
            when (reason) {
                ChangedReason.SUCCESS -> { /* nothing to do */
                }
                ChangedReason.INVALID_AUTH -> setState(DetailedState.FAILED, reason)
                else -> {
                    // if the handoffConnection fails, the registry must be retry.
                    if (!isHandOff) {
                        setState(DetailedState.FAILED, reason)
                    } else {
                        setState(DetailedState.RECONNECTING, reason)
                        tryGetPolicy()
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
    private fun tryConnectToDeviceGateway(policy: Policy): Boolean {
        checkAuthorizationIfEmpty {
            setState(DetailedState.FAILED, ChangedReason.INVALID_AUTH)
        } ?: return false

        isHandOff = getDetailedState() == DetailedState.HANDOFF

        setState(DetailedState.CONNECTING_DEVICEGATEWAY)

        deviceGatewayClient?.let {
            Logger.w(TAG, "[tryConnectToDeviceGateway] deviceGatewayClient is not null")
            deviceGatewayClient?.shutdown()
        }

        DeviceGatewayClient.create(
            policy,
            serverInfo.keepConnection,
            messageConsumer,
            deviceGatewayObserver,
            authDelegate
        ).also {
            deviceGatewayClient = it
            return it.connect()
        }
    }

    /**
     * Check for Authorization
     * @param block Invoked only when authorization is NullOrBlank.
     * @return the authorization
     */
    private fun checkAuthorizationIfEmpty(block: () -> Unit): String? {
        val authorization = authDelegate.getAuthorization()
        if (authorization.isNullOrBlank()) {
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

        // disconnect() was executed first because DetailedState.DISCONNECTED is called after DetailedState.DISCONNECTING.
        setState(DetailedState.DISCONNECTING, ChangedReason.CLIENT_REQUEST)
    }

    /**
     * Returns whether this object is currently connected.
     * @return true is CONNECTED.
     */
    override fun isConnected(): Boolean = state.isConnected()

    /**
     * Returns whether this object is currently connecting or connected.
     * @return true is CONNECTING or CONNECTED.
     */
    override fun isConnectedOrConnecting(): Boolean {
        return state.isConnectedOrConnecting()
    }

    override fun send(request: MessageRequest): Boolean {
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

        executor.submit {
            registryClient.shutdown()

            deviceGatewayClient?.shutdown()
            deviceGatewayClient = null

            // remove observer
            transportObserver = null
            // only internal
            // DISCONNECTED is not deliver because it's delivering state from DISCONNECTING
            setState(DetailedState.DISCONNECTED)
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
        charge: String
    ) {
        setState(DetailedState.HANDOFF, ChangedReason.SERVER_ENDPOINT_CHANGED)

        val healthCheckPolicy = RegistryClient.cachedPolicy?.healthCheckPolicy
        if (healthCheckPolicy == null) {
            Logger.d(TAG, "[handoffConnection] healthCheckPolicy is null")
            setState(DetailedState.FAILED, ChangedReason.UNRECOVERABLE_ERROR)
            return
        }

        executor.submit {
            val policy =
                Policy(
                    healthCheckPolicy = healthCheckPolicy,
                    serverPolicy = listOf(
                        ServerPolicy(
                            protocol = protocol,
                            hostname = hostname,
                            port = port,
                            retryCountLimit = retryCountLimit,
                            connectionTimeout = connectionTimeout,
                            charge = charge
                        )
                    )
                )
            tryConnectToDeviceGateway(policy)
        }
    }

    /**
     * Set the state to a new state.
     */
    private fun setState(
        newDetailedState: DetailedState,
        reason: ChangedReason = ChangedReason.NONE
    ): Boolean {
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
                allowed = DetailedState.IDLE == getDetailedState()
            }
            DetailedState.CONNECTING_REGISTRY -> {
                allowed =
                    DetailedState.CONNECTING == getDetailedState() || DetailedState.RECONNECTING == getDetailedState()
            }
            DetailedState.CONNECTING_DEVICEGATEWAY -> {
                allowed =
                    getDetailedState() == DetailedState.CONNECTING_REGISTRY || getDetailedState() == DetailedState.HANDOFF
            }
            DetailedState.HANDOFF -> {
                allowed = DetailedState.IDLE == getDetailedState()
            }
            DetailedState.CONNECTED -> {
                allowed =
                    getDetailedState() == DetailedState.CONNECTING_DEVICEGATEWAY || getDetailedState() == DetailedState.RECONNECTING
            }
            DetailedState.DISCONNECTING -> {
                allowed = getDetailedState() != DetailedState.DISCONNECTED &&
                        getDetailedState() != DetailedState.FAILED &&
                        getDetailedState() != DetailedState.IDLE
            }
            DetailedState.DISCONNECTED -> {
                allowed =
                    getDetailedState() == DetailedState.CONNECTED || getDetailedState() == DetailedState.DISCONNECTING
            }
            DetailedState.RECONNECTING -> {
                allowed =
                    getDetailedState() == DetailedState.CONNECTING_DEVICEGATEWAY || getDetailedState() == DetailedState.CONNECTED
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

        // Update state
        state.setDetailedState(newDetailedState, reason)

        // Perform status processing for Observer delivery
        when (TransportState.fromDetailedState(newDetailedState)) {
            ConnectionStatusListener.Status.CONNECTING -> {
                transportObserver?.onConnecting(this, reason)
            }
            ConnectionStatusListener.Status.CONNECTED -> {
                transportObserver?.onConnected(this)
            }
            ConnectionStatusListener.Status.DISCONNECTED -> {
                transportObserver?.onDisconnected(this, reason)
            }
        }
        return true
    }
}
