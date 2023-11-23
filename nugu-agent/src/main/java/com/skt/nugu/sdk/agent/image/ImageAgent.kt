package com.skt.nugu.sdk.agent.image

interface ImageAgent {
    fun sendImage(source: ImageSource, service: String? = null, callback: SendImageCallback? = null): SendImageRequest

    interface SendImageRequest {
        val dialogRequestId: String
    }

    interface SendImageCallback {
        // Called when a image source's byte retrieved.
        fun onImageSourceRetrieved(source: ImageSource, byteArray: ByteArray)
        // Called when a image source's byte retrieving failed.
        fun onImageSourceRetrieveFailed(source: ImageSource)

        // Called when a image source's byte sent
        fun onImageSourceSent(source: ImageSource, byteArray: ByteArray, offset: Long, size: Long)

        // Called when send a image failed.
        fun onImageSendFailed()

        // Called when send a image success.
        // It does not mean that the image was processed normally on the server.
        fun onImageSendSuccess()
    }
}