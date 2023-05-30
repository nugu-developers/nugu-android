package com.skt.nugu.sdk.agent.image

import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

interface ImageAgent {
    fun sendImage(source: ImageSource, callback: SendImageCallback? = null)

    interface SendImageCallback {
        fun onEventRequestCreated(request: EventMessageRequest)
        fun onEventRequestFailed(request: EventMessageRequest, status: Status)
        fun onEventRequestSuccess(request: EventMessageRequest)
        fun onImageSourceRetrieved(source: ImageSource, byteArray: ByteArray)
        fun onImageSourceRetrieveFailed(source: ImageSource)
        fun onImageSourceSent(source: ImageSource, byteArray: ByteArray, offset: Long, size: Long)
        fun onImageSendFailed()
        fun onImageSendCompleted()
    }
}