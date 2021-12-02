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

package com.skt.nugu.sdk.agent.tts.handler

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import java.util.*

class StopDirectiveHandler(
    private val controller: Controller
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_STOP = "Stop"
        val STOP = NamespaceAndName(
            DefaultTTSAgent.NAMESPACE,
            NAME_STOP
        )
    }

    data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String?,
        @SerializedName("token")
        val token: String
    )

    interface Controller {
        fun stop(payload: Payload)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload", DirectiveHandlerResult.POLICY_CANCEL_ALL)
            return
        }

        controller.stop(payload)
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {

    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[STOP] = BlockingPolicy.sharedInstanceFactory.get()
    }
}