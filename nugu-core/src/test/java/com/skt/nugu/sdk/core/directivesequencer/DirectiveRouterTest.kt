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

package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.*

class DirectiveRouterTest {
    companion object {
        private val HANDLER: DirectiveHandler = mock()

        private val DIRECTIVE = Directive(null, Header(
            "dialogRequestId",
            "messageId",
            "Play",
            "AudioPlayer",
            "1.0",
            ""
        ), "{}")

        init {
            whenever(HANDLER.configurations).thenReturn(mutableMapOf<NamespaceAndName, BlockingPolicy>().apply{
                put(DIRECTIVE.getNamespaceAndName(), BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO))
            })
        }
    }

    @Test
    fun testDirectiveRouterTrue() {
        val router = DirectiveRouter()
        router.addDirectiveHandler(HANDLER)

        val result: DirectiveHandlerResult = mock()

        Assert.assertTrue(router.preHandleDirective(DIRECTIVE, result))
        Assert.assertTrue(router.handleDirective(DIRECTIVE))
        Assert.assertTrue(router.cancelDirective(DIRECTIVE))
        verify(HANDLER).preHandleDirective(DIRECTIVE, result)
        verify(HANDLER).handleDirective(DIRECTIVE.getMessageId())
        verify(HANDLER).cancelDirective(DIRECTIVE.getMessageId())
    }

    @Test
    fun testDirectiveRouterFalseWithRemoveDirectiveHandler() {
        val router = DirectiveRouter()
        router.addDirectiveHandler(HANDLER)
        router.removeDirectiveHandler(HANDLER)
        val result: DirectiveHandlerResult = mock()

        Assert.assertFalse(router.preHandleDirective(DIRECTIVE, result))
        Assert.assertFalse(router.handleDirective(DIRECTIVE))
        Assert.assertFalse(router.cancelDirective(DIRECTIVE))
        verify(HANDLER, never()).preHandleDirective(DIRECTIVE, result)
        verify(HANDLER, never()).handleDirective(DIRECTIVE.getMessageId())
        verify(HANDLER, never()).cancelDirective(DIRECTIVE.getMessageId())
    }

    @Test
    fun testGetPolicy() {
        val router = DirectiveRouter()
        router.addDirectiveHandler(HANDLER)

        Assert.assertEquals(HANDLER.configurations[DIRECTIVE.getNamespaceAndName()], router.getPolicy(DIRECTIVE))
        verify(HANDLER, atLeastOnce()).configurations
    }
}