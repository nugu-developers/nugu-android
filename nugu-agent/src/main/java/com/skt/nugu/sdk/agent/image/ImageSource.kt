package com.skt.nugu.sdk.agent.image

interface ImageSource {
    fun getByteArray(): ByteArray?
    fun getMediaType(): String
}