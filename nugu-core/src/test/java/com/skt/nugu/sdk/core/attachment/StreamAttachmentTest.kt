/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.core.attachment

import org.junit.Assert
import org.junit.Test
import java.io.EOFException
import java.nio.ByteBuffer

class StreamAttachmentTest {
    @Test
    fun testCreateWriter() {
        Assert.assertNotNull(StreamAttachment("testCreateWriter").createWriter())
    }

    @Test
    fun testCreateReader() {
        Assert.assertNotNull(StreamAttachment("testCreateReader").createReader())
    }


    @Test
    fun testWriteAndReadBuffer() {
        val attachment = StreamAttachment("testWriteAndReadBuffer")

        val writeBuffer = ByteBuffer.allocate(128)
        for(i in 0..127) {
            writeBuffer.put(i.toByte())
        }
        writeBuffer.flip()

        val writer = attachment.createWriter()
        writer.write(writeBuffer)
        writer.close()

        val readBuffer = ByteBuffer.allocate(128)
        val reader = attachment.createReader()
        reader.read(readBuffer,0, 128)

        Assert.assertArrayEquals(writeBuffer.array(), readBuffer.array())
        Assert.assertFalse(reader.isClosed())
        Assert.assertEquals(reader.read(readBuffer,0, 128), -1)
        reader.close()
        Assert.assertTrue(reader.isClosed())
    }

    @Test
    fun testReadAndWrite() {
        val attachment = StreamAttachment("testReadAndWrite")

        val writeBuffer = ByteBuffer.allocate(128)
        for(i in 0..127) {
            writeBuffer.put(i.toByte())
        }

        // read
        val readBuffer = ByteBuffer.allocate(128)
        val reader = attachment.createReader()
        val t1 = Thread {
            reader.read(readBuffer,0, 128)
        }.also {
            it.start()
        }

        // write
        val t2 = Thread {
            writeBuffer.flip()

            val writer = attachment.createWriter()
            writer.write(writeBuffer)
            writer.close()
        }.also {
            it.start()
        }

        t1.join()
        t2.join()

        Assert.assertArrayEquals(writeBuffer.array(), readBuffer.array())
        Assert.assertEquals(reader.read(readBuffer,0, 128), -1)
        reader.close()
    }


    @Test
    fun testWriteAndReadArray() {
        val attachment = StreamAttachment("testWriteAndReadArray")

        val writeBuffer = ByteBuffer.allocate(128)
        for(i in 0..127) {
            writeBuffer.put(i.toByte())
        }
        writeBuffer.flip()

        val writer = attachment.createWriter()
        writer.write(writeBuffer)
        writer.close(true)

        val readArray = ByteArray(128)
        val reader = attachment.createReader()
        reader.read(readArray,0, 128)
        reader.close()

        Assert.assertArrayEquals(writeBuffer.array(), readArray)

        Assert.assertThrows(EOFException::class.java) {
            reader.read(readArray, 0, 128)
        }
    }

    @Test
    fun testWriteAndReadChunk() {
        val attachment = StreamAttachment("testWriteAndReadChunk")

        val writeBuffer = ByteBuffer.allocate(128)
        for(i in 0..127) {
            writeBuffer.put(i.toByte())
        }
        writeBuffer.flip()

        val writer = attachment.createWriter()
        writer.write(writeBuffer)
        writer.close()

        val reader = attachment.createReader()
        Assert.assertEquals(writeBuffer, reader.readChunk())
        reader.close()
    }
}