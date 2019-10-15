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

class DisplayAggregator(
    private val templateAgent: DisplayAgentInterface,
    private val audioPlayerAgent: DisplayAgentInterface
) : DisplayAggregatorInterface {

    private val templateRenderer = object : DisplayAgentInterface.Renderer {
        override fun render(
            templateId: String,
            templateType: String,
            templateContent: String
        ): Boolean {
            val willRender = observer?.render(templateId, templateType, templateContent, DisplayAggregatorInterface.Type.INFOMATION) ?: false

            if(willRender) {
                requestAgentMap[templateId] = templateAgent
            }

            return willRender
        }

        override fun clear(templateId: String, force: Boolean) {
            observer?.clear(templateId, force)
        }
    }

    private val audioPlayerRenderer = object : DisplayAgentInterface.Renderer {
        override fun render(
            templateId: String,
            templateType: String,
            templateContent: String
        ): Boolean {
            val willRender = observer?.render(templateId, templateType, templateContent, DisplayAggregatorInterface.Type.AUDIO_PLAYER) ?: false

            if(willRender) {
                requestAgentMap[templateId] = audioPlayerAgent
            }

            return willRender
        }

        override fun clear(templateId: String, force: Boolean) {
            observer?.clear(templateId, force)
        }
    }

    private var observer: DisplayAggregatorInterface.Renderer? = null
    private val requestAgentMap = HashMap<String, DisplayAgentInterface>()

    init {
        templateAgent.setRenderer(templateRenderer)
        audioPlayerAgent.setRenderer(audioPlayerRenderer)
    }

    override fun setElementSelected(templateId: String, token: String) {
        requestAgentMap[templateId]?.setElementSelected(templateId, token)
    }

    override fun displayCardRendered(templateId: String) {
        requestAgentMap[templateId]?.displayCardRendered(templateId)
    }

    override fun displayCardCleared(templateId: String) {
        requestAgentMap.remove(templateId)?.displayCardCleared(templateId)
    }

    override fun setRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        this.observer = renderer
    }
}