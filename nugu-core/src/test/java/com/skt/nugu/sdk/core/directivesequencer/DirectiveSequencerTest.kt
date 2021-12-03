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
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class DirectiveSequencerTest {
    companion object {
        private val DIRECTIVE_0 = Directive(null, Header(
            "dialogRequestId",
            "messageId_0",
            "Play",
            "AudioPlayer",
            "1.0",
            ""
        ), "{}")

        private val DIRECTIVE_1 = Directive(null, Header(
            "dialogRequestId",
            "messageId_1",
            "Play",
            "TTS",
            "1.0",
            ""
        ), "{}")

        private val HANDLER_0: DirectiveHandler = object : DirectiveHandler {
            val resultMap = HashMap<String, DirectiveHandlerResult>()

            override fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult) {
                resultMap[directive.getMessageId()] = result
            }

            override fun handleDirective(messageId: String): Boolean {
                val removed = resultMap.remove(messageId)
                removed?.setCompleted()

                return removed != null
            }

            override fun cancelDirective(messageId: String) {
                resultMap.remove(messageId)?.setFailed("")
            }

            override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
                this[DIRECTIVE_0.getNamespaceAndName()] = BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO)
            }
        }

        private val HANDLER_1: DirectiveHandler = object : DirectiveHandler {
            val resultMap = HashMap<String, DirectiveHandlerResult>()

            override fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult) {
                resultMap[directive.getMessageId()] = result
            }

            override fun handleDirective(messageId: String): Boolean {
                val removed = resultMap.remove(messageId)
                removed?.setFailed("")

                return removed != null
            }

            override fun cancelDirective(messageId: String) {
                resultMap.remove(messageId)?.setFailed("")
            }

            override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
                this[DIRECTIVE_1.getNamespaceAndName()] = BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO)
            }
        }
    }

    @Test
    fun testDisable() {
        val sequencer = DirectiveSequencer()
        sequencer.disable()
        sequencer.addDirectiveHandler(HANDLER_0)

        val onDirectiveHandlingListener: DirectiveSequencerInterface.OnDirectiveHandlingListener = mock()

        sequencer.addOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.onDirectives(listOf(DIRECTIVE_0))

        verify(onDirectiveHandlingListener, never()).onRequested(DIRECTIVE_0)

        sequencer.removeOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.removeDirectiveHandler(HANDLER_0)
    }

    @Test
    fun testOnDirectivesOnCompleted() {
        val sequencer = DirectiveSequencer()
        sequencer.enable()
        sequencer.addDirectiveHandler(HANDLER_0)

        val onDirectiveHandlingListener: DirectiveSequencerInterface.OnDirectiveHandlingListener = mock()

        sequencer.addOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.onDirectives(listOf(DIRECTIVE_0))

        verify(onDirectiveHandlingListener, timeout(1000)).onRequested(DIRECTIVE_0)
        verify(onDirectiveHandlingListener, timeout(1000)).onCompleted(DIRECTIVE_0)

        sequencer.removeOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.removeDirectiveHandler(HANDLER_0)
    }

    @Test
    fun testOnDirectivesOnSkipped() {
        val sequencer = DirectiveSequencer()

        val onDirectiveHandlingListener: DirectiveSequencerInterface.OnDirectiveHandlingListener = mock()

        sequencer.addOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.onDirectives(listOf(DIRECTIVE_0))

        verify(onDirectiveHandlingListener, never()).onRequested(DIRECTIVE_0)
        verify(onDirectiveHandlingListener, timeout(1000)).onSkipped(DIRECTIVE_0)

        sequencer.removeOnDirectiveHandlingListener(onDirectiveHandlingListener)
    }

    @Test
    fun testOnDirectivesOnFailedAndOnCanceled() {
        val sequencer = DirectiveSequencer()
        sequencer.addDirectiveHandler(HANDLER_0)
        sequencer.addDirectiveHandler(HANDLER_1)

        val onDirectiveHandlingListener: DirectiveSequencerInterface.OnDirectiveHandlingListener = mock()

        sequencer.addOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.onDirectives(listOf(DIRECTIVE_1, DIRECTIVE_0))

        verify(onDirectiveHandlingListener, timeout(1000)).onRequested(DIRECTIVE_1)
        verify(onDirectiveHandlingListener, timeout(1000)).onFailed(DIRECTIVE_1, "")
        verify(onDirectiveHandlingListener, timeout(1000)).onCanceled(DIRECTIVE_0)

        sequencer.removeOnDirectiveHandlingListener(onDirectiveHandlingListener)
        sequencer.removeDirectiveHandler(HANDLER_0)
        sequencer.removeDirectiveHandler(HANDLER_1)
    }
}