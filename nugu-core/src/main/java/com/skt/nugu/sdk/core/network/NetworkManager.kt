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
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.*
import java.util.concurrent.CopyOnWriteArraySet

/**
 * This class is designed to manage connections.
 */
class NetworkManager private constructor(
    private val messageRouter: MessageRouterInterface
) : ConnectionManagerInterface,
    MessageSender by messageRouter,
    MessageRouterObserverInterface {

    companion object {
        /**
         * Create a [NetworkManager]
         * @return a [NetworkManager] instance
         */
        fun create(messageRouter: MessageRouterInterface): NetworkManager {
            val connectionManager = NetworkManager(messageRouter)

            messageRouter.setObserver(connectionManager)

            return connectionManager
        }
    }

    private val messageObservers = CopyOnWriteArraySet<MessageObserver>()
    private val connectionStatusObservers = CopyOnWriteArraySet<ConnectionStatusListener>()
    /**
     * Initiate a connection to DeviceGateway.
     */
    override fun enable(quiet: Boolean) = Unit

    /**
     * Disconnect from DeviceGateway.
     */
    override fun disable() {
        shutdown()
    }

    /**
     * Disconnect from DeviceGateway.
     */
    override fun shutdown() {
        messageRouter.shutdown()
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    override fun isConnected(): Boolean =
        messageRouter.getConnectionStatus() == ConnectionStatusListener.Status.CONNECTED

    /**
     * Adds an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to add.
     */
    override fun addMessageObserver(observer: MessageObserver) {
        messageObservers.add(observer)
    }

    /**
     * Removes an observer to be notified when a message arrives from DeviceGateway.
     * @param observer The observer to remove.
     */
    override fun removeMessageObserver(observer: MessageObserver) {
        messageObservers.remove(observer)
    }

    /**
     * Adds an observer to be notified of connection status changes.
     * @param observer The observer to add.
     */
    override fun addConnectionStatusListener(listener: ConnectionStatusListener) {
        connectionStatusObservers.add(listener)
        listener.onConnectionStatusChanged(
            messageRouter.getConnectionStatus(),
            ConnectionStatusListener.ChangedReason.NONE
        )
    }

    /**
     * Removes an observer from being notified of connection status changes.
     * @param observer The observer to remove.
     */
    override fun removeConnectionStatusListener(listener: ConnectionStatusListener) {
        connectionStatusObservers.remove(listener)
    }

    /**
     * Receives the connection status changes.
     */
    override fun onConnectionStatusChanged(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        connectionStatusObservers.forEach { it.onConnectionStatusChanged(status,reason) }
    }

    override fun receiveDirectives(directives: List<DirectiveMessage>) {
        messageObservers.forEach {
            it.receiveDirectives(directives)
        }
    }

    override fun receiveAttachment(attachment: AttachmentMessage) {
        messageObservers.forEach {
            it.receiveAttachment(attachment)
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
        messageRouter.handoffConnection(protocol, hostname, address, port, retryCountLimit, connectionTimeout, charge)
    }

    /**
     * Resets the connection immediately.
     */
    override fun resetConnection(description: String?) {
        messageRouter.resetConnection(description)
    }

    /**
     * Start the connection-oriented feature.
     * @param onCompletion This indicates that the reconnection with the server is complete and the message is ready to be sent.
     * @return success or not
     */
    override fun startReceiveServerInitiatedDirective(onCompletion: (() -> Unit)?) =
        messageRouter.startReceiveServerInitiatedDirective(onCompletion)

    /**
     * Stop the connection-oriented feature.
     */
    override fun stopReceiveServerInitiatedDirective() =
        messageRouter.stopReceiveServerInitiatedDirective()

    /**
     * Return whether the connection-oriented has been started.
     */
    override fun isStartReceiveServerInitiatedDirective() =
        messageRouter.isStartReceiveServerInitiatedDirective()
}