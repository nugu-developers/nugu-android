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

import java.util.HashMap
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.max

/** A utility class to handle multipart/mixed */
class MultipartParser(private val source: BufferedSource?, private val listener: Listener) {
    companion object {
        const val CRLF = "\r\n"
        const val DASHES = "--"
    }

    interface Listener {
        fun onResult(headers: Map<String, String>, body: Buffer)
    }

    private fun notifyOnResult(chunk: Buffer, listener: Listener) {
        val marker = (CRLF + CRLF).encodeUtf8()

        val indexOfMarker = chunk.indexOf(marker)
        if (indexOfMarker == -1L) {
            listener.onResult(HashMap(), chunk)
        } else {
            val headers = Buffer()
            val body = Buffer()
            chunk.read(headers, indexOfMarker)
            chunk.skip(marker.size.toLong())
            chunk.readAll(body)
            listener.onResult(parseHeaders(headers), body)
        }
    }

    private fun parseHeaders(data: Buffer): Map<String, String> {
        val headers = HashMap<String, String>()

        val text = data.readUtf8()
        val lines = text.split(CRLF.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            val index = line.indexOf(":")
            if (index != -1) {
                val headerName = line.substring(0, index).trim()
                val headerValue = line.substring(index + 1).trim()//.trim { it <= ' ' }
                headers[headerName] = headerValue
            }
        }
        return headers
    }
    /**
     * Parse the part of the multipart response.
     *
     * @param listener Listener invoked when part are received.
     * @return If the read was successful
     */
    fun start(): Boolean {
        var chunkStart: Long = 0
        var contentSize: Long = 0
        val content = Buffer()

        val source = this.source ?: return false
        val line = source.readUtf8Line() ?: ""
        if (!line.startsWith(DASHES)) {
            return false
        }
        val rawBoundary = line.replace(DASHES,"")
        val prefixBoundary = "$CRLF$DASHES$rawBoundary$CRLF".encodeUtf8()
        val closeBoundary = "$CRLF$DASHES$rawBoundary$DASHES$CRLF".encodeUtf8()

        while (true) {
            var isClose = false

            val searchStart = max(contentSize - closeBoundary.size, chunkStart)
            var indexOfBoundary = content.indexOf(prefixBoundary, searchStart)
            if (indexOfBoundary == -1L) {
                indexOfBoundary = content.indexOf(closeBoundary, searchStart)
                isClose = true
            }
            if (indexOfBoundary == -1L) {
                contentSize = content.size
                if (source.read(content, (4 * 1024).toLong()) <= 0) {
                    return false
                }
                continue
            }

            val chunkEnd = indexOfBoundary
            val length = chunkEnd - chunkStart

            if (chunkStart >= 0) {
                val headers = Buffer()
                content.skip(chunkStart)
                content.read(headers, length)
                notifyOnResult(headers, listener)
            } else {
                content.skip(chunkEnd)
            }
            chunkStart = prefixBoundary.size.toLong()
            contentSize = chunkStart

            if (isClose) {
                break
            }
        }
        return true
    }
}