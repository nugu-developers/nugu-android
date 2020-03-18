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

package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy

class UpdateDirectiveHandler(
    private val controller: Controller
): AbstractDirectiveHandler() {
    companion object {
        // supported at v1.2
        private const val TAG = "UpdateDirectiveHandler"
        private const val NAME_UPDATE = "Update"

        private val UPDATE = NamespaceAndName(
            DefaultDisplayAgent.NAMESPACE,
            NAME_UPDATE
        )
    }

    interface Controller {
        fun update(token: String, payload: String, listener: OnUpdateListener)

        interface OnUpdateListener {
            fun onSuccess()
            fun onFailure(description: String)
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, DefaultDisplayAgent.TemplatePayload::class.java)
        if (payload?.token == null) {
            setHandlingFailed(info, "[handleDirective] invalid payload: $payload")
            return
        }

        if(payload.token.isBlank()) {
            setHandlingFailed(info, "[handleDirective] token empty")
            return
        }

        controller.update(payload.token, info.directive.payload, object: Controller.OnUpdateListener {
            override fun onSuccess() {
                setHandlingCompleted(info)
            }

            override fun onFailure(description: String) {
                setHandlingFailed(
                    info,
                    description
                )
            }
        })
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingFailed(info: DirectiveInfo, description: String) {
        info.result.setFailed(description)
        removeDirective(info.directive.getMessageId())
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val blockingPolicy = BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            true
        )

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[UPDATE] = blockingPolicy

        return configuration
    }
}