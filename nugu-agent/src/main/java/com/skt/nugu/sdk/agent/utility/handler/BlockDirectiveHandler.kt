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

package com.skt.nugu.sdk.agent.utility.handler

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.agent.utility.UtilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class BlockDirectiveHandler: AbstractDirectiveHandler() {
    companion object {
        private const val NAME_BLOCK = "Block"

        private val BLOCK = NamespaceAndName(UtilityAgent.NAMESPACE, NAME_BLOCK)
    }

    data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("sleepInMillisecond")
        val sleepInMillisecond: Long?
    )

    private val sleepScheduler = Executors.newSingleThreadScheduledExecutor()
    private val sleepFutureMap = HashMap<String, ScheduledFuture<*>>()

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            if(payload.sleepInMillisecond == null) {
                info.result.setCompleted()
            } else {
                sleepFutureMap[info.directive.getMessageId()] = sleepScheduler.schedule({
                    sleepFutureMap.remove(info.directive.getMessageId())
                    info.result.setCompleted()
                }, payload.sleepInMillisecond, TimeUnit.MILLISECONDS)
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        sleepFutureMap.remove(info.directive.getMessageId())?.cancel(true)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[BLOCK] = BlockingPolicy.sharedInstanceFactory.get(
            BlockingPolicy.MEDIUM_ALL,
            BlockingPolicy.MEDIUM_ANY_ONLY
        )
    }
}