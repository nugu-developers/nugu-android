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
package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DisplayAggregator(
    private val templateAgent: DisplayAgentInterface,
    private val audioPlayerAgent: AudioPlayerDisplayInterface
) : DisplayAggregatorInterface, DirectiveGroupProcessorInterface.Listener {
    private abstract inner class BaseRenderer {
        protected abstract fun getAgent(): DisplayInterface<*, *>

        fun render(
            templateId: String,
            templateType: String,
            templateContent: String,
            header: Header,
            type: DisplayAggregatorInterface.Type,
            parentTemplateId: String?
        ): Boolean {
            lock.withLock {
                requestAgentMap[templateId] = getAgent()
            }
            val willRender = observer?.render(
                templateId,
                templateType,
                templateContent,
                header,
                type,
                parentTemplateId
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

        fun update(templateId: String, templateContent: String) {
            observer?.update(templateId, templateContent)
        }
    }

    private var observer: DisplayAggregatorInterface.Renderer? = null
    private val lock = ReentrantLock()
    private val requestAgentMap = HashMap<String, DisplayInterface<*, *>>()
    private val displayTypeMap = LinkedHashMap<String, DisplayAggregatorInterface.Type>()

    init {
        templateAgent.setRenderer(object : DisplayAgentInterface.Renderer {
            private val renderer = object : BaseRenderer() {
                override fun getAgent() = templateAgent
            }

            override fun render(
                templateId: String,
                templateType: String,
                templateContent: String,
                header: Header,
                contextLayer: DisplayAgentInterface.ContextLayer,
                parentTemplateId: String?
            ): Boolean = renderer.render(
                templateId,
                templateType,
                templateContent,
                header,
                getAndRemoveTypeForTemplateAgent(header, contextLayer),
                parentTemplateId
            )

            override fun clear(templateId: String, force: Boolean) =
                renderer.clear(templateId, force)

            override fun update(templateId: String, templateContent: String) {
                renderer.update(templateId, templateContent)
            }
        })

        audioPlayerAgent.setRenderer(object : AudioPlayerDisplayInterface.Renderer {
            private val renderer = object : BaseRenderer() {
                override fun getAgent() = audioPlayerAgent
            }

            override fun render(
                templateId: String,
                templateType: String,
                templateContent: String,
                header: Header
            ): Boolean =
                renderer.render(
                    templateId,
                    templateType,
                    templateContent,
                    header,
                    DisplayAggregatorInterface.Type.AUDIO_PLAYER,
                    null
                )

            override fun clear(templateId: String, force: Boolean) =
                renderer.clear(templateId, force)

            override fun update(templateId: String, templateContent: String) {
                renderer.update(templateId, templateContent)
            }
        })
    }

    private fun getAndRemoveTypeForTemplateAgent(header: Header, contextLayer: DisplayAgentInterface.ContextLayer): DisplayAggregatorInterface.Type {
        val layer = when(contextLayer) {
            DisplayAgentInterface.ContextLayer.MEDIA -> DisplayAggregatorInterface.Type.AUDIO_PLAYER
            DisplayAgentInterface.ContextLayer.ALERT -> DisplayAggregatorInterface.Type.ALERT
            DisplayAgentInterface.ContextLayer.CALL -> DisplayAggregatorInterface.Type.CALL
            else -> DisplayAggregatorInterface.Type.INFORMATION
        }

        // for backward compatibility
        return if(layer == DisplayAggregatorInterface.Type.INFORMATION) {
            displayTypeMap.remove(header.dialogRequestId) ?: DisplayAggregatorInterface.Type.INFORMATION
        } else {
            layer
        }
    }

    override fun notifyUserInteraction(templateId: String) {
        val agent = lock.withLock {
            requestAgentMap[templateId]
        } ?: return

        if(agent == templateAgent) {
            templateAgent.notifyUserInteraction(templateId)
        } else if(agent == audioPlayerAgent) {
            templateAgent.notifyUserInteraction(templateId)
        }
    }

    override fun setElementSelected(
        templateId: String,
        token: String,
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ): String = lock.withLock {
        requestAgentMap[templateId]
    }?.setElementSelected(templateId, token, postback, callback)
        ?: throw IllegalStateException("invalid templateId: $templateId (maybe cleared or not rendered yet)")

    override fun displayCardRendered(templateId: String, controller: DisplayAggregatorInterface.Controller?) {
        val agent = lock.withLock {
            requestAgentMap[templateId]
        } ?: return

        if(agent == templateAgent) {
            templateAgent.displayCardRendered(templateId, controller as DisplayAgentInterface.Controller?)
        } else if(agent == audioPlayerAgent){
            audioPlayerAgent.displayCardRendered(templateId, controller as AudioPlayerDisplayInterface.Controller?)
        }
    }

    override fun displayCardRenderFailed(templateId: String) {
        lock.withLock {
            requestAgentMap.remove(templateId)
        }?.displayCardRenderFailed(templateId)
    }

    override fun displayCardCleared(templateId: String) {
        lock.withLock {
            requestAgentMap.remove(templateId)
        }?.displayCardCleared(templateId)
    }

    override fun setRenderer(renderer: DisplayAggregatorInterface.Renderer?) {
        this.observer = renderer
    }

    override fun onPostProcessed(directives: List<Directive>) {
        val audioPlayerDirective = directives.filter {
            (it.getNamespaceAndName() == DefaultAudioPlayerAgent.PLAY)
        }

        val dialogRequestId = audioPlayerDirective.firstOrNull()?.getDialogRequestId() ?: return

        displayTypeMap[dialogRequestId] = DisplayAggregatorInterface.Type.AUDIO_PLAYER

        if (displayTypeMap.count() > 10) {
            val removeKey = displayTypeMap.keys.firstOrNull() ?: return
            displayTypeMap.remove(removeKey)
        }
    }
}