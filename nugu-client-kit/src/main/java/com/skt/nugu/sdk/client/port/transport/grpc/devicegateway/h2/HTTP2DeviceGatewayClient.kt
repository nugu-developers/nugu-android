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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.h2

import com.google.gson.GsonBuilder
import com.google.protobuf.ByteString
import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.DeviceGateway
import com.skt.nugu.sdk.client.port.transport.grpc.utils.BackOff
import com.skt.nugu.sdk.client.port.transport.grpc.utils.SecurityInterceptor
import com.skt.nugu.sdk.client.port.transport.grpc.utils.UserAgentInterceptor
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener.ChangedReason
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.SdkVersion
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Protocol
import devicegateway.grpc.*
import io.grpc.ExperimentalApi
import io.grpc.Status
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  Implementation of DeviceGateway with http2
 **/
@ExperimentalApi("The stability has not been verified")
internal class HTTP2DeviceGatewayClient(private val policyResponse: PolicyResponse,
                                        private var messageConsumer: MessageConsumer?,
                                        private var transportObserver: DeviceGateway.Observer?,
                                        private val authDelegate: AuthDelegate)
    : DeviceGateway
    , DownStreamService.Observer
    , UpStreamService.Observer
    , PingService.Observer{
    companion object {
        private const val TAG = "HTTP2DeviceGatewayClient"

        fun create(
            policyResponse: PolicyResponse,
            messageConsumer: MessageConsumer?,
            transportObserver: DeviceGateway.Observer?,
            authDelegate: AuthDelegate
        ) = HTTP2DeviceGatewayClient(
            policyResponse,
            messageConsumer, transportObserver, authDelegate
        )
    }

    private val policies = ConcurrentLinkedQueue(policyResponse.serverPolicyList)
    private var backoff : BackOff = BackOff.DEFAULT()

    private var downStreamService: DownStreamService? = null
    private var upStreamService: UpStreamService? = null
    private var pingService: PingService? = null
    private var currentPolicy : PolicyResponse.ServerPolicy? = nextPolicy()
    private var healthCheckPolicy = policyResponse.healthCheckPolicy

    private val isConnected = AtomicBoolean(false)

    /**
     * Set a policy.
     * @return the ServerPolicy
     */
    private fun nextPolicy(): PolicyResponse.ServerPolicy? {
        backoff.reset()
        currentPolicy = policies.poll()
        currentPolicy?.let {
            backoff = BackOff.Builder(maxAttempts = it.retryCountLimit).build()
        }
        return currentPolicy
    }


    lateinit var client: OkHttpClient
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
            val option = Options(
                address = this.address,
                retryCountLimit= this.retryCountLimit,
                port = this.port,
                connectionTimeout = this.connectionTimeout,
                hostname = this.hostName,
                charge = this.charge.toString(),
                protocol = this.protocol.toString()
            )
            val client = OkHttpClient().apply {
                protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
                setReadTimeout(0, TimeUnit.MINUTES)
                setWriteTimeout(0, TimeUnit.MINUTES)
                setConnectTimeout(0, TimeUnit.MINUTES)
                interceptors().add(
                    SecurityInterceptor(
                        authDelegate
                    )
                )
                networkInterceptors().add(
                    UserAgentInterceptor(
                        "OpenSDK/" + SdkVersion.currentVersion
                    )
                )
            }
            downStreamService = DownStreamService.create(option, client, this@HTTP2DeviceGatewayClient)
            upStreamService = UpStreamService.create(option, client, this@HTTP2DeviceGatewayClient)
            pingService = PingService.create(option, client, healthCheckPolicy, this@HTTP2DeviceGatewayClient)
        }
        return true
    }

    /**
     * disconnect from DeviceGateway
     */
    override fun disconnect() {
        downStreamService?.shutdown()
        downStreamService = null
        upStreamService?.shutdown()
        upStreamService = null
        pingService?.shutdown()
        pingService = null
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
    override fun send(request: MessageRequest) : Boolean {
        val event = upStreamService

        val result = when(request) {
            is AttachmentMessageRequest -> event?.sendAttachmentMessage(request)
            is EventMessageRequest -> event?.sendEventMessage(request)
            is CrashReportMessageRequest -> event?.sendCrashReport(request)
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

        isConnected.compareAndSet(true, false)

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

    override fun onReceiveDirectives(json: String) {
        messageConsumer?.consumeMessage(json)
    }

    override fun onReceiveAttachment(json: String) {
        messageConsumer?.consumeMessage(json)
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

    private fun toProtobufMessage(request: EventMessageRequest): String {
       /* with(request) {
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
        }*/
        return GsonBuilder().create().toJson(request)
    }

    private fun toStringMessage(request: MessageRequest) : String {
        return when(request) {
            is AttachmentMessageRequest -> {
                val temp = request.copy()
                temp.byteArray = null
                temp.toString() + "byteArray size = " + request.byteArray?.size
            }
            else -> request.toString()
        }
    }
}