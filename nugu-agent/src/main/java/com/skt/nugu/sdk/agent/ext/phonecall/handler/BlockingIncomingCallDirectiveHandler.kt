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

package com.skt.nugu.sdk.agent.ext.phonecall.handler

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.phonecall.PhoneCallAgent
import com.skt.nugu.sdk.agent.ext.phonecall.payload.BlockingIncomingCallPayload
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy

class BlockingIncomingCallDirectiveHandler (
    private val controller: Controller
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_BLOCKING_INCOMING_CALL = "BlockingIncomingCall"

        private val BLOCKING_INCOMING_CALL = NamespaceAndName(PhoneCallAgent.NAMESPACE, NAME_BLOCKING_INCOMING_CALL)
    }

    interface Controller {
        fun blockingIncomingCall(payload: BlockingIncomingCallPayload)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, BlockingIncomingCallPayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.blockingIncomingCall(payload)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configurations = HashMap<NamespaceAndName, BlockingPolicy>()

        configurations[BLOCKING_INCOMING_CALL] = BlockingPolicy()

        return configurations
    }
}