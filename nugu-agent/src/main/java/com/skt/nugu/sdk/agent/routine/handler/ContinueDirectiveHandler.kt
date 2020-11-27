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

package com.skt.nugu.sdk.agent.routine.handler

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header

class ContinueDirectiveHandler(
    private val controller: Controller
): AbstractDirectiveHandler() {
    companion object {
        private const val NAME_CONTINUE = "Continue"

        val CONTINUE = NamespaceAndName(RoutineAgent.NAMESPACE, NAME_CONTINUE)
    }

    data class ContinueDirective(
        val header: Header,
        val payload: Payload
    ) {
        data class Payload(
            @SerializedName("playServiceId")
            val playServiceId: String,
            @SerializedName("token")
            val token: String
        )
    }

    interface Controller {
        fun doContinue(directive: ContinueDirective): Boolean
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, ContinueDirective.Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload.")
            return
        }

        if(controller.doContinue(ContinueDirective(info.directive.header, payload))) {
            info.result.setCompleted()
        } else {
            info.result.setFailed("do continue failed.")
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configurations = HashMap<NamespaceAndName, BlockingPolicy>()

        configurations[CONTINUE] = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO
        )

        return configurations
    }
}