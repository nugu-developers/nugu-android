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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfDirectiveIsUnauthorizedRequestException
import com.skt.nugu.sdk.client.port.transport.grpc2.utils.DirectivePreconditions.checkIfEventMessageIsAsrRecognize
import junit.framework.TestCase
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import devicegateway.grpc.DirectiveMessage
import devicegateway.grpc.Header
import org.junit.Assert
import org.junit.Test

class DirectivePreconditionsTest : TestCase() {
    @Test
    fun testCheckIfDirectiveIsUnauthorizedRequestExceptionTrue() {
        Assert.assertTrue(DirectiveMessage.newBuilder().addDirectives(
            devicegateway.grpc.Directive.newBuilder().apply {
                setHeader(Header.newBuilder().apply {
                    name = "Exception"
                    namespace = "System"
                })
                payload = "  \"payload\": {\n" +
                        "    \"code\": \"UNAUTHORIZED_REQUEST_EXCEPTION\",\n" +
                        "    \"description\": \"device\"\n" +
                        "  }"
            }
        ).build().checkIfDirectiveIsUnauthorizedRequestException())
    }

    @Test
    fun testCheckIfDirectiveIsUnauthorizedRequestExceptionFalse() {
        Assert.assertFalse(DirectiveMessage.newBuilder().addDirectives(
            devicegateway.grpc.Directive.newBuilder().apply {
                setHeader(Header.newBuilder().apply {
                    name = "Exception"
                    namespace = "System"
                })
                payload = "  \"payload\": {\n" +
                        "    \"code\": \"ASR_RECOGNIZING_EXCEPTION\",\n" +
                        "    \"description\": \"device\"\n" +
                        "  }"
            }
        ).build().checkIfDirectiveIsUnauthorizedRequestException())
    }

    @Test
    fun testCheckIfEventMessageIsAsrRecognizeTrue() {
        Assert.assertTrue(
            EventMessageRequest.Builder(
                context = "",
                namespace = "ASR",
                name = "Recognize",
                version = "1.0"
            ).build().checkIfEventMessageIsAsrRecognize()
        )
    }

    @Test
    fun testCheckIfEventMessageIsAsrRecognizeFalse() {
        Assert.assertFalse(
            EventMessageRequest.Builder(
                context = "",
                namespace = "TTS",
                name = "Speak",
                version = "1.0"
            ).build().checkIfEventMessageIsAsrRecognize()
        )
    }

}