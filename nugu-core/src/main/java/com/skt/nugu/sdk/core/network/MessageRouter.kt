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
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.Status.Companion.withDescription
import com.skt.nugu.sdk.core.interfaces.transport.FixedStateCall

/**
 * This class which specifies the interface to manage an connection over DeviceGateway.
 */
class MessageRouter(
    private val transportFactory: TransportFactory,
    private val authDelegate: AuthDelegate
) : MessageRouterInterface, TransportListener, MessageConsumer,
    MessageSender.OnSendMessageListener {
    companion object {
        private const val TAG = "MessageRouter"
    }

    /** The current active transport */
    private var activeTransport: Transport? = null

    /** The handoff transport */
    private var handoffTransport: Transport? = null

    /** The observer object.*/
    private var observer: MessageRouterObserverInterface? = null

    /**
     * The listener for MessageSender
     */
    private val messageSenderListeners = CopyOnWriteArraySet<MessageSender.OnSendMessageListener>()

    /** The current connection status. */
    private var status: ConnectionStatusListener.Status =
        ConnectionStatusListener.Status.DISCONNECTED
    private var reason: ConnectionStatusListener.ChangedReason =
        ConnectionStatusListener.ChangedReason.NONE

    /**
     * lock for create transport
     */
    private val lock = ReentrantLock()

    /**
     * disconnect all transport
     * @return true is successful disconnect call, otherwise false
     */
    private fun disconnectAllTransport(): Boolean {
        return lock.withLock {
            if (activeTransport == null && handoffTransport == null) {
                false
            } else {
                activeTransport?.disconnect()
                handoffTransport?.disconnect()
                true
            }
        }
    }

    /**
     * create a new transport
     */
    private fun createActiveTransport() {
        lock.withLock {
            // Shutdown a previous activeTransport
            if (activeTransport != null) {
                activeTransport?.shutdown()
                activeTransport = null
            }
            // Release ServerInitiatedDirective if not started
            if (!sidController.isStarted()) {
                sidController.release()
            }
            transportFactory.createTransport(
                authDelegate = authDelegate,
                messageConsumer = this,
                transportObserver = this,
                isStartReceiveServerInitiatedDirective = isStartReceiveServerInitiatedDirective
            ).apply {
                activeTransport = this
            }
        }.connect()
    }

    override fun shutdown() {
        if(!disconnectAllTransport()) {
            setConnectionStatus(
                ConnectionStatusListener.Status.DISCONNECTED,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
            )
        }
        sidController.release()
    }

    /**
     * Set the observer to this object.
     */
    override fun setObserver(observer: MessageRouterObserverInterface) {
        this.observer = observer
    }

    /**
     * Prepares the [MessageRequest] to be executed at some point in the future.
     */
    override fun newCall(request: MessageRequest, headers: Map<String, String>?): Call {
        if(activeTransport.isNotInitialized()) {
            createActiveTransport()
        }
        return activeTransport?.newCall(activeTransport, request, headers, this) ?: FixedStateCall(
            Status(
                Status.Code.FAILED_PRECONDITION
            ).withDescription("Transport is not initialized"), request, this
        )
    }

    override fun addOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        messageSenderListeners.add(listener)
    }

    override fun removeOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        messageSenderListeners.remove(listener)
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
     * Get the status of the connection.
     */
    override fun getConnectionStatus(): ConnectionStatusListener.Status {
        return this.status
    }

    /**
     * Get the status of the connection.
     */
    override fun getConnectionChangedReason(): ConnectionStatusListener.ChangedReason {
        return this.reason
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
        sidController.notifyOnCompletionListener()
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
        Logger.d(TAG, "[onDisconnected] transport=$transport, activeTransport=$activeTransport, reason=$reason")
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

    override fun consumeDirectives(directives: List<DirectiveMessage>) {
        observer?.receiveDirectives(directives)
    }

    override fun consumeAttachment(attachment: AttachmentMessage) {
        observer?.receiveAttachment(attachment)
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
            // Canceling a previous handoff in progress
            if (handoffTransport != null) {
                handoffTransport?.shutdown()
                handoffTransport = null
            }
            transportFactory.createTransport(
                authDelegate = authDelegate,
                messageConsumer = this,
                transportObserver = this,
                isStartReceiveServerInitiatedDirective = isStartReceiveServerInitiatedDirective
            ).apply {
                handoffTransport = this
            }
        }.handoffConnection(
            protocol,
            hostname,
            address,
            port,
            retryCountLimit,
            connectionTimeout,
            charge
        )
    }

    override fun resetConnection(description: String?) {
        Logger.d(TAG, "[resetConnection] description=$description")
        createActiveTransport()
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        val builder = StringBuilder("MessageRouter : ")
            .append("activeTransport: ").append(activeTransport)
            .append(", handoffTransport: ").append(handoffTransport)
            .append(", observer: ").append(observer)
            .append(", messageSenderListeners: ").append(messageSenderListeners.size)
            .append(", status: ").append(status)
            .append(", reason: ").append(reason)
        return builder.toString()
    }

    override fun onPreSendMessage(request: MessageRequest) {
        messageSenderListeners.forEach {
            it.onPreSendMessage(request)
        }
    }

    override fun onPostSendMessage(request: MessageRequest, status: Status) {
        messageSenderListeners.forEach {
            it.onPostSendMessage(request, status)
        }
    }

    private var sidController: ServerInitiatedDirectiveController = ServerInitiatedDirectiveController(TAG)
    override fun startReceiveServerInitiatedDirective(onCompletion: (() -> Unit)?) : Boolean {
        if(sidController.isStarted()) {
            sidController.release()
        }
        sidController.setOnCompletionListener(onCompletion)
        if(!sidController.start(activeTransport)) {
            setConnectionStatus(
                ConnectionStatusListener.Status.CONNECTING,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
            )
            createActiveTransport()
        }
        return true
    }

    override fun stopReceiveServerInitiatedDirective() = sidController.stop(activeTransport)
    override fun isStartReceiveServerInitiatedDirective() = sidController.isStarted()
    private val isStartReceiveServerInitiatedDirective: () -> Boolean = {
        sidController.isStarted()
    }
    private fun Transport?.isNotInitialized() = this == null
}