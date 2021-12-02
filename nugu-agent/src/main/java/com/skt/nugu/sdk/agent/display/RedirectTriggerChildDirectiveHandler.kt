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

package com.skt.nugu.sdk.agent.display

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header

class RedirectTriggerChildDirectiveHandler(
    private val controller: Controller
): AbstractDirectiveHandler() {
    companion object {
        // supported at v1.9
        private const val NAME_REDIRECT_TRIGGER_CHILD = "RedirectTriggerChild"

        private val REDIRECT_TRIGGER_CHILD = NamespaceAndName(
            DisplayAgent.NAMESPACE,
            NAME_REDIRECT_TRIGGER_CHILD
        )
    }

    data class Payload(
        val playServiceId: String,
        val parentToken: String,
        val data: JsonObject
    ) {
        companion object {
            fun fromJson(json: String): Payload = run {
                val jsonObject = JsonParser.parseString(json).asJsonObject
                Payload(
                    jsonObject.get("playServiceId").asString,
                    jsonObject.get("parentToken").asString,
                    jsonObject.get("data").asJsonObject,
                )
            }
        }
    }

    interface Controller {
        fun redirectTriggerChild(header: Header, payload: Payload, result: OnResultListener)

        interface OnResultListener {
            fun onSuccess()
            fun onFailure(description: String)
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = try {
            Payload.fromJson(info.directive.payload)
        } catch (e: Exception) {
            info.result.setFailed(e.toString())
            return
        }

        controller.redirectTriggerChild(info.directive.header, payload, object: Controller.OnResultListener {
            override fun onSuccess() {
                info.result.setCompleted()
            }

            override fun onFailure(description: String) {
                info.result.setFailed(description)
            }
        })
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[REDIRECT_TRIGGER_CHILD] = BlockingPolicy.sharedInstanceFactory.get()
    }
}