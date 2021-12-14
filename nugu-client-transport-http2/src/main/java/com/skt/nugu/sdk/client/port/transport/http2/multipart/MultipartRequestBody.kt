/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.client.port.transport.http2.multipart

import com.skt.nugu.sdk.client.port.transport.http2.HttpHeaders.Companion.APPLICATION_JSON
import com.skt.nugu.sdk.core.utils.Logger
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.Pipe
import okio.buffer
import java.util.*
import com.skt.nugu.sdk.core.interfaces.message.Call as MessageCall

class MultipartRequestBody(
    private val boundary: ByteString,
    private val parts: Queue<Part>,
    private val pipe: Pipe,
    private val close : Boolean,
    public val call: MessageCall?
) : RequestBody() {
    override fun contentType(): MediaType = "multipart/form-data; boundary=${boundary.utf8()}".toMediaType()
    override fun isDuplex() = true
    private fun takeSink() = pipe.sink.buffer()
    private var cancel : Boolean = false

    init {
       write(close)
    }

    override fun writeTo(sink: BufferedSink) = pipe.fold(sink)
    fun isCanceled() : Boolean = cancel
    fun cancel() {
        cancel = true
        takeSink().apply {
            if(isOpen) {
                closeQuietly()
            }
        }
    }

    private fun write(close : Boolean): MultipartRequestBody {
        takeSink().let{ sink ->
            synchronized(parts) {
                var part: Part?
                while (parts.poll().also { part = it } != null) {
                    val headers = part!!.headers
                    val body = part!!.body

                    sink.buffer.apply {
                        write(DASHDASH)
                        write(boundary)
                        write(CRLF)
                    }
                    Logger.d("OkHttp", "\r\n--${boundary.toString()}\r\n")

                    if (headers != null) {
                        for (h in 0 until headers.size) {
                            sink.buffer.apply {
                                writeUtf8(headers.name(h))
                                write(COLONSPACE)
                                writeUtf8(headers.value(h))
                                write(CRLF)
                            }
                            Logger.d("OkHttp", "${headers.name(h)}: ${headers.value(h)}\r\n")
                        }
                    }

                    val contentType = body.contentType()
                    if (contentType != null) {
                        sink.buffer.apply {
                            writeUtf8("Content-Type: ")
                            writeUtf8(contentType.toString())
                            write(CRLF)
                        }
                        Logger.d("OkHttp", "Content-Type: ${contentType.toString()}\r\n")
                    }

                    val contentLength = body.contentLength()
                    if (contentLength != -1L) {
                        sink.buffer.apply {
                            writeUtf8("Content-Length: ")
                            writeDecimalLong(contentLength)
                            write(CRLF)
                        }
                        Logger.d("OkHttp", "Content-Length: ${contentLength}\r\n")
                    }

                    sink.buffer.apply {
                        write(CRLF)
                        body.writeTo(this)
                        write(CRLF)
                    }
                    Logger.d("OkHttp", "\r\n")
                    try {
                        sink.flush()
                    } catch (e :Throwable) {
                        Logger.d(TAG, e.message.toString())
                    }

                }
            }

            if(close) {
                sink.buffer.apply {
                    write(DASHDASH)
                    write(boundary)
                    write(DASHDASH)
                    write(CRLF)
                }
                Logger.d("OkHttp", "--${boundary.toString()}--\r\n")
                try {
                    sink.close()
                } catch (e :Throwable) {
                    Logger.d(TAG, e.message.toString())
                }
            }
        }
        return this
    }

    fun newBuilder() =
        Builder(
            this
        )

    class Part private constructor(
        val headers: Headers?,
        val body: RequestBody
    ) {
        companion object {
            fun create(body: RequestBody): Part =
                create(
                    null,
                    body
                )
            fun create(headers: Headers?, body: RequestBody): Part {
                require(headers?.get("Content-Type") == null) { "Unexpected header: Content-Type" }
                require(headers?.get("Content-Length") == null) { "Unexpected header: Content-Length" }
                return Part(
                    headers,
                    body
                )
            }

            fun createFormData(name: String, filename: String?, headers: Headers?, body: RequestBody): Part {
                val disposition = buildString {
                    append("form-data; name=")
                    appendQuotedString(name)

                    if (filename != null) {
                        append("; filename=")
                        appendQuotedString(filename)
                    }
                }
                val headersBuilder = headers?.newBuilder() ?: Headers.Builder()
                headersBuilder.addUnsafeNonAscii("Content-Disposition", disposition)

                return create(
                    headersBuilder.build(),
                    body
                )
            }
        }
    }

    open class Builder {
        companion object {
            val MAX_BUFFER_SIZE = 1024L * 8L
        }
        constructor()
        internal constructor(response: MultipartRequestBody) {
            this.boundary = response.boundary
            this.pipe = response.pipe
        }

        private var boundary: ByteString = UUID.randomUUID().toString().encodeUtf8()
        private val parts: Queue<Part> = ArrayDeque()
        private var pipe: Pipe = Pipe(MAX_BUFFER_SIZE)
        private var close : Boolean = true
        private var call: MessageCall? = null

        /** Add a form data part to the body. */
        fun addFormDataPart(name: String, filename: String? = null, headers: Headers? = null,  body: RequestBody) = apply {
            parts += Part.createFormData(
                name,
                filename,
                headers,
                body
            )
        }

        fun close(close: Boolean) = apply {
            this.close = close
        }
        fun call(call: MessageCall) = apply {
            this.call = call
        }
        /** Assemble the specified parts into a request body. */
        fun build(): MultipartRequestBody {
            check(parts.isNotEmpty()) { "Multipart body must have at least one part." }
            return MultipartRequestBody(
                boundary,
                parts,
                pipe,
                close,
                call
            )
        }
    }

    companion object {
        private val TAG = "MultipartRequestBody"

        private val COLONSPACE = byteArrayOf(':'.code.toByte(), ' '.code.toByte())
        private val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        private val DASHDASH = byteArrayOf('-'.code.toByte(), '-'.code.toByte())

        fun String.toMultipartRequestBody(name : String, close : Boolean, call: MessageCall) : MultipartRequestBody {
            return Builder()
                .addFormDataPart(name = name, body = this.toRequestBody(APPLICATION_JSON.toMediaType()))
                .close(close)
                .call(call)
                .build()
        }
        /**
         * Appends a quoted-string to a StringBuilder.
         *
         * RFC 2388 is rather vague about how one should escape special characters in form-data
         * parameters, and as it turns out Firefox and Chrome actually do rather different things, and
         * both say in their comments that they're not really sure what the right approach is. We go
         * with Chrome's behavior (which also experimentally seems to match what IE does), but if you
         * actually want to have a good chance of things working, please avoid double-quotes, newlines,
         * percent signs, and the like in your field names.
         */
        internal fun StringBuilder.appendQuotedString(key: String) {
            append('"')
            for (i in 0 until key.length) {
                when (val ch = key[i]) {
                    '\n' -> append("%0A")
                    '\r' -> append("%0D")
                    '"' -> append("%22")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
