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

import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayInterface

/**
 * There are some displays to be render.
 *
 * This is a helper interface to aggregate those displays to handle at one place.
 *
 * @see [com.skt.nugu.sdk.agent.display.DisplayAgentInterface]
 */
interface DisplayAggregatorInterface:
    DisplayInterface<DisplayAggregatorInterface.Renderer> {
    /**
     * Enum class for display types
     */
    enum class Type {
        AUDIO_PLAYER,
        INFOMATION,
        ALERT,
        CALL
    }

    /**
     * The renderer for DisplayAggregator
     * Similar to [com.skt.nugu.sdk.agent.display.DisplayAgentInterface.Renderer]
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
         * @param dialogRequestId the dialog request id
         * @param displayType the display type of display
         * @return true: if will render, false: otherwise
         */
        fun render(templateId: String, templateType: String, templateContent: String, dialogRequestId: String, displayType: Type): Boolean

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

        /**
         * Used to notify the renderer when display should change focus of item.
         *
         * @param templateId the unique identifier for the template card
         * @param direction the direction to which change focus
         * @return true: success, false: otherwise
         */
        fun controlFocus(templateId: String, direction: Direction): Boolean

        /**
         * Used to notify the renderer when display should scroll
         *
         * @param templateId the unique identifier for the template card
         * @param direction the direction to which scroll
         * @return true: success, false: otherwise
         */
        fun controlScroll(templateId: String, direction: Direction): Boolean

        /**
         * Returns a token which has focus currently.
         * @return the token if exist, otherwise  null.
         */
        fun getFocusedItemToken(templateId: String): String?

        /**
         * Return visible token list.
         * @return the visible token list in order
         */
        fun getVisibleTokenList(templateId: String): List<String>?
    }
}