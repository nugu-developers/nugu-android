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

import com.google.gson.*
import com.skt.nugu.sdk.client.port.transport.grpc.utils.UnderscoresNamingStrategy
import com.skt.nugu.sdk.client.port.transport.grpc.utils.UnknownFieldsExclusionStrategy
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class is designed to manage eventstream of DeviceGateway
 */
internal class EventStreamService(
    asyncStub: VoiceServiceGrpc.VoiceServiceStub,
    private val observer: Observer
) {
    companion object {
        private const val TAG = "EventStreamService"
    }
    private val streamLock = ReentrantLock()
    private val isShutdown = AtomicBoolean(false)
    val gson = GsonBuilder().setFieldNamingStrategy(UnderscoresNamingStrategy())
        .addSerializationExclusionStrategy(UnknownFieldsExclusionStrategy())
        .create()

    interface Observer {
        fun onReceiveDirectives(json: JsonObject)
        fun onReceiveAttachment(json: JsonObject)
        fun onError(status: Status)
    }

    private val eventStream: StreamObserver<Upstream>? by lazy {
        //Returns a new stub
        asyncStub.withWaitForReady().eventStream(object : StreamObserver<Downstream> {
            override fun onNext(downstream: Downstream) {
                Logger.d(TAG, "[EventStreamService] onNext : ${downstream.messageCase}")
                when (downstream.messageCase) {
                    Downstream.MessageCase.DIRECTIVE_MESSAGE -> {
                        downstream.directiveMessage?.let {
                            if (it.directivesCount > 0) {
                                try {
                                    gson.toJsonTree(downstream.directiveMessage).asJsonObject.let {
                                        observer.onReceiveDirectives(it)
                                    }
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "[directiveMessage] Error occurred while parsing json, $e")
                                }
                            }
                            if(checkIfDirectiveIsUnauthorizedRequestException(it)) {
                                observer.onError(Status.UNAUTHENTICATED)
                            }
                        }
                    }
                    Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                        downstream.attachmentMessage?.let {
                            if (it.hasAttachment()) {
                                try {
                                    gson.toJsonTree(downstream.attachmentMessage).asJsonObject.let {
                                        observer.onReceiveAttachment(it)
                                    }
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "[attachmentMessage] Error occurred while parsing json, $e")
                                }
                            }
                        }
                    }
                    else -> {
                        // nothing
                    }
                }
            }

            override fun onError(t: Throwable) {
                if(!isShutdown.get()) {
                    val status = Status.fromThrowable(t)
                    Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
                    observer.onError(status)
                }
            }

            override fun onCompleted() {
                if(!isShutdown.get()) {
                    Logger.d(TAG, "[onCompleted] Stream is completed")
                    observer.onError(Status.UNKNOWN)
                }
            }

            private fun checkIfDirectiveIsUnauthorizedRequestException(directiveMessage: DirectiveMessage): Boolean {
                val namespace = "System"
                val name = "Exception"
                val code = "UNAUTHORIZED_REQUEST_EXCEPTION"

                directiveMessage.directivesOrBuilderList.forEach {
                    if (it.header.namespace == namespace && it.header.name == name) {
                        if (it.payload.contains(code)) {
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    fun sendAttachmentMessage(attachment: AttachmentMessage) : Boolean {
        try {
            streamLock.withLock {
                eventStream?.onNext(
                    Upstream.newBuilder()
                        .setAttachmentMessage(attachment)
                        .build()
                )
            }
        } catch (ignored: IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            return false
        }
        return true
    }

    fun sendEventMessage(event: EventMessage) : Boolean {
        Logger.d(TAG, "[sendEventMessage]")
        try {
            streamLock.withLock {
                eventStream?.onNext(
                    Upstream.newBuilder()
                        .setEventMessage(event)
                        .build()
                )
            }
        } catch (ignored : IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            return false
        }
        return true
    }

    fun shutdown() {
        if(isShutdown.compareAndSet(false, true)) {
            try {
                streamLock.withLock {
                    eventStream?.onCompleted()
                }
            } catch (ignored : IllegalStateException) {
                // Perhaps, call already half-closed
            }
        }
        else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }
    }
}