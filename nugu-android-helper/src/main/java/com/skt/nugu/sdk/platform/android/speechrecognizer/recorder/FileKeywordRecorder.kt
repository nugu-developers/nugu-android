package com.skt.nugu.sdk.platform.android.speechrecognizer.recorder

import android.content.Context
import com.skt.nugu.jademarblelib.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class ObbDirFileProvider(private val context: Context) : FileKeywordRecorder.FileProvider {
    companion object {
        private val TIME_FORMAT = SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.KOREA)
    }

    override fun createNewFile(): File = File(context.obbDir, "keyword_${TIME_FORMAT.format(Date())}.raw")
}

class FileKeywordRecorder(private val fileProvider: FileProvider): KeywordRecorder {
    companion object {
        private val TAG = "FileKeywordRecorder"
    }

    interface FileProvider {
        fun createNewFile(): File
    }

    private var openFileStream: OutputStream? = null

    override fun open(): Boolean {
        if(openFileStream != null) {
            return false
        }

        return try {
            openFileStream =
                FileOutputStream(fileProvider.createNewFile().apply {
                    Logger.d(TAG, "[open] ${this.absolutePath}")
                })
            true
        }catch (e: Exception) {
            Logger.w(TAG, "[open] failed", e)
            false
        }
    }

    override fun write(buffer: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        openFileStream?.write(buffer, offsetInBytes, sizeInBytes)
        return sizeInBytes
    }

    override fun close() {
        openFileStream?.close()
        openFileStream = null
    }
}