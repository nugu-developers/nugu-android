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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.system.ExceptionDirective
import com.skt.nugu.sdk.agent.system.ExceptionDirectiveDelegate
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.system.handler.RevokeDirectiveHandler
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class DefaultSystemAgent(
    messageSender: MessageSender,
    connectionManager: ConnectionManagerInterface,
    contextManager: ContextManagerInterface,
    directiveSequencer: DirectiveSequencerInterface,
    private val exceptionDirectiveDelegate: ExceptionDirectiveDelegate?
) : AbstractSystemAgent(
    messageSender,
    connectionManager,
    contextManager
), RevokeDirectiveHandler.Controller {

    /**
     * This class handles providing configuration for the System Capability agent
     */
    companion object {
        private const val TAG = "DefaultSystemCapabilityAgent"

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
        const val NAME_HANDOFF_CONNECTION = "HandoffConnection"
        const val NAME_TURN_OFF = "TurnOff"
        const val NAME_UPDATE_STATE = "UpdateState"
        const val NAME_EXCEPTION = "Exception"
        const val NAME_ECHO = "Echo"
        const val NAME_NO_DIRECTIVES = "NoDirectives"
        const val NAME_NOOP = "Noop"
        const val NAME_RESET_CONNECTION = "ResetConnection"

        /** events */
        const val EVENT_NAME_SYNCHRONIZE_STATE = "SynchronizeState"
        const val EVENT_NAME_ECHO = "Echo"

        val HANDOFF_CONNECTION = NamespaceAndName(
            NAMESPACE,
            NAME_HANDOFF_CONNECTION
        )
        val TURN_OFF = NamespaceAndName(
            NAMESPACE,
            NAME_TURN_OFF
        )
        val UPDATE_STATE = NamespaceAndName(
            NAMESPACE,
            NAME_UPDATE_STATE
        )
        val EXCEPTION = NamespaceAndName(
            NAMESPACE,
            NAME_EXCEPTION
        )
        val ECHO = NamespaceAndName(
            NAMESPACE,
            NAME_ECHO
        )
        val NO_DIRECTIVES = NamespaceAndName(
            NAMESPACE,
            NAME_NO_DIRECTIVES
        )
        val NOOP = NamespaceAndName(
            NAMESPACE,
            NAME_NOOP
        )
        val RESET_CONNECTION = NamespaceAndName(
            NAMESPACE,
            NAME_RESET_CONNECTION
        )

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val observers = HashSet<SystemAgentInterface.Listener>()

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    init {
        /**
         * Performs initialization.
         */
        directiveSequencer.addDirectiveHandler(this)
        directiveSequencer.addDirectiveHandler(RevokeDirectiveHandler(this))
        contextManager.setStateProvider(namespaceAndName, this)
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

    internal data class ResetConnectionPayload(
        @SerializedName("description")
        val description: String?
    )

    /**
     * Shut down the Impl.
     */
    override fun shutdown() = Unit

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val nonBlockingPolicy = BlockingPolicy.sharedInstanceFactory.get()

        this[HANDOFF_CONNECTION] = nonBlockingPolicy
        this[TURN_OFF] = nonBlockingPolicy
        this[UPDATE_STATE] = nonBlockingPolicy
        this[EXCEPTION] = nonBlockingPolicy
        this[ECHO] = nonBlockingPolicy
        this[NO_DIRECTIVES] = nonBlockingPolicy
        this[NOOP] = nonBlockingPolicy
        this[RESET_CONNECTION] = nonBlockingPolicy
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        contextSetter.setState(
            namespaceAndName,
            contextState,
            StateRefreshPolicy.NEVER,
            contextType,
            stateRequestToken
        )
    }

    /**
     * Handle the action specified by the directive
     * @param info The directive currently being handled.
     * @see [AbstractCapabilityAgent]
     */
    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[handleDirective] $info")

        when (info.directive.getName()) {
            NAME_HANDOFF_CONNECTION -> handleHandoffConnection(info)
            NAME_TURN_OFF -> handleTurnOff(info)
            NAME_UPDATE_STATE -> handleUpdateState(info)
            NAME_EXCEPTION -> handleException(info)
            NAME_ECHO -> handleEcho(info)
            NAME_RESET_CONNECTION -> handleResetConnection(info)
            NAME_NO_DIRECTIVES, NAME_NOOP -> {
            }
        }
        setHandlingCompleted(info)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    /**
     * Registry Connection Handoff to [com.skt.nugu.core.network.transport.Transport.onHandoffConnection]
     * disconnect and reconnect to the device
     * @param info The directive currently being handled.
     */
    private fun handleHandoffConnection(info: DirectiveInfo) {
        Logger.d(TAG, "[handleHandoffConnection] $info")
        val payload =
            MessageFactory.create(info.directive.payload, HandoffConnectionPayload::class.java)
        if (payload == null) {
            Logger.d(
                TAG,
                "[handleHandoffConnection] invalid payload: ${info.directive.payload}"
            )
            return
        }

        executor.submit {
            connectionManager.handoffConnection(
                payload.protocol,
                payload.hostname,
                payload.address,
                payload.port,
                payload.retryCountLimit,
                payload.connectionTimeout,
                payload.charge
            )
        }
    }

    /**
     * Power Off
     * @param info The directive currently being handled.
     */
    private fun handleTurnOff(info: DirectiveInfo) {
        Logger.d(TAG, "[handleTurnOff] $info")
        executor.submit {
            connectionManager.shutdown()
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
            with(info.directive.header) {
                executeSynchronizeStateEvent(
                    if (referrerDialogRequestId.isNullOrBlank()) {
                        dialogRequestId
                    } else {
                        referrerDialogRequestId
                    }
                )
            }
        }
    }

    /**
     * An event is fired when an invalid request or exception occurs on the server
     * @param info The directive currently being handled.
     */
    private fun handleException(info: DirectiveInfo) {
        Logger.d(TAG, "[handleException] $info")
        executor.submit {
            val payload =
                MessageFactory.create(info.directive.payload, ExceptionDirective.Payload::class.java)
            if (payload != null ) {
                val exceptionCode = try {
                    SystemAgentInterface.ExceptionCode.valueOf(payload.code)
                } catch (e: Exception) {
                    // ignore
                    null
                }

                if(exceptionDirectiveDelegate == null) {
                    when (payload.code) {
                        CODE_UNAUTHORIZED_REQUEST_EXCEPTION,
                        CODE_PLAY_ROUTER_PROCESSING_EXCEPTION,
                        CODE_TTS_SPEAKING_EXCEPTION -> {
                            if (exceptionCode != null) {
                                observers.forEach {
                                    it.onException(exceptionCode, payload.description)
                                }
                            }
                        }
                        CODE_ASR_RECOGNIZING_EXCEPTION,
                        CODE_INTERNAL_SERVICE_EXCEPTION -> {
                        }
                    }

                } else {
                    exceptionDirectiveDelegate.onException(ExceptionDirective(info.directive.header, payload))
                }
                Logger.e(TAG, "EXCEPTION : ${payload.code}, ${payload.description}")
            }
        }
    }

    /**
     * Resets the connection immediately.
     * @param info The directive currently being handled.
     */
    private fun handleResetConnection(info: DirectiveInfo) {
        Logger.d(TAG, "[handleResetConnection] $info")
        val payload =
            MessageFactory.create(info.directive.payload, ResetConnectionPayload::class.java)
        if (payload == null) {
            Logger.d(
                TAG,
                "[handleResetConnection] invalid payload: ${info.directive.payload}"
            )
            return
        }

        executor.submit {
            connectionManager.resetConnection(payload.description)
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
        sendEvent(EVENT_NAME_ECHO)
    }

    private fun sendEvent(
        name: String,
        referrerDialogRequestId: String? = null,
        payload: String? = null,
        noAck: Boolean = false
    ) {
        val tempNamespaceAndName = if (name == EVENT_NAME_SYNCHRONIZE_STATE) {
            null
        } else {
            namespaceAndName
        }

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val call = messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION.toString())
                        .also {
                            if (payload != null) {
                                it.payload(payload)
                            }

                            if (referrerDialogRequestId != null) {
                                it.referrerDialogRequestId(referrerDialogRequestId)
                            }
                        }.build()
                )
                if(noAck) {
                    call.noAck()
                }
                call.enqueue(null)
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

    override fun onContextAvailable(jsonContext: String) {
        // no-op
    }

    override fun onContextFailure(
        error: ContextRequester.ContextRequestError,
        jsonContext: String
    ) {
        // no-op
    }

    /**
     * Executes a synchronize state in thread.
     */
    private fun executeSynchronizeStateEvent(referrerDialogRequestId: String? = null) {
        sendEvent(EVENT_NAME_SYNCHRONIZE_STATE, referrerDialogRequestId)
    }

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    override fun addListener(listener: SystemAgentInterface.Listener) {
        Logger.d(TAG, "[addListener] observer: $listener")
        executor.submit {
            observers.add(listener)
        }
    }

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    override fun removeListener(listener: SystemAgentInterface.Listener) {
        Logger.d(TAG, "[removeListener] observer: $listener")
        executor.submit {
            observers.remove(listener)
        }
    }

    override fun onRevoke(reason: SystemAgentInterface.RevokeReason) {
        executor.submit {
            observers.forEach { it.onRevoke(reason) }
        }
    }
}