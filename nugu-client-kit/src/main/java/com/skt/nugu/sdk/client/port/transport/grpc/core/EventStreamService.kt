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
package com.skt.nugu.sdk.client.port.transport.grpc.core

import com.google.gson.*
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.client.port.transport.grpc.Channels
import devicegateway.grpc.*
import io.grpc.stub.StreamObserver
import java.lang.reflect.Field
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class EventStreamService(
    val observer: GrpcServiceListener
) : GrpcServiceInterface {
    private var streamObserver: StreamObserver<Upstream>? = null
    private var channel: Channels? = null
    private val lock: Lock = ReentrantLock()

    companion object {
        private const val TAG = "GrpcTransport"
    }


    /**
     * Execute a request to the DeviceGateway.
     */

    fun sendCompleted() {
        getStream()?.onCompleted()
    }

    /**
     * Execute a request to the DeviceGateway.
     */

    fun sendAttachmentMessage(attachment: AttachmentMessage) {
        val request = Upstream.newBuilder()
            .setAttachmentMessage(attachment)
            .build()
        getStream()?.onNext(request)
    }

    /**
     * Execute a request to the DeviceGateway.
     */
    fun sendEventMessage(value: EventMessage) {
        val request = Upstream.newBuilder()
            .setEventMessage(value)
            .build()
        getStream()?.onNext(request)
        Logger.d(TAG, "[EventStreamService] $value ${getStream()}")
    }

    /**
     * The underlying channel of the stub.
     * @return the Upstream StreamObserver
     */
    private fun getStream(): StreamObserver<Upstream>? {
        if (streamObserver != null) {
            return streamObserver
        }

        VoiceServiceGrpc.newStub(channel?.getChannel()).withWaitForReady().apply {
            eventStream(object : StreamObserver<Downstream> {
                override fun onNext(downstream: Downstream) {
                    Logger.d(TAG, "[EventStreamService] onNext : ${downstream.messageCase}")
                    when (downstream.messageCase) {
                        Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                            downstream.directiveMessage?.let {
                                if (it.directivesCount > 0) {
                                    val json = toJson(downstream.directiveMessage)
                                    observer.onDirectives(json)
                                }
                            }
                        }
                        Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                            downstream.attachmentMessage?.let {
                                if (it.hasAttachment()) {
                                    val json = toJson(downstream.attachmentMessage)
                                    observer.onDirectives(json)
                                }
                            }
                        }
                        else -> {
                            TODO("not implemented")
                        }
                    }
                }

                override fun onError(t: Throwable) {
                    Logger.e(TAG, "[EventStreamService] onError : $t")
                    lock.withLock {
                        streamObserver = null
                    }
                    observer.onServerSideDisconnect()
                }

                override fun onCompleted() {
                    Logger.e(TAG, "[EventStreamService] onCompleted")
                    lock.withLock {
                        streamObserver = null
                    }
                }
            }).also {
                lock.withLock {
                    streamObserver = it
                }
            }
        }
        return streamObserver
    }

    /**
     * Initializes, Creates a new async stub and timeout for the EventStreamService.
     */
    override fun connect(channel : Channels) {
        this.channel = channel
    }

    /**
     * clean up
     */
    override fun shutdown() {
        lock.withLock {
            streamObserver?.onCompleted()
            streamObserver = null
        }
    }

    private fun toJson(src: Any): String {
        return GsonBuilder().setFieldNamingStrategy(UnderscoresNamingStrategy())
            .addSerializationExclusionStrategy(UnknownFieldsExclusionStrategy())
            .create().toJson(src)
    }
    // directives_ to directives
    inner class UnderscoresNamingStrategy : FieldNamingStrategy {
        override fun translateName(f: Field): String {
            val index = f.name.lastIndexOf("_")
            return if (index == -1 || index != f.name.lastIndex) {
                f.name
            } else {
                f.name.substring(0, index)
            }
        }
    }

    inner class UnknownFieldsExclusionStrategy : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes): Boolean {
            return when (f.name) {
                "unknownFields",
                "memoizedSerializedSize",
                "memoizedHashCode" -> true
                else -> false
            }
        }

        override fun shouldSkipClass(clazz: Class<*>): Boolean {
            return false
        }
    }

}