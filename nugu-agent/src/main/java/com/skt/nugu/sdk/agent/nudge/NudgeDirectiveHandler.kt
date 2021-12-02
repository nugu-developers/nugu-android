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

package com.skt.nugu.sdk.agent.nudge

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy

class NudgeDirectiveHandler(private val nudgeDirectiveObserver: NudgeDirectiveObserver) : AbstractDirectiveHandler() {

    companion object {
        internal const val NAME_DIRECTIVE = "Append"

        private val APPEND = NamespaceAndName(NudgeAgent.NAMESPACE, NAME_DIRECTIVE)

        fun isAppendDirective(namespace: String, name: String) = namespace == NudgeAgent.NAMESPACE && name == NAME_DIRECTIVE
    }

    data class Payload(
        @SerializedName("nudgeInfo")
        val nudgeInfo: JsonObject
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // skip
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if (payload?.nudgeInfo == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            nudgeDirectiveObserver.onNudgeAppendDirective(info.directive.header.dialogRequestId, payload)
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // skip
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> by lazy {
        HashMap<NamespaceAndName, BlockingPolicy>().apply {
            this[APPEND] = BlockingPolicy.sharedInstanceFactory.get()
        }
    }
}