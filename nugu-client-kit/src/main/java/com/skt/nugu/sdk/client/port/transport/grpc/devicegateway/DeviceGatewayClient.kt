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
import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.utils.BackOff
import com.skt.nugu.sdk.client.port.transport.grpc.utils.ChannelBuilderUtils
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.network.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.network.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.ManagedChannel
import io.grpc.Status
import java.util.concurrent.ConcurrentLinkedQueue

/**
 *  Implementation of DeviceGateway
 **/
class DeviceGatewayClient(policyResponse: PolicyResponse,
                          private val messageConsumer: MessageConsumer,
                          private val transportObserver: TransportListener,
                          private val authorization: String)
    : Transport
    , PingService.Observer
    , EventStreamService.Observer {
    companion object {
        private const val TAG = "DeviceGatewayClient"
    }

    private val policies = ConcurrentLinkedQueue(policyResponse.serverPolicyList)
    private var backoff : BackOff = BackOff.DEFAULT()

    private var currentChannel: ManagedChannel? = null

    private var pingService: PingService? = null
    private var eventStreamService: EventStreamService? = null
    private var crashReportService: CrashReportService? = null
    private var serverPolicy : PolicyResponse.ServerPolicy? = null
    private var healthCheckPolicy = policyResponse.healthCheckPolicy
    @Volatile
    private var isConnected = false

    override fun connect(): Boolean {
        if(isConnected) {
            return false
        }

        serverPolicy ?: run {
            backoff.reset()

            policies.poll()?.apply {
                serverPolicy = this
            } ?: run {
                Logger.d(TAG, "No more serverPolicy")
                shutdown()
                transportObserver.onDisconnected(this, ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR)
                return false
            }

            backoff = BackOff.Builder(maxAttempts = serverPolicy!!.retryCountLimit).build()
        }

        serverPolicy?.apply {
            val option = Options(
                address = this.address,
                port = this.port,
                retryCountLimit = this.retryCountLimit,
                connectionTimeout = this.connectionTimeout,
                hostname = this.hostName
            )
            currentChannel =
                ChannelBuilderUtils.createChannelBuilderWith(option, authorization).build()
            currentChannel.also {
                pingService = PingService(VoiceServiceGrpc.newBlockingStub(it), healthCheckPolicy, observer = this@DeviceGatewayClient)
                eventStreamService = EventStreamService(VoiceServiceGrpc.newStub(it), observer = this@DeviceGatewayClient)
                crashReportService = CrashReportService(VoiceServiceGrpc.newBlockingStub(it))
            }
        }

        return true
    }

    override fun disconnect() {
        pingService?.shutdown()
        eventStreamService?.shutdown()
        crashReportService?.shutdown()
        currentChannel?.shutdownNow()
        isConnected = false
    }

    override fun isConnected(): Boolean = isConnected

    override fun send(request: MessageRequest) : Boolean {
        val event = eventStreamService ?: return false
        val crash = crashReportService ?: return false

        when(request) {
            is AttachmentMessageRequest -> event.sendAttachmentMessage(toProtobufMessage(request))
            is EventMessageRequest -> event.sendEventMessage(toProtobufMessage(request))
            is CrashReportMessageRequest -> crash.sendCrashReport(request)
            else -> { return false }
        }
        return true
    }

    override fun onError(code: Status.Code) {
        Logger.w(TAG, "[awaitRetry] Error : $code")

        if(isConnected) {
            isConnected = false
            transportObserver.onDisconnected(this, when(code) {
                Status.Code.OK -> ConnectionStatusListener.ChangedReason.SUCCESS
                Status.Code.CANCELLED -> ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
                Status.Code.UNAVAILABLE,
                Status.Code.UNKNOWN,
                Status.Code.INVALID_ARGUMENT -> ConnectionStatusListener.ChangedReason.SERVER_SIDE_DISCONNECT
                Status.Code.DEADLINE_EXCEEDED -> ConnectionStatusListener.ChangedReason.READ_TIMEDOUT
                Status.Code.NOT_FOUND ,
                Status.Code.ALREADY_EXISTS ,
                Status.Code.PERMISSION_DENIED ,
                Status.Code.RESOURCE_EXHAUSTED ,
                Status.Code.FAILED_PRECONDITION ,
                Status.Code.ABORTED ,
                Status.Code.OUT_OF_RANGE ,
                Status.Code.UNIMPLEMENTED ,
                Status.Code.INTERNAL ,
                Status.Code.DATA_LOSS -> ConnectionStatusListener.ChangedReason.INTERNAL_ERROR
                Status.Code.UNAUTHENTICATED -> ConnectionStatusListener.ChangedReason.INVALID_AUTH
            })
        }

        backoff.awaitRetry(code, object : BackOff.Observer {

            override fun onError(reason: String) {
                Logger.w(TAG, "[awaitRetry] Error : $reason")
                // clear serverPolicy, This means handoff.
                serverPolicy = null
                disconnect()
                connect()
            }

            override fun onRetry(retriesAttempted: Int) {
                if (isConnected) {
                    Logger.w(TAG, "[awaitRetry] connected")
                } else {
                    disconnect()
                    connect()
                }
            }
        })
    }

    override fun shutdown() {
        backoff.reset()
        disconnect()
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
        shutdown()
    }

    override fun onPingRequestAcknowledged() {
        if(!isConnected) {
            isConnected = true
            transportObserver.onConnected(this)

            backoff.reset()
        }
        Logger.d(TAG, "onPingRequestAcknowledged")
    }

    override fun onReceiveDirectives(json: String) {
        messageConsumer.consumeMessage(json)
    }

    override fun onReceiveAttachment(json: String) {
        messageConsumer.consumeMessage(json)
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
                        .build()
                )
                .setSeq(seq)
                .setIsEnd(isEnd)
                .setContent(
                    if (byteArray != null) {
                        ByteString.copyFrom(byteArray)
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
}