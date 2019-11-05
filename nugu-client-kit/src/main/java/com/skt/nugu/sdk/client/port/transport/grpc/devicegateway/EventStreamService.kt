package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Field
import java.util.concurrent.locks.Lock
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

    interface Observer {
        fun onReceiveDirectives(json: String)
        fun onReceiveAttachment(json: String)
        fun onError(code: Status.Code)
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
                Logger.e(TAG, "[EventStreamService] onError : $t")
                val status = Status.fromThrowable(t)
                observer.onError(status.code)
            }

            override fun onCompleted() {
                Logger.e(TAG, "[EventStreamService] onCompleted")
                shutdown()
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
        })
    }

    fun sendAttachmentMessage(attachment: AttachmentMessage) {
        eventStream?.onNext(
            Upstream.newBuilder()
                .setAttachmentMessage(attachment)
                .build()
        )
    }

    fun sendEventMessage(event: EventMessage) {
        eventStream?.onNext(
            Upstream.newBuilder()
                .setEventMessage(event)
                .build()
        )
    }

    fun shutdown() {
        eventStream?.onCompleted()
    }
}