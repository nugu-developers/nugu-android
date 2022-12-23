package com.skt.nugu.sdk.platform.android.speechrecognizer.recorder

interface KeywordRecorder {
    fun open(): Boolean
    fun write(buffer: ByteArray, offsetInBytes:Int, sizeInBytes: Int): Int
    fun close()
}