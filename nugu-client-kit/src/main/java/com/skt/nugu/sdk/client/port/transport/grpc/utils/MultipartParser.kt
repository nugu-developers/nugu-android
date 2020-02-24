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
package com.skt.nugu.sdk.client.port.transport.grpc.utils

import java.util.HashMap
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.encodeUtf8
import kotlin.math.max

/** A utility class to handle multipart/mixed */
class MultipartParser(private val bufferedSource: BufferedSource?) {
    companion object {
        const val CRLF = "\r\n"
        const val TWO_DASHES = "--"
    }

    interface Listener {
        fun onResult(headers: Map<String, String>, body: Buffer)
    }

    private fun notifyOnResult(chunk: Buffer, listener: Listener) {
        val marker = encodeUtf8(CRLF + CRLF)
        val indexOfMarker = chunk.indexOf(marker)
        if (indexOfMarker == -1L) {
            listener.onResult(HashMap(), chunk)
        } else {
            val headers = Buffer()
            val body = Buffer()
            chunk.read(headers, indexOfMarker)
            chunk.skip(marker.size().toLong())
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
    fun parsePart(listener: Listener): Boolean {
        var chunkStart: Long = 0
        var contentSize: Long = 0
        val content = Buffer()

        val source = bufferedSource ?: return false
        val line = source.readUtf8Line() ?: ""
        if (!line.startsWith(TWO_DASHES, false)) {
            return false
        }
        val rawBoundary = line.replace(TWO_DASHES,"")
        val prefixBoundary = encodeUtf8("$CRLF$TWO_DASHES$rawBoundary$CRLF")
        val closeBoundary = encodeUtf8("$CRLF$TWO_DASHES$rawBoundary$TWO_DASHES$CRLF")

        while (true) {
            var isClose = false

            val searchStart = max(contentSize - closeBoundary.size(), chunkStart)
            var indexOfBoundary = content.indexOf(prefixBoundary, searchStart)
            if (indexOfBoundary == -1L) {
                indexOfBoundary = content.indexOf(closeBoundary, searchStart)
                isClose = true
            }
            if (indexOfBoundary == -1L) {
                contentSize = content.size()
                if (source.read(content, 4 * 1024) <= 0) {
                    return false
                }
                continue
            }

            val chunkEnd = indexOfBoundary
            val length = chunkEnd - chunkStart

            // Ignore preamble
            if (chunkStart > 0) {
                val headers = Buffer()
                content.skip(chunkStart)
                content.read(headers, length)
                notifyOnResult(headers, listener)
            } else {
                content.skip(chunkEnd)
            }
            chunkStart = prefixBoundary.size().toLong()
            contentSize = chunkStart

            if (isClose) {
                break
            }
        }
        return true
    }
}