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

package com.skt.nugu.sdk.agent.chips.handler

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.chips.ChipsAgent
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap

class RenderDirectiveHandler(
    private val renderer: Renderer,
    directiveSequencer: DirectiveSequencerInterface,
    sessionManager: SessionManagerInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "RenderDirectiveHandler"

        private const val NAME_RENDER = "Render"
        val RENDER = NamespaceAndName(ChipsAgent.NAMESPACE, NAME_RENDER)
    }

    private val renderedDirectiveQueue: Queue<RenderDirective> = ConcurrentLinkedQueue()

    private val directiveLifecycleHandler =
        object : DirectiveSequencerInterface.OnDirectiveHandlingListener,
            SessionManagerInterface.Listener {
            override fun onRequested(directive: Directive) {}

            override fun onCompleted(directive: Directive) {
                clearDirectiveIfNeed(directive)
            }

            override fun onCanceled(directive: Directive) {
                clearDirectiveIfNeed(directive)
            }

            override fun onFailed(directive: Directive, description: String) {
                clearDirectiveIfNeed(directive)
            }

            override fun onSkipped(directive: Directive) {
                clearDirectiveIfNeed(directive)
            }

            override fun onSessionActivated(key: String, session: SessionManagerInterface.Session) { }

            override fun onSessionDeactivated(
                key: String,
                session: SessionManagerInterface.Session
            ) {
                val shouldBeClears = renderedDirectiveQueue.filter {
                    it.header.dialogRequestId == key
                }.filter {
                    it.payload.target == RenderDirective.Payload.Target.DM
                }

                shouldBeClears.forEach {
                    Logger.d(TAG, "[onSessionDeactivated] clear: $it")
                    renderedDirectiveQueue.remove(it)
                    renderer.clear(it)
                }
            }

            private fun clearDirectiveIfNeed(directive: Directive) {
                val matchedRenderDirective = renderedDirectiveQueue.filter {
                    it.header.dialogRequestId == directive.getDialogRequestId()
                }

                if (matchedRenderDirective.isNullOrEmpty()) {
                    return
                }

                val shouldBeClears: List<RenderDirective>? =
                    if (directive.header.namespace == "TTS" && directive.header.name == "Speak") {
                        matchedRenderDirective.filter {
                            it.payload.target == RenderDirective.Payload.Target.SPEAKING
                        }
                    } else if (directive.header.namespace == "ASR" && directive.header.name == "ExpectSpeech") {
                        matchedRenderDirective.filter {
                            it.payload.target == RenderDirective.Payload.Target.LISTEN
                        }
                    } else {
                        null
                    }

                shouldBeClears?.forEach {
                    Logger.d(TAG, "[clearDirectiveIfNeed] clear: $it")
                    renderedDirectiveQueue.remove(it)
                    renderer.clear(it)
                }
            }
        }

    init {
        directiveSequencer.addOnDirectiveHandlingListener(directiveLifecycleHandler)
        sessionManager.addListener(directiveLifecycleHandler)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    interface Renderer {
        fun render(directive: RenderDirective)
        fun clear(directive: RenderDirective)
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, RenderDirective.Payload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload", DirectiveHandlerResult.POLICY_CANCEL_ALL)
            return
        }

        info.result.setCompleted()
        RenderDirective(info.directive.header, payload).let {
            renderer.render(it)
            renderedDirectiveQueue.add(it)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[RENDER] = BlockingPolicy.sharedInstanceFactory.get(
            blocking = BlockingPolicy.MEDIUM_AUDIO_ONLY
        )
    }
}