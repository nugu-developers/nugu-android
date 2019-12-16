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
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class which specifies the interface to manage an connection over DeviceGateway.
 */
class MessageRouter(
    private val transportFactory: TransportFactory,
    private val authDelegate: AuthDelegate
) : MessageRouterInterface, TransportListener, MessageConsumer {
    companion object {
        private const val TAG = "MessageRouter"
    }

    /** The current active transport */
    var activeTransport: Transport? = null
    /** A list of all transports */
    private var transports = CopyOnWriteArraySet<Transport>()
    /** The observer object.*/
    private var observer: MessageRouterObserverInterface? = null
    /**
     * Returns the enabled status of MessageRouter
     * @return True if this MessageRouter is enabled, false otherwise.
     */
    private var isEnabled: Boolean = false
    /** The current connection status. */
    var status: ConnectionStatusListener.Status = ConnectionStatusListener.Status.DISCONNECTED
    var reason: ConnectionStatusListener.ChangedReason = ConnectionStatusListener.ChangedReason.NONE
    /**
     * lock for create transport
     */
    private val lock = ReentrantLock()

    /**
     * Begin the process of establishing an DeviceGateway connection.
     */
    override fun enable() {
        isEnabled = true

        disconnectAllTransport()

        val isConnected = activeTransport?.isConnected() ?: false
        if (!isConnected) {
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
    fun disconnectAllTransport() {
        lock.withLock {
            for (transport in transports) {
                transport.shutdown()
                transports.remove(transport)
            }
            activeTransport = null
        }
    }

    /**
     * disconnect all transport
     */
    fun disconnectTransport(transport: Transport) {
        lock.withLock {
            transports.filter { it == transport }.forEach {
                it.shutdown()
                transports.remove(it)
            }
            if (transport == activeTransport) {
                activeTransport = null
            }
        }
    }
    /**
     * create a new transport
     */
    private fun createActiveTransport() {
        lock.withLock {
            val transport = transportFactory.createTransport(authDelegate, this, this)
            if (transport.connect()) {
                transports.add(transport)
                activeTransport = transport
                Logger.d(TAG, "createActiveTransport ${transports.size}")
                return
            }
        }

        disconnectAllTransport()
        setConnectionStatus(
            ConnectionStatusListener.Status.DISCONNECTED,
            ConnectionStatusListener.ChangedReason.INTERNAL_ERROR
        )
    }

    /**
     * Close the DeviceGateway connection.
     */
    override fun disable() {
        isEnabled = false

        disconnectAllTransport()
        setConnectionStatus(
            ConnectionStatusListener.Status.DISCONNECTED,
            ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
        )
    }

    /**
     * Set the observer to this object.
     */
    override fun setObserver(observer: MessageRouterObserverInterface) {
        this.observer = observer
    }

    /**
     * Get the observer.
     * @return observer
     */
    private fun getObserver(): MessageRouterObserverInterface? {
        return this.observer
    }

    /**
     * Expect to have the message sent to the transport.
     * @param messageRequest the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun sendMessage(messageRequest: MessageRequest) : Boolean {
        if(!isEnabled) {
            Logger.d(TAG, "[sendMessage] isConnected : ${activeTransport?.isConnected()}")
            setConnectionStatus(
                ConnectionStatusListener.Status.DISCONNECTED,
                ConnectionStatusListener.ChangedReason.DISABLED
            )
            return false
        }
        return activeTransport?.send(messageRequest) ?: false
    }

    /**
     * Notify the connection observer when the status has changed.
     */
    private fun notifyObserverOnConnectionStatusChanged(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        val observer = getObserver()
        observer?.onConnectionStatusChanged(status, reason)
    }

    /**
     * Notify the receive observer when the message has changed.
     */
    private fun notifyObserverOnReceived(message: String) {
        val observer = getObserver()
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
    fun setConnectionStatus(
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

        setConnectionStatus(
            ConnectionStatusListener.Status.CONNECTED,
            ConnectionStatusListener.ChangedReason.SUCCESS
        )
        // for handoffConnection
        if( activeTransport == transport) {
            disconnectAllExceptTransport(transport)
        }
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
        Logger.d(TAG, "[onDisconnected] $transport / $reason")
        //disconnectTransport(transport)
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
     * Processing after handoffConnection
     */
    private fun disconnectAllExceptTransport(transport: Transport) {
        lock.withLock {
            transports.filter { it != transport }.forEach {
                it.shutdown()
                transports.remove(it)
            }
        }
    }

    /**
     * forwarding Handoff to transport
     */
    override fun handoffConnection(
        protocol: String,
        domain: String,
        hostname: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    ) {
        activeTransport?.let { disconnectAllExceptTransport(it) }

        lock.withLock {
            transportFactory.createTransport(authDelegate, this, this).apply {
                transports.add(this)
                activeTransport = this
            }.handoffConnection(protocol, domain, hostname, port, retryCountLimit, connectionTimeout, charge)
        }
    }
}