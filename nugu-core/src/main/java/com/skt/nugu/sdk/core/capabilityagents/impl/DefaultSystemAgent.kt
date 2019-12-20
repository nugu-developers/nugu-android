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
package com.skt.nugu.sdk.core.capabilityagents.impl

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.system.*
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

object DefaultSystemAgent {
    private const val TAG = "DefaultSystemCapabilityAgent"

    val FACTORY = object : SystemAgentFactory {
        /**
         * Create an instance of Impl
         * initializing is performed at default initializer
         */
        override fun create(
            messageSender: MessageSender,
            connectionManager: ConnectionManagerInterface,
            contextManager: ContextManagerInterface,
            batteryStatusProvider: BatteryStatusProvider?
        ): AbstractSystemAgent =
            Impl(
                messageSender,
                connectionManager,
                contextManager,
                batteryStatusProvider
            )
    }

    internal data class ExceptionPayload(
        @SerializedName("code")
        val code: String,
        @SerializedName("description")
        val description: String?
    )

    /**
     * This class handles providing configuration for the System Capability agent
     */
    internal class Impl constructor(
        messageSender: MessageSender,
        connectionManager: ConnectionManagerInterface,
        contextManager: ContextManagerInterface,
        batteryStatusProvider: BatteryStatusProvider? = null
    ) : AbstractSystemAgent(
        messageSender,
        connectionManager,
        contextManager,
        batteryStatusProvider
    ) {
        companion object {
            /** exceptions */
            /// The server encountered a runtime error.
            const val CODE_INTERNAL_SERVICE_EXCEPTION = "INTERNAL_SERVICE_EXCEPTION"
            /// The client is not authorized to use authorization codes.
            const val CODE_UNAUTHORIZED_REQUEST_EXCEPTION = "UNAUTHORIZED_REQUEST_EXCEPTION"
            /// The server encountered a runtime error during ASR processing.
            const val CODE_ASR_RECOGNIZING_EXCEPTION = "ASR_RECOGNIZING_EXCEPTION"
            /// The server encountered a runtime error during ROUTER processing.
            const val CODE_PLAY_ROUTER_PROCESSING_EXCEPTION = "PLAY_ROUTER_PROCESSING_EXCEPTION"
            /// The server encountered a runtime error during TTS processing.
            const val CODE_TTS_SPEAKING_EXCEPTION = "TTS_SPEAKING_EXCEPTION"

            /** directives */
            const val NAME_RESET_USER_INACTIVITY = "ResetUserInactivity"
            const val NAME_HANDOFF_CONNECTION = "HandoffConnection"
            const val NAME_TURN_OFF = "TurnOff"
            const val NAME_UPDATE_STATE = "UpdateState"
            const val NAME_EXCEPTION = "Exception"
            const val NAME_ECHO = "Echo"
            const val NAME_NO_DIRECTIVES = "NoDirectives"

            /** events */
            const val EVENT_NAME_SYNCHRONIZE_STATE = "SynchronizeState"
            const val EVENT_NAME_USER_INACTIVITY_REPORT = "UserInactivityReport"
            const val EVENT_NAME_DISCONNECT = "Disconnect"
            const val EVENT_NAME_ECHO = "Echo"

            val RESET_USER_INACTIVITY = NamespaceAndName(NAMESPACE,
                NAME_RESET_USER_INACTIVITY
            )
            val HANDOFF_CONNECTION = NamespaceAndName(NAMESPACE,
                NAME_HANDOFF_CONNECTION
            )
            val TURN_OFF = NamespaceAndName(NAMESPACE,
                NAME_TURN_OFF
            )
            val UPDATE_STATE = NamespaceAndName(NAMESPACE,
                NAME_UPDATE_STATE
            )
            val EXCEPTION = NamespaceAndName(NAMESPACE,
                NAME_EXCEPTION
            )
            val ECHO = NamespaceAndName(NAMESPACE,
                NAME_ECHO
            )
            val NO_DIRECTIVES = NamespaceAndName(NAMESPACE,
                NAME_NO_DIRECTIVES
            )

            private const val KEY_INACTIVITY_EVENT_PAYLOAD = "inactiveTimeInSeconds"
            const val SECONDS = 1000L
        }

        override val namespaceAndName: NamespaceAndName =
            NamespaceAndName("supportedInterfaces", NAMESPACE)

        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        private var inActiveFuture: ScheduledFuture<*>? = null
        //private var eventTimer = Timer()
        private var lastTimeActive = 0L

        private val executor = Executors.newSingleThreadExecutor()
        private val observers = HashSet<SystemAgentInterface.Listener>()

        init {
            /**
             * Performs initialization.
             */
            contextManager.setStateProvider(namespaceAndName, this)
            onUserActive()
            connectionManager.addConnectionStatusListener(this)
        }

        /**
         * Shut down the Impl.
         */
        override fun shutdown() {
            onUserDisconnect()
            inActiveFuture?.cancel(true)
            inActiveFuture = null
        }

        override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
            val nonBlockingPolicy = BlockingPolicy()

            val configuration = HashMap<NamespaceAndName, BlockingPolicy>()
            configuration[RESET_USER_INACTIVITY] = nonBlockingPolicy
            configuration[HANDOFF_CONNECTION] = nonBlockingPolicy
            configuration[TURN_OFF] = nonBlockingPolicy
            configuration[UPDATE_STATE] = nonBlockingPolicy
            configuration[EXCEPTION] = nonBlockingPolicy
            configuration[ECHO] = nonBlockingPolicy
            configuration[NO_DIRECTIVES] = nonBlockingPolicy

            return configuration
        }

        override fun preHandleDirective(info: DirectiveInfo) {
            // no-op
        }

        override fun cancelDirective(info: DirectiveInfo) {
            removeDirective(info)
        }

        private fun removeDirective(info: DirectiveInfo) {
            removeDirective(info.directive.getMessageId())
        }

        override fun provideState(
            contextSetter: ContextSetterInterface,
            namespaceAndName: NamespaceAndName,
            stateRequestToken: Int
        ) {
            contextSetter.setState(
                namespaceAndName,
                buildContext(),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }

        private fun buildContext(): String = JsonObject().apply {
            addProperty("version", VERSION)
            batteryStatusProvider?.let {
                val level = it.getBatteryLevel()
                if (level > 0) {
                    addProperty("battery", level)
                }
            }
        }.toString()

        /**
         * Handle the action specified by the directive
         * @param info The directive currently being handled.
         * @see [com.skt.nugu.core.common.base.CapabilityAgent]
         */
        override fun handleDirective(info: DirectiveInfo) {
            Logger.d(TAG, "[handleDirective] $info")
            when (info.directive.getName()) {
                NAME_RESET_USER_INACTIVITY -> handleResetUserInactivity(info)
                NAME_HANDOFF_CONNECTION -> handleHandoffConnection(info)
                NAME_TURN_OFF -> handleTurnOff(info)
                NAME_UPDATE_STATE -> handleUpdateState(info)
                NAME_EXCEPTION -> handleException(info)
                NAME_ECHO -> handleEcho(info)
                NAME_NO_DIRECTIVES -> {
                }
            }
            setHandlingCompleted(info)
        }

        private fun setHandlingCompleted(info: DirectiveInfo) {
            info.result.setCompleted()
            removeDirective(info)
        }

        /**
         * The ResetUserInactivity directive is sent to your client to reset the inactivity timer used by UserInactivityReport.
         * @param info The directive currently being handled.
         */
        private fun handleResetUserInactivity(info: DirectiveInfo) {
            Logger.d(TAG, "[handleResetUserInactivity] $info")
            executor.submit {
                onUserActive()
            }
        }

        internal data class HandoffConnectionPayload(
            @SerializedName("protocol")
            val protocol: String,
            @SerializedName("hostname")
            val hostname: String,
            @SerializedName("address")
            val address: String,
            @SerializedName("port")
            val port: Int,
            @SerializedName("retryCountLimit")
            val retryCountLimit: Int,
            @SerializedName("connectionTimeout")
            val connectionTimeout: Int,
            @SerializedName("charge")
            val charge: String
        )

        /**
         * Registry Connection Handoff to [com.skt.nugu.core.network.transport.Transport.onHandoffConnection]
         * disconnect and reconnect to the device
         * @param info The directive currently being handled.
         */
        private fun handleHandoffConnection(info: DirectiveInfo) {
            Logger.d(TAG, "[handleHandoffConnection] $info")
            val payload = MessageFactory.create(info.directive.payload, HandoffConnectionPayload::class.java)
            if(payload == null) {
                Logger.d(TAG, "[handleHandoffConnection] invalid payload: ${info.directive.payload}")
                return
            }

            executor.submit {
                connectionManager.handoffConnection(payload.protocol, payload.hostname, payload.address, payload.port, payload.retryCountLimit, payload.connectionTimeout, payload.charge)
            }
        }

        /**
         * Power Off
         * @param info The directive currently being handled.
         */
        private fun handleTurnOff(info: DirectiveInfo) {
            Logger.d(TAG, "[handleTurnOff] $info")
            executor.submit {
                executeDisconnectEvent()
                observers.forEach { it.onTurnOff() }
            }
        }

        /**
         * Request all status information of device as Context
         * @param info The directive currently being handled.
         */
        private fun handleUpdateState(info: DirectiveInfo) {
            Logger.d(TAG, "[handleUpdateState] $info")
            executor.submit {
                executeSynchronizeStateEvent()
            }
        }

        /**
         * An event is fired when an invalid request or exception occurs on the server
         * @param info The directive currently being handled.
         */
        private fun handleException(info: DirectiveInfo) {
            Logger.d(TAG, "[handleException] $info")
            executor.submit {
                val payload = MessageFactory.create(info.directive.payload, ExceptionPayload::class.java)
                if(payload != null) {
                    when (payload.code) {
                        CODE_UNAUTHORIZED_REQUEST_EXCEPTION,
                        CODE_PLAY_ROUTER_PROCESSING_EXCEPTION,
                        CODE_TTS_SPEAKING_EXCEPTION -> {
                            val exceptionCode = try {
                                SystemAgentInterface.ExceptionCode.valueOf(payload.code)
                            } catch (e: Exception){
                                // ignore
                                null
                            }

                            if(exceptionCode != null) {
                                observers.forEach {
                                    it.onException(exceptionCode, payload.description)
                                }
                            }
                        }
                        CODE_ASR_RECOGNIZING_EXCEPTION,
                        CODE_INTERNAL_SERVICE_EXCEPTION -> {
                        }
                    }
                    Logger.e(TAG, "EXCEPTION : ${payload.code}, ${payload.description}")
                }
            }
        }

        /**
         * @hide only internal
         * Receives the last transmitted directive from deviceGateway
         * Purpose for internal test
         */
        override fun onEcho() {
            executor.submit {
                executeEchoEvent()
            }
        }

        /**
         * Execute event in thread for echo
         * @see [onEcho]
         */
        private fun executeEchoEvent() {
            sendEvent(EVENT_NAME_ECHO, VERSION)
        }

        private fun sendEvent(name: String, payload: String? = null) {
            val tempNamespaceAndName = if(name == EVENT_NAME_SYNCHRONIZE_STATE) {
                null
            } else {
                namespaceAndName
            }

            contextManager.getContext(object : ContextRequester {
                override fun onContextAvailable(jsonContext: String) {
                    messageSender.sendMessage(
                        EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION).also {
                            if(payload != null) {
                                it.payload(payload)
                            }
                        }.build()
                    )
                }

                override fun onContextFailure(error: ContextRequester.ContextRequestError) {

                }
            }, tempNamespaceAndName)
        }

        /**
         * only logs
         * @see [onEcho]
         */
        private fun handleEcho(info: DirectiveInfo) {
            Logger.d(TAG, "[handleEcho] $info")
        }

        /**
         * The function to be called when the user has become active.
         * If a timeout(1hour) occurs, it is send to the server
         */
        override fun onUserActive() {
            this.inActiveFuture?.cancel(true)
            this.inActiveFuture = this.scheduler.scheduleAtFixedRate({
                executor.submit(this@Impl::executeInactivityReport)
            }, 1, 1, TimeUnit.HOURS)

            lastTimeActive = System.currentTimeMillis()
        }

        /**
         * Execute event in thread for InactivityReport
         */
        private fun executeInactivityReport() {
            val inactiveTimeInSeconds = (System.currentTimeMillis() - lastTimeActive) / SECONDS
            Logger.d(
                TAG,
                "[executeInactivityReport] inactiveTimeInSeconds = $inactiveTimeInSeconds"
            )
            sendEvent(EVENT_NAME_USER_INACTIVITY_REPORT, JsonObject().apply {
                addProperty(KEY_INACTIVITY_EVENT_PAYLOAD, inactiveTimeInSeconds)
            }.toString())
        }

        /**
         * Execute event in thread for user disconnect
         */
        private fun onUserDisconnect() {
            executor.submit(this@Impl::executeDisconnectEvent)
        }

        /**
         * Execute event in thread for synchronize state
         */
        private fun executeDisconnectEvent() {
            sendEvent(EVENT_NAME_DISCONNECT)
        }

        override fun onContextAvailable(jsonContext: String) {
            // no-op
        }

        override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            // no-op
        }

        /**
         * Executes a synchronize state in thread.
         */
        private fun executeSynchronizeStateEvent() {
            sendEvent(EVENT_NAME_SYNCHRONIZE_STATE)
        }

        /**
         * Receives the connection status changes.
         */
        override fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason: ConnectionStatusListener.ChangedReason) {
            if (status == ConnectionStatusListener.Status.CONNECTED) {
                executeSynchronizeStateEvent()
            }
        }

        /** Add a listener to be called when a state changed.
         * @param listener the listener that added
         */
        override fun addListener(listener: SystemAgentInterface.Listener) {
            Logger.d(TAG, "[addAuthStateListener] observer: $listener")
            executor.submit {
                observers.add(listener)
            }

        }

        /**
         * Remove a listener
         * @param listener the listener that removed
         */
        override fun removeListener(listener: SystemAgentInterface.Listener) {
            Logger.d(TAG, "[removeAuthStateListener] observer: $listener")
            executor.submit {
                observers.remove(listener)
            }
        }
    }
}