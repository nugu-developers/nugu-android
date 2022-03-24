/**
 * Copyright (c) 2022 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.agent.extension

import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.Executors

class ExtensionAgent(
    directiveSequencer: DirectiveSequencerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender
) : CapabilityAgent,
    ExtensionAgentInterface {

    companion object {
        const val NAMESPACE = "Extension"
        val VERSION = Version(1,1)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var client: ExtensionAgentInterface.Client? = null

    private val contextProvider = object : ContextProvider() {
        override fun getClient(): ExtensionAgentInterface.Client? = client
    }

    private val actionDirectiveHandler = object : ActionDirectiveHandler(contextManager, messageSender, contextProvider.namespaceAndName, executor) {
        override fun getClient(): ExtensionAgentInterface.Client? = client
    }

    private val issueCommandEventRequester = IssueCommandEventRequesterImpl(contextManager, messageSender, contextProvider.namespaceAndName)

    init {
        contextManager.setStateProvider(contextProvider.namespaceAndName, contextProvider)
        directiveSequencer.addDirectiveHandler(actionDirectiveHandler)
    }

    override fun setClient(client: ExtensionAgentInterface.Client) {
        this.client = client
    }

    override fun issueCommand(
        playServiceId: String,
        data: String,
        callback: ExtensionAgentInterface.OnCommandIssuedCallback?
    ): String = issueCommandEventRequester.issueCommand(playServiceId, data, callback)
}