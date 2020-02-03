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
package com.skt.nugu.sdk.client.display

import com.skt.nugu.sdk.agent.display.AudioPlayerDisplayInterface
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.display.DisplayInterface
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DisplayAggregator(
    private val templateAgent: DisplayAgentInterface,
    private val audioPlayerAgent: AudioPlayerDisplayInterface
) : DisplayAggregatorInterface {
    private abstract inner class BaseRenderer {
        protected abstract fun getAgent(): DisplayInterface<*>
        protected abstract fun getType(): DisplayAggregatorInterface.Type

        fun render(
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

        fun clear(templateId: String, force: Boolean) {
            observer?.clear(templateId, force)
        }
    }

    private var observer: DisplayAggregatorInterface.Renderer? = null
    private val lock = ReentrantLock()
    private val requestAgentMap = HashMap<String, DisplayInterface<*>>()

    init {
        templateAgent.setRenderer(object : DisplayAgentInterface.Renderer {
            private val renderer = object : BaseRenderer() {
                override fun getAgent() = templateAgent
                override fun getType(): DisplayAggregatorInterface.Type =
                    DisplayAggregatorInterface.Type.INFOMATION
            }

            override fun render(
                templateId: String,
                templateType: String,
                templateContent: String,
                dialogRequestId: String
            ): Boolean = renderer.render(templateId, templateType, templateContent, dialogRequestId)

            override fun clear(templateId: String, force: Boolean) =
                renderer.clear(templateId, force)
        })

        audioPlayerAgent.setRenderer(object : AudioPlayerDisplayInterface.Renderer {
            private val renderer = object : BaseRenderer() {
                override fun getAgent() = audioPlayerAgent
                override fun getType(): DisplayAggregatorInterface.Type =
                    DisplayAggregatorInterface.Type.AUDIO_PLAYER
            }

            override fun render(
                templateId: String,
                templateType: String,
                templateContent: String,
                dialogRequestId: String
            ): Boolean =
                renderer.render(templateId, templateType, templateContent, dialogRequestId)

            override fun clear(templateId: String, force: Boolean) =
                renderer.clear(templateId, force)

            override fun update(templateId: String, templateContent: String) {
                observer?.update(templateId, templateContent)
            }
        })
    }

    override fun setElementSelected(
        templateId: String,
        token: String,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String = lock.withLock {
        requestAgentMap[templateId]
    }?.setElementSelected(templateId, token, callback)
        ?: throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

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

    override fun stopRenderingTimer(templateId: String) {
        lock.withLock {
            requestAgentMap[templateId]
        }?.stopRenderingTimer(templateId)
    }
}