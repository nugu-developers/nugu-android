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

package com.skt.nugu.sdk.agent.permission

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.mockito.kotlin.*
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert
import org.junit.Test

class RequestPermissionDirectiveHandlerTest {
    @Test
    fun testHandleDirectiveOnFailed() {
        val directiveInfo: AbstractDirectiveHandler.DirectiveInfo = object: AbstractDirectiveHandler.DirectiveInfo {
            override val directive: Directive = Directive(
                null,
                Header("", "messageId_1", "", "" ,"" ,""), JsonObject().apply {
                    addProperty("playServiceId", "playServiceId")
                    add("permissions", JsonArray().apply {
                        add("Loc")
                    })
                }.toString()
            )
            override val result: DirectiveHandlerResult = mock()
        }

        RequestPermissionDirectiveHandler(mock()).let {
            it.preHandleDirective(directiveInfo.directive, directiveInfo.result)
            it.handleDirective(directiveInfo.directive.getMessageId())
        }

        verify(directiveInfo.result).setFailed(any(), any())
    }

    @Test
    fun testHandleDirectiveOnCompleted() {
        val directiveInfo: AbstractDirectiveHandler.DirectiveInfo = object: AbstractDirectiveHandler.DirectiveInfo {
            override val directive: Directive = Directive(
                    null,
                    Header("", "messageId_1", "", "" ,"" ,""), JsonObject().apply {
                        addProperty("playServiceId", "playServiceId")
                        add("permissions", JsonArray().apply {
                            add(PermissionType.LOCATION.name)
                        })
                    }.toString()
                )
            override val result: DirectiveHandlerResult = mock()
        }

        val controller: RequestPermissionDirectiveHandler.Controller = mock()
        RequestPermissionDirectiveHandler(controller).let {
            it.preHandleDirective(directiveInfo.directive, directiveInfo.result)
            it.handleDirective(directiveInfo.directive.getMessageId())
        }

        verify(directiveInfo.result).setCompleted()
        verify(controller).requestPermission(eq(directiveInfo.directive.header), any())
    }

    @Test
    fun testGetConfiguration() {
        val handler = RequestPermissionDirectiveHandler(mock())
        handler.configurations.let {
            val requestPermission = it[RequestPermissionDirectiveHandler.DIRECTIVE]
            Assert.assertTrue(requestPermission != null)
            requestPermission?.let { blockingPolicy ->
                Assert.assertTrue(blockingPolicy.blockedBy == BlockingPolicy.MEDIUM_AUDIO)
                Assert.assertTrue(blockingPolicy.blocking == null)
            }
        }
    }
}