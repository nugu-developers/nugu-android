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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.grpc

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

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
    private val isShutdown = AtomicBoolean(false)

    interface Observer {
        fun onReceiveDirectives(json: String)
        fun onReceiveAttachment(json: String)
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
                                observer.onReceiveDirectives(toJson(downstream.directiveMessage))
                            }
                            if(checkIfDirectiveIsUnauthorizedRequestException(it)) {
                                observer.onError(Status.UNAUTHENTICATED)
                            }
                        }
                    }
                    Downstream.MessageCase.ATTACHMENT_MESSAGE -> {
                        downstream.attachmentMessage?.let {
                            if (it.hasAttachment()) {
                                observer.onReceiveAttachment(toJson(downstream.attachmentMessage))
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
            eventStream?.onNext(
                Upstream.newBuilder()
                    .setAttachmentMessage(attachment)
                    .build()
            )
        } catch (ignored: IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            return false
        }
        return true
    }

    fun sendEventMessage(event: EventMessage) : Boolean {
        try {
            eventStream?.onNext(
                Upstream.newBuilder()
                    .setEventMessage(event)
                    .build()
            )
        } catch (ignored : IllegalStateException) {
            // Perhaps, Stream is already completed, no further calls are allowed
            return false
        }
        return true
    }

    fun shutdown() {
        if(isShutdown.compareAndSet(false, true)) {
            try {
                eventStream?.onCompleted()
            } catch (ignored : IllegalStateException) {
                // Perhaps, call already half-closed
            }
        }
        else {
            Logger.w(TAG, "[shutdown] already shutdown")
        }

    }
}