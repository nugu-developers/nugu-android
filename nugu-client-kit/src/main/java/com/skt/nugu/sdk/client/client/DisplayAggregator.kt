/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.client.client

import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DisplayAggregator(
    private val templateAgent: DisplayAgentInterface,
    private val audioPlayerAgent: DisplayAgentInterface
) : DisplayAggregatorInterface {
    private abstract inner class BaseRenderer : DisplayAgentInterface.Renderer {
        protected abstract fun getAgent(): DisplayAgentInterface
        protected abstract fun getType(): DisplayAggregatorInterface.Type

        override fun render(
            templateId: String,
            templateType: String,
            templateContent: String,
            dialogRequestId: String
        ): Boolean {
            lock.withLock {
                requestAgentMap[templateId] = getAgent()
            }
            val willRender = observer?.render(
                templateId,
                templateType,
                templateContent,
                dialogRequestId,
                getType()
            ) ?: false

            if (!willRender) {
                lock.withLock {
                    requestAgentMap.remove(templateId)
                }
            }

            return willRender
        }

        override fun clear(templateId: String, force: Boolean) {
            observer?.clear(templateId, force)
        }
    }

    private var observer: DisplayAggregatorInterface.Renderer? = null
    private val lock = ReentrantLock()
    private val requestAgentMap = HashMap<String, DisplayAgentInterface>()

    init {
        templateAgent.setRenderer(object : BaseRenderer() {
            override fun getAgent(): DisplayAgentInterface = templateAgent

            override fun getType(): DisplayAggregatorInterface.Type =
                DisplayAggregatorInterface.Type.INFOMATION
        })
        audioPlayerAgent.setRenderer(object : BaseRenderer() {
            override fun getAgent(): DisplayAgentInterface = audioPlayerAgent

            override fun getType(): DisplayAggregatorInterface.Type =
                DisplayAggregatorInterface.Type.AUDIO_PLAYER
        })
    }

    override fun setElementSelected(templateId: String, token: String) {
        lock.withLock {
            requestAgentMap[templateId]
        }?.setElementSelected(templateId, token)
    }

    override fun displayCardRendered(templateId: String) {
        lock.withLock {
            requestAgentMap[templateId]
        }?.displayCardRendered(templateId)
    }

    override fun displayCardCleared(templateId: String) {
        lock.withLock {
            requestAgentMap.remove(templateId)
        }?.displayCardCleared(templateId)
    }

    override fun setRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        this.observer = renderer
    }
}