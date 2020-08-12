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

class RenderDirectiveHandler(
    private val renderer: Renderer
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_RENDER = "Render"
        val RENDER = NamespaceAndName(ChipsAgent.NAMESPACE, NAME_RENDER)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    interface Renderer {
        fun render(directive: RenderDirective)
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, RenderDirective.Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload", DirectiveHandlerResult.POLICY_CANCEL_ALL)
            return
        }

        info.result.setCompleted()
        renderer.render(RenderDirective(info.directive.header, payload))
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[RENDER] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )

        return configuration
    }
}