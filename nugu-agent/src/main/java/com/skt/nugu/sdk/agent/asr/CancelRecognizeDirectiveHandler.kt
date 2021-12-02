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

package com.skt.nugu.sdk.agent.asr

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.*

class CancelRecognizeDirectiveHandler(
    private val agent: ASRAgentInterface
): AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "CancelRecognizeDirectiveHandler"

        private const val NAME_CANCEL_RECOGNIZE = "CancelRecognize"

        val NOTIFY_RESULT = NamespaceAndName(
            DefaultASRAgent.NAMESPACE,
            NAME_CANCEL_RECOGNIZE
        )
    }

    private data class Payload(
        @SerializedName("cause")
        val cause: ASRAgentInterface.CancelCause
    )

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if(payload == null) {
            setHandlingFailed(info, "invalid payload")
            return
        }

        agent.stopRecognition(true, payload.cause)
        setHandlingCompleted(info)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[NOTIFY_RESULT] = BlockingPolicy.sharedInstanceFactory.get()
    }
}