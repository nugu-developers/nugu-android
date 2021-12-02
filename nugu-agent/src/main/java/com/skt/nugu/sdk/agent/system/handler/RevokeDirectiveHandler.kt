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

package com.skt.nugu.sdk.agent.system.handler

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.system.AbstractSystemAgent
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy

class RevokeDirectiveHandler(
    private val controller: Controller
) : AbstractDirectiveHandler(){

    companion object {
        private const val NAME_REVOKE = "Revoke"

        val REVOKE = NamespaceAndName(AbstractSystemAgent.NAMESPACE, NAME_REVOKE)
    }

    interface Controller {
        fun onRevoke(reason: SystemAgentInterface.RevokeReason)
    }

    internal data class Payload(
        @SerializedName("reason")
        val reason: SystemAgentInterface.RevokeReason
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, Payload::class.java)
        if (payload == null) {
            info.result.setFailed("[handleRevoke] invalid payload: ${info.directive.payload}")
            return
        }

        controller.onRevoke(payload.reason)
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // no-op
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[REVOKE] = BlockingPolicy.sharedInstanceFactory.get()
    }
}