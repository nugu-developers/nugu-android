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
import com.skt.nugu.sdk.agent.routine.Action
import com.skt.nugu.sdk.agent.routine.RoutineAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header

class StartDirectiveHandler(
    private val controller: Controller,
    private val handleController: HandleController?
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_START = "Start"

        private val START = NamespaceAndName(RoutineAgent.NAMESPACE, NAME_START)
    }

    data class StartDirective(
        val header: Header,
        val payload: Payload
    ) {
        data class Payload(
            @SerializedName("playServiceId")
            val playServiceId: String,
            @SerializedName("token")
            val token: String,
            @SerializedName("actions")
            val actions: Array<Action>
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Payload

                if (playServiceId != other.playServiceId) return false
                if (token != other.token) return false
                if (!actions.contentEquals(other.actions)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = playServiceId.hashCode()
                result = 31 * result + token.hashCode()
                result = 31 * result + actions.contentHashCode()
                return result
            }
        }
    }

    interface HandleController {
        sealed class Result {
            object OK : Result()
            data class ERROR(val errorMessage: String) : Result()
        }

        fun shouldExecuteDirective(payload: StartDirective.Payload, header: Header): Result
    }

    interface Controller {
        fun start(directive: StartDirective): Boolean
        fun failed(directive: StartDirective, errorMessage: String)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload =
            MessageFactory.create(info.directive.payload, StartDirective.Payload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload.")
            return
        }

        handleController?.shouldExecuteDirective(payload, info.directive.header).let {
            if(it is HandleController.Result.ERROR) {
                controller.failed(StartDirective(
                    info.directive.header,
                    payload
                ), it.errorMessage)
                info.result.setFailed("start failed by handleController: ${it.errorMessage}")
                return
            }
        }

        if (controller.start(
                StartDirective(
                    info.directive.header,
                    payload
                )
            )
        ) {
            info.result.setCompleted()
        } else {
            info.result.setFailed("start failed.")
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[START] = BlockingPolicy.sharedInstanceFactory.get()
    }
}