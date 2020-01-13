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
package com.skt.nugu.sdk.core.network

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.capability.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class which specifies the interface to manage an connection over DeviceGateway.
 */
class MessageRouter(
    private val transportFactory: TransportFactory,
    private val authDelegate: AuthDelegate
) : MessageRouterInterface, TransportListener, MessageConsumer{
    companion object {
        private const val TAG = "MessageRouter"
    }

    /** The current active transport */
    private var activeTransport: Transport? = null
    /** The handoff transport */
    private var handoffTransport: Transport? = null

    /** The observer object.*/
    private var observer: MessageRouterObserverInterface? = null

    /** The current connection status. */
    private var status: ConnectionStatusListener.Status = ConnectionStatusListener.Status.DISCONNECTED
    private var reason: ConnectionStatusListener.ChangedReason = ConnectionStatusListener.ChangedReason.NONE
    /**
     * lock for create transport
     */
    private val lock = ReentrantLock()

    /**
     * Begin the process of establishing an DeviceGateway connection.
     */
    override fun enable() {
        val isConnectedOrConnecting = activeTransport?.isConnectedOrConnecting() ?: false
        if (!isConnectedOrConnecting) {
            setConnectionStatus(
                ConnectionStatusListener.Status.CONNECTING,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
            )
            createActiveTransport()
        }
    }

    /**
     * disconnect all transport
     */
    private fun disconnectAllTransport() {
        lock.withLock {
            activeTransport?.disconnect()
            handoffTransport?.disconnect()
        }
    }

    /**
     * create a new transport
     */
    private fun createActiveTransport() {
        lock.withLock {
            transportFactory.createTransport(authDelegate, this, this).apply {
                activeTransport = this
            }
        }.connect()
    }

    /**
     * Close the DeviceGateway connection.
     */
    override fun disable() {
        disconnectAllTransport()
    }

    /**
     * Set the observer to this object.
     */
    override fun setObserver(observer: MessageRouterObserverInterface) {
        this.observer = observer
    }

    /**
     * Expect to have the message sent to the transport.
     * @param messageRequest the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun sendMessage(messageRequest: MessageRequest) : Boolean {
        return activeTransport?.send(messageRequest) ?: false

    }

    /**
     * Notify the connection observer when the status has changed.
     */
    private fun notifyObserverOnConnectionStatusChanged(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        observer?.onConnectionStatusChanged(status, reason)
    }

    /**
     * Notify the receive observer when the message has changed.
     */
    private fun notifyObserverOnReceived(message: String) {
        observer?.receive(message)
    }

    /**
     * Get the status of the connection.
     */
    override fun getConnectionStatus(): ConnectionStatusListener.Status {
        return this.status
    }

    /**
     * Set the connection state. If it changes, notify the connection observer.
     */
    private fun setConnectionStatus(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        if (status != this.status) {
            this.status = status
            this.reason = reason
            notifyObserverOnConnectionStatusChanged(status, reason)
        }
    }

    /**
     * Notify the onConnected observer When connected.
     * @see [setConnectionStatus]
     * @param transport is interface.....  not used yet..
     */
    override fun onConnected(transport: Transport) {
        Logger.d(TAG, "[onConnected] $transport")

        // Switch from handoffTransport to activeTransport.
        lock.withLock {
            if (handoffTransport == transport) {
                activeTransport?.shutdown()
                activeTransport = handoffTransport
                handoffTransport = null
            }
        }

        setConnectionStatus(
            ConnectionStatusListener.Status.CONNECTED,
            ConnectionStatusListener.ChangedReason.SUCCESS
        )
    }

    /**
     * Notify the onDisconnected observer When disconnected.
     * @see [setConnectionStatus]
     * @param transport is Interface
     */
    override fun onDisconnected(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        lock.withLock {
            if (transport == activeTransport) {
                activeTransport?.shutdown()
                activeTransport = null
            } else if (transport == handoffTransport) {
                // handoff fails
                handoffTransport?.shutdown()
                handoffTransport = null
                activeTransport?.shutdown()
                activeTransport = null
            }
        }
        setConnectionStatus(ConnectionStatusListener.Status.DISCONNECTED, reason)
    }

    /**
     * Notify the onConnecting observer When connecting.
     * @see [setConnectionStatus]
     * @param transport is Interface
     */
    override fun onConnecting(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        setConnectionStatus(
            ConnectionStatusListener.Status.CONNECTING,
            reason
        )
    }

    /**
     * receive the message from transport, then it is notify
     * @see [setConnectionStatus]
     * @param message the message received
     */
    override fun consumeMessage(message: String) {
        notifyObserverOnReceived(message)
    }

    /**
     * forwarding Handoff to transport
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
        lock.withLock {
            transportFactory.createTransport(authDelegate, this, this).apply {
                handoffTransport = this
            }
        }.handoffConnection(protocol, hostname, address, port, retryCountLimit, connectionTimeout, charge)
    }
}