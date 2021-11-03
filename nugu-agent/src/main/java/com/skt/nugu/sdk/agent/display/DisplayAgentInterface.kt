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

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.core.interfaces.common.EventCallback
import com.skt.nugu.sdk.core.interfaces.message.Header

/**
 * The public interface for DisplayAgent
 */
interface DisplayAgentInterface:
    DisplayInterface<DisplayAgentInterface.Renderer, DisplayAgentInterface.Controller> {
    enum class ContextLayer {
        CALL,
        ALERT,
        INFO,
        MEDIA
    }

    interface Listener {
        fun onRendered(templateId: String, dialogRequestId: String)
        fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    /**
     * This should be called when occur interaction(input event such as touch, drag, etc...) for display
     *
     * @param templateId the unique identifier for the template card
     */
    fun notifyUserInteraction(templateId: String)

    interface OnTriggerChildCallback: EventCallback<DisplayInterface.ErrorType>
    fun triggerChild(templateId: String, playServiceId: String, data: JsonObject, callback: OnTriggerChildCallback?)

    /**
     * The renderer of display agent.
     * When receive an directive for display, the agent will request the renderer to render it.
     */
    interface Renderer {
        /**
         * Used to notify the renderer when received a display directive.
         *
         * It is a good time to display template.
         *
         * If true returned, the renderer should call [displayCardRendered] after display rendered.
         *
         * @param templateId the unique identifier for the template card
         * @param templateType the template type
         * @param templateContent the content of template in structured JSON
         * @param header the header for this render
         * @param contextLayer the layer type
         * @param parentTemplateId the parent template id, null if not exist.
         * @return true: if will render, false: otherwise
         */
        fun render(templateId: String, templateType: String, templateContent: String, header: Header, contextLayer: ContextLayer, parentTemplateId: String?): Boolean

        /**
         * Used to notify the renderer when display should be cleared .
         *
         * the renderer should call [displayCardRendered] after display cleared.
         *
         * @param templateId the unique identifier for the template card
         * @param force true: the display should be cleared, false: recommend to clear.
         */
        fun clear(templateId: String, force: Boolean)

        /**
         * Used to notify the renderer when display should be updated. .
         *
         * @param templateId the unique identifier for the template card
         * @param templateContent the content of template in structured JSON which should be updated. The content consist of partial or full elements for templateContent of [render]
         */
        fun update(templateId: String, templateContent: String)
    }

    interface Controller {
        /**
         * Used to notify the renderer when display should change focus of item.
         *
         * @param direction the direction to which change focus
         * @return true: success, false: otherwise
         */
        fun controlFocus(direction: Direction): Boolean

        /**
         * Used to notify the renderer when display should scroll
         *
         * @param direction the direction to which scroll
         * @return true: success, false: otherwise
         */
        fun controlScroll(direction: Direction): Boolean

        /**
         * Returns a token which has focus currently.
         * @return the token if exist, otherwise  null.
         */
        fun getFocusedItemToken(): String?

        /**
         * Return visible token list.
         * @return the visible token list in order
         */
        fun getVisibleTokenList(): List<String>?
    }
}