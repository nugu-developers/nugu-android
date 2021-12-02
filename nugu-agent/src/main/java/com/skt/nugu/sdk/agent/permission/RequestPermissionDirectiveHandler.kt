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

package com.skt.nugu.sdk.agent.permission

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import java.lang.Exception

class RequestPermissionDirectiveHandler(
    private val controller: Controller
): AbstractDirectiveHandler() {
    companion object {
        private const val NAME_DIRECTIVE = "RequestPermission"

        internal val DIRECTIVE = NamespaceAndName(PermissionAgent.NAMESPACE, NAME_DIRECTIVE)
    }

    data class Payload(
        val playServiceId: String,
        val permissions: Array<PermissionType>
    )

    interface Controller {
        fun requestPermission(header: Header, payload: Payload)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload: Payload? = parsePayload(info.directive.payload)

        if(payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.requestPermission(info.directive.header, payload)
        }
    }

    private fun parsePayload(payload: String): Payload? = try {
        val jsonPayload = JsonParser.parseString(payload).asJsonObject
        val playServiceId: String = jsonPayload.getAsJsonPrimitive("playServiceId").asString
        val permissions = ArrayList<PermissionType>()
        jsonPayload.getAsJsonArray("permissions").forEach {
            permissions.add(PermissionType.valueOf(it.asString))
        }
        if(permissions.isEmpty()) {
            null
        } else {
            Payload(playServiceId, permissions.toTypedArray())
        }
    } catch (e: Exception) {
        null
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // can't cancel.
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[DIRECTIVE] = BlockingPolicy.sharedInstanceFactory.get(BlockingPolicy.MEDIUM_AUDIO)
    }
}