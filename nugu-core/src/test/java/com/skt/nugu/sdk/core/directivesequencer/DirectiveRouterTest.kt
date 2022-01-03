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

        private val POLICY = BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO)

        init {
            whenever(HANDLER.configurations).thenReturn(mutableMapOf<NamespaceAndName, BlockingPolicy>().apply{
                put(DIRECTIVE.getNamespaceAndName(), POLICY)
            })
        }
    }

    @Test
    fun testGetDirectiveHandler() {
        val router = DirectiveRouter()
        router.addDirectiveHandler(HANDLER)

        Assert.assertEquals(router.getDirectiveHandler(DIRECTIVE), HANDLER)
        router.removeDirectiveHandler(HANDLER)
        Assert.assertNull(router.getDirectiveHandler(DIRECTIVE))
    }

    @Test
    fun testGetHandlerAndPolicyOfDirective() {
        val router = DirectiveRouter()
        router.addDirectiveHandler(HANDLER)

        Assert.assertEquals(router.getHandlerAndPolicyOfDirective(DIRECTIVE), Pair(HANDLER, POLICY))
        router.removeDirectiveHandler(HANDLER)
        Assert.assertNull(router.getHandlerAndPolicyOfDirective(DIRECTIVE))
    }
}