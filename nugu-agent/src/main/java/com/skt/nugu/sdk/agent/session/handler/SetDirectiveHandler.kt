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

package com.skt.nugu.sdk.agent.session.handler

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.session.SessionAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import java.util.HashMap

class SetDirectiveHandler(
    private val controller: Controller
): AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "SetDirectiveHandler"

        private const val NAME_SET = "Set"
        private val SET = NamespaceAndName(SessionAgent.NAMESPACE, NAME_SET)
    }

    data class SetDirective(
        val header: Header,
        val payload: Payload
    ) {
        data class Payload(
            @SerializedName("playServiceId")
            val playServiceId: String,
            @SerializedName("sessionId")
            val sessionId: String
        )
    }

    interface Controller {
        fun set(directive: SetDirective)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        Logger.d(TAG, "[preHandleDirective] $info")
        val payload = MessageFactory.create(info.directive.payload, SetDirective.Payload::class.java)
        if(payload == null) {
            info.result.setFailed("invalid payload")
            return
        }

        controller.set(SetDirective(info.directive.header, payload))
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[SET] = BlockingPolicy.sharedInstanceFactory.get()
    }
}