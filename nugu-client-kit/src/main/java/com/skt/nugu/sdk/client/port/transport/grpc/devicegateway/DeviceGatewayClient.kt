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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.google.protobuf.ByteString
import com.skt.nugu.sdk.client.port.transport.grpc.Policy
import com.skt.nugu.sdk.client.port.transport.grpc.ServerPolicy
import com.skt.nugu.sdk.client.port.transport.grpc.utils.BackOff
import com.skt.nugu.sdk.client.port.transport.grpc.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.client.port.transport.grpc.HeaderClientInterceptor
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.ManagedChannel
import io.grpc.Status
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import com.skt.nugu.sdk.core.interfaces.message.Status as SDKStatus

/**
 *  Implementation of DeviceGateway
 **/
internal class DeviceGatewayClient(policy: Policy,
                                   private var messageConsumer: MessageConsumer?,
                                   private var transportObserver: Observer?,
                                   private val authorization: String?,
                                   var isHandOff: Boolean)
    : Transport
    , PingService.Observer
    , EventStreamService.Observer
    , HeaderClientInterceptor.Delegate {
    companion object {
        private const val TAG = "DeviceGatewayClient"
        private const val maxAsyncCallsSize = 100
    }

    private val policies = ConcurrentLinkedQueue(policy.serverPolicy)
    private var backoff : BackOff = BackOff.DEFAULT()

    private var currentChannel: ManagedChannel? = null

    private var pingService: PingService? = null
    private var eventStreamService: EventStreamService? = null
    private var crashReportService: CrashReportService? = null
    private var currentPolicy : ServerPolicy? = nextPolicy()
    private var healthCheckPolicy = policy.healthCheckPolicy

    private val isConnected = AtomicBoolean(false)

    private var eventMessageHeaders: Map<String, String>? = null

    var asyncCalls = Collections.synchronizedMap(object : LinkedHashMap<String, Call>() {
        private val serialVersionUID = 301077066599181567L
        override fun removeEldestEntry(p0: MutableMap.MutableEntry<String, Call>?): Boolean {
            return size > maxAsyncCallsSize
        }
    })

    interface Observer {
        fun onConnected()
        fun onReconnecting(reason: ChangedReason = ChangedReason.NONE)
        fun onError(reason: ChangedReason)
    }

    /**
     * Set a policy.
     * @return the ServerPolicy
     */
    private fun nextPolicy(): ServerPolicy? {
        backoff.reset()
        currentPolicy = policies.poll()
        currentPolicy?.let {
            backoff = BackOff.Builder(maxAttempts = it.retryCountLimit).build()
        }
        return currentPolicy
    }

    /**
     * Connect to DeviceGateway.
     * @return true is success, otherwise false
     */
    override fun connect(): Boolean {
        if (isConnected.get()) {
            return false
        }

        val policy = currentPolicy ?: run {
            Logger.w(TAG, "[connect] no more policy")
            transportObserver?.onError(
                ChangedReason.UNRECOVERABLE_ERROR
            )
            return false
        }

        policy.apply {
            currentChannel = ChannelBuilderUtils.createChannelBuilderWith(this, authorization, this@DeviceGatewayClient).build()
            currentChannel.also {
                pingService = PingService(VoiceServiceGrpc.newBlockingStub(it),
                    healthCheckPolicy,
                    observer = this@DeviceGatewayClient
                )
                eventStreamService = EventStreamService(VoiceServiceGrpc.newStub(it),
                    observer = this@DeviceGatewayClient
                )
                crashReportService = CrashReportService(VoiceServiceGrpc.newBlockingStub(it))
            }
        }
        return true
    }

    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        pingService?.shutdown()
        pingService = null
        eventStreamService?.shutdown()
        eventStreamService = null
        crashReportService?.shutdown()
        crashReportService = null
        ChannelBuilderUtils.shutdown(currentChannel)
        currentChannel = null
        eventMessageHeaders = null
        isConnected.set(false)
    }

    /**
     * Returns whether this object is currently connected to DeviceGateway.
     */
    override fun isConnected(): Boolean = isConnected.get()

    override fun isConnectedOrConnecting(): Boolean {
        throw NotImplementedError("not implemented")
    }

    /**
     * Sends an message request.
     * @param request the messageRequest to be sent
     * @return true is success, otherwise false
     */
    override fun send(call: Call): Boolean {
        val event = eventStreamService
        val crash = crashReportService
        val request = call.request()

        val result = when(request) {
            is AttachmentMessageRequest -> {
                val result = event?.sendAttachmentMessage(toProtobufMessage(request)) ?: false
                if(result) {
                    call.result(SDKStatus.OK)
                }
                result
            }
            is EventMessageRequest -> {
                asyncCalls[request.dialogRequestId] = call
                call.headers()?.let {
                    eventMessageHeaders = it
                }
                event?.sendEventMessage(toProtobufMessage(request))
            }
            is CrashReportMessageRequest -> {
                val result = crash?.sendCrashReport(request) ?: false
                if(result) {
                    call.result(SDKStatus.OK)
                }
                result
            }
            else -> false
        } ?: false

        Logger.d(TAG, "sendMessage : ${toStringMessage(request)}, result : $result")
        return result
    }

    /**
     * Receive an error.
     * @param the status of grpc
     */
    override fun onError(status: Status) {
        Logger.w(TAG, "[onError] Error : ${status.code}")

        when(status.code) {
            Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED -> {
                // nothing to do
            }
            else -> {
                transportObserver?.onReconnecting( when(status.code) {
                    Status.Code.OK -> ChangedReason.SUCCESS
                    Status.Code.UNAVAILABLE -> {
                        var cause = status.cause
                        var reason =
                            if (isConnected.get()) ChangedReason.SERVER_SIDE_DISCONNECT
                            else ChangedReason.CONNECTION_TIMEDOUT
                        while (cause != null) {
                            if (cause is UnknownHostException) {
                                reason = ChangedReason.DNS_TIMEDOUT
                            } else if( cause is ConnectException) {
                                reason = ChangedReason.CONNECTION_TIMEDOUT
                                //ECONNREFUSED, ENETUNREACH
                            }
                            cause = cause.cause
                        }
                        reason
                    }
                    Status.Code.UNKNOWN -> ChangedReason.SERVER_SIDE_DISCONNECT
                    Status.Code.DEADLINE_EXCEEDED -> {
                        if (isConnected.get()) ChangedReason.PING_TIMEDOUT
                        else ChangedReason.CONNECTION_TIMEDOUT
                    }
                    Status.Code.UNIMPLEMENTED -> ChangedReason.FAILURE_PROTOCOL_ERROR
                    Status.Code.NOT_FOUND ,
                    Status.Code.ALREADY_EXISTS ,
                    Status.Code.RESOURCE_EXHAUSTED ,
                    Status.Code.FAILED_PRECONDITION ,
                    Status.Code.ABORTED ,
                    Status.Code.INTERNAL -> ChangedReason.SERVER_INTERNAL_ERROR
                    Status.Code.OUT_OF_RANGE,
                    Status.Code.DATA_LOSS,
                    Status.Code.CANCELLED,
                    Status.Code.INVALID_ARGUMENT -> ChangedReason.INTERNAL_ERROR
                    else -> {
                        throw NotImplementedError()
                    }
                })
            }
        }

        disconnect()
        backoff.awaitRetry(status.code, object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                Logger.w(TAG, "[awaitRetry] Error : $error")

                when (status.code) {
                    Status.Code.PERMISSION_DENIED,
                    Status.Code.UNAUTHENTICATED -> {
                        transportObserver?.onError(ChangedReason.INVALID_AUTH)
                    }
                    else -> {
                        nextPolicy()
                        disconnect()
                        connect()
                    }
                }
            }

            override fun onRetry(retriesAttempted: Int) {
                if (isConnected.get()) {
                    Logger.w(TAG, "[awaitRetry] connected")
                } else {
                    disconnect()
                    connect()
                }
            }
        })
    }

    override fun shutdown() {
        Logger.d(TAG, "[shutdown]")
        messageConsumer = null
        transportObserver = null

        disconnect()
        backoff.reset()
    }

    /**
     * Notification that sending a ping to DeviceGateway has been acknowledged by DeviceGateway.
     */
    override fun onPingRequestAcknowledged() {
        if(isConnected.compareAndSet(false, true)) {
            backoff.reset()
            transportObserver?.onConnected()
        }
        Logger.d(TAG, "onPingRequestAcknowledged")
    }

    override fun onReceiveDirectives(directiveMessage: DirectiveMessage) {
        val message = convertToDirectives(directiveMessage)
        val dialogRequestId =  message.first().header.dialogRequestId
        asyncCalls[dialogRequestId]?.result(SDKStatus.OK)
        if(asyncCalls[dialogRequestId]?.isCanceled() != true) {
            messageConsumer?.consumeDirectives(message)
        }
    }

    private fun convertToDirectives(directiveMessage: DirectiveMessage): List<com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage> {
        val directives = ArrayList<com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage>()

        directiveMessage.directivesList.forEach {
            directives.add(com.skt.nugu.sdk.core.interfaces.message.DirectiveMessage(convertHeader(it.header), it.payload))
        }

        return directives
    }

    override fun onReceiveAttachment(attachmentMessage: AttachmentMessage) {
        val message = convertToAttachmentMessage(attachmentMessage)
        val dialogRequestId = message.header.dialogRequestId
        asyncCalls[dialogRequestId]?.result(SDKStatus.OK)
        if(asyncCalls[dialogRequestId]?.isCanceled() != true) {
            messageConsumer?.consumeAttachment(message)
        }
    }

    private fun convertToAttachmentMessage(attachmentMessage: AttachmentMessage): com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage {
        return with(attachmentMessage.attachment) {
            com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage(
                content.asReadOnlyByteBuffer(),
                convertHeader(header),
                isEnd,
                parentMessageId,
                seq,
                mediaType
            )
        }
    }

    private fun convertHeader(header: Header): com.skt.nugu.sdk.core.interfaces.message.Header = with(header) {
        com.skt.nugu.sdk.core.interfaces.message.Header(
            dialogRequestId,
            messageId,
            name,
            namespace,
            version,
            referrerDialogRequestId
        )
    }

    private fun toProtobufMessage(request: AttachmentMessageRequest): AttachmentMessage {
        with(request) {
            val attachment = Attachment.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .setReferrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
                .setParentMessageId(parentMessageId)
                .setSeq(seq)
                .setIsEnd(isEnd)
                .setMediaType(mediaType)
                .setContent(
                    if (byteArray != null) {
                        ByteString.copyFrom(ByteBuffer.wrap(byteArray))
                    } else {
                        ByteString.EMPTY
                    }
                )
                .build()

            return AttachmentMessage.newBuilder()
                .setAttachment(attachment).build()
        }
    }

    private fun toProtobufMessage(request: EventMessageRequest): EventMessage {
        with(request) {
            val event = Event.newBuilder()
                .setHeader(
                    Header.newBuilder()
                        .setNamespace(namespace)
                        .setName(name)
                        .setMessageId(messageId)
                        .setDialogRequestId(dialogRequestId)
                        .setVersion(version)
                        .setReferrerDialogRequestId(referrerDialogRequestId)
                        .build()
                )
                .setPayload(payload)
                .build()

            return EventMessage.newBuilder()
                .setContext(context)
                .setEvent(event)
                .build()
        }
    }

    private fun toStringMessage(request: MessageRequest) : String {
        return when(request) {
            is AttachmentMessageRequest -> {
                StringBuilder().apply {
                    append(request.javaClass.simpleName)
                    append("(")
                        append("messageId=${request.messageId}")
                        append(",dialogRequestId=${request.dialogRequestId}")
                        append("context=${request.context}")
                        append(",namespace=${request.namespace}")
                        append(",name=${request.name}")
                        append(",version=${request.version}")
                        append(",referrerDialogRequestId=${request.referrerDialogRequestId}")
                        append(",seq=${request.seq}")
                        append(",isEnd=${request.isEnd}")
                        append(",parentMessageId=${request.parentMessageId}")
                        append(",mediaType=${request.mediaType}")
                        append(",content size=${request.byteArray?.size}")
                    append(")")
                }.toString()
            }
            else -> request.toString()
        }
    }

    override fun newCall(
        activeTransport: Transport?,
        request: MessageRequest,
        headers: Map<String, String>?,
        listener: MessageSender.OnSendMessageListener
    ): Call {
        throw NotImplementedError()
    }

    override fun getHeaders(): Map<String, String>? {
        return eventMessageHeaders
    }
}