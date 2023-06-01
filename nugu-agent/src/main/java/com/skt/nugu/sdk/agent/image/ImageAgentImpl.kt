package com.skt.nugu.sdk.agent.image

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.BaseContextState
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

class ImageAgentImpl(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
) : CapabilityAgent, SupportedInterfaceContextProvider, ImageAgent {
    companion object {
        private const val TAG = "ImageAgent"

        const val NAMESPACE = "Image"
        val VERSION = Version(1, 0)

        const val EVENT_NAME_SEND_IMAGE = "SendImage"

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    private val senderExecutors = Executors.newSingleThreadExecutor()

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )
        contextSetter.setState(
            namespaceAndName,
            contextState,
            StateRefreshPolicy.NEVER,
            contextType,
            stateRequestToken
        )
    }

    override fun sendImage(source: ImageSource, callback: ImageAgent.SendImageCallback?): ImageAgent.SendImageRequest {
        val request = object: ImageAgent.SendImageRequest {
            override val dialogRequestId: String = UUIDGeneration.timeUUID().toString()
        }

        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                senderExecutors.submit {
                    val eventRequest = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE, EVENT_NAME_SEND_IMAGE, VERSION.toString()
                    ).dialogRequestId(request.dialogRequestId).isStreaming(true)
                        .build()

                    // send image event async, if we send this as sync, then will be timeout occur.
                    // because of attachment not sent.
                    messageSender.newCall(eventRequest).enqueue(null)
                    sendAttachment(eventRequest, callback)
                }
            }

            private fun sendAttachment(
                request: EventMessageRequest,
                callback: ImageAgent.SendImageCallback?
            ) {
                val sourceByteArray = source.getByteArray()
                val inputStream = sourceByteArray?.let {
                    ByteArrayInputStream(it)
                } ?: kotlin.run {
                    callback?.onImageSourceRetrieveFailed(source)
                    callback?.onImageSendFailed()
                    return
                }

                callback?.onImageSourceRetrieved(source, sourceByteArray)

                inputStream.use {
                    val tempBuffer = ByteArray(8192)
                    var totalRead = 0
                    var read: Int
                    var currentAttachmentSequenceNumber = 0
                    var isEnd = false
                    var isFailed = false

                    while (!isEnd && !isFailed) {
                        val offset = totalRead
                        read = it.read(tempBuffer)
                        val currentRead = read

                        if (read > -1) {
                            totalRead += read
                        } else {
                            isEnd = true
                        }

                        val attachment = AttachmentMessageRequest(
                            UUIDGeneration.timeUUID().toString(),
                            request.dialogRequestId,
                            request.context,
                            request.namespace,
                            request.name,
                            request.version,
                            request.dialogRequestId,
                            currentAttachmentSequenceNumber,
                            isEnd,
                            request.messageId,
                            source.getMediaType(),
                            if(isEnd) {
                                null
                            } else {
                                ByteArray(read).apply {
                                    System.arraycopy(tempBuffer, 0, this, 0, read)
                                }
                            }
                        )

                        currentAttachmentSequenceNumber++

                        val status = messageSender.newCall(attachment).execute()

                        if(status.isOk()) {
                            callback?.onImageSourceSent(
                                source,
                                sourceByteArray,
                                offset.toLong(),
                                currentRead.toLong()
                            )
                        } else {
                            isFailed = true
                        }
                    }

                    if (isFailed) {
                        callback?.onImageSendFailed()
                    }

                    if (isEnd) {
                        callback?.onImageSendSuccess()
                    }
                }
            }
        })

        return request
    }
}