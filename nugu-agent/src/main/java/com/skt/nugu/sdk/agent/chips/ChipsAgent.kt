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

package com.skt.nugu.sdk.agent.chips

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.chips.handler.RenderDirectiveHandler
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArraySet

class ChipsAgent(
    directiveSequencer: DirectiveSequencerInterface,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    sessionManager: SessionManagerInterface
) : CapabilityAgent
    , ChipsAgentInterface
    , SupportedInterfaceContextProvider
    , RenderDirectiveHandler.Renderer {
    companion object {
        private const val TAG = "ChipsAgent"
        const val NAMESPACE = "Chips"
        private val VERSION = Version(1, 2)

        private fun buildCompactContext(): JsonObject = JsonObject().apply {
            addProperty("version", VERSION.toString())
        }

        private val COMPACT_STATE: String = buildCompactContext().toString()
    }

    private val listeners = CopyOnWriteArraySet<ChipsAgentInterface.Listener>()

    private val contextState = object : BaseContextState {
        override fun value(): String = COMPACT_STATE
    }

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextStateProviderRegistry.setStateProvider(namespaceAndName, this)
        directiveSequencer.addDirectiveHandler(RenderDirectiveHandler(this, directiveSequencer, sessionManager))
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(TAG, "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken")
        contextSetter.setState(namespaceAndName, contextState, StateRefreshPolicy.NEVER, contextType, stateRequestToken)
    }

    override fun addListener(listener: ChipsAgentInterface.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ChipsAgentInterface.Listener) {
        listeners.remove(listener)
    }

    override fun render(directive: RenderDirective) {
        Logger.d(TAG, "[render] $directive")
        listeners.forEach {
            it.renderChips(directive)
        }
    }

    override fun clear(directive: RenderDirective) {
        Logger.d(TAG, "[clear] $directive")
        listeners.forEach {
            it.clearChips(directive)
        }
    }
}