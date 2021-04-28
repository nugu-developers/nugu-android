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

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreProcessor
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import org.junit.Test
import org.mockito.kotlin.*

class DirectiveGroupProcessorTest {
    private val directiveSequencer: DirectiveSequencerInterface = mock()
    private val directiveGroupProcessor = DirectiveGroupProcessor(directiveSequencer)
    private val directives: List<Directive> = mock()
    private val listener: DirectiveGroupProcessorInterface.Listener = mock()
    private val preProcessor: DirectiveGroupPreProcessor = mock()

    @Test
    fun testSimpleCase() {
        whenever(preProcessor.preProcess(directives)).thenReturn(directives)

        directiveGroupProcessor.addListener(listener)
        directiveGroupProcessor.addDirectiveGroupPreprocessor(preProcessor)

        directiveGroupProcessor.onReceiveDirectives(directives)

        inOrder(listener, preProcessor, listener, directiveSequencer).apply {
            verify(listener).onPreProcessed(eq(directives))
            verify(preProcessor).preProcess(eq(directives))
            verify(listener).onPostProcessed(eq(directives))
            verify(directiveSequencer).onDirectives(eq(directives))
        }
    }

    @Test
    fun testRemoveListener() {
        directiveGroupProcessor.addListener(listener)
        directiveGroupProcessor.removeListener(listener)

        directiveGroupProcessor.onReceiveDirectives(directives)

        verifyZeroInteractions(listener)
    }

    @Test
    fun testRemoveDirectiveGroupPreprocessor() {
        directiveGroupProcessor.addDirectiveGroupPreprocessor(preProcessor)
        directiveGroupProcessor.removeDirectiveGroupPreprocessor(preProcessor)

        directiveGroupProcessor.onReceiveDirectives(directives)

        verifyZeroInteractions(preProcessor)
    }
}