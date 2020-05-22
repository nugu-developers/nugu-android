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

package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.core.interfaces.common.EventCallback

interface ElementSelectedHandler {
    /**
     * enum class for ErrorType
     */
    enum class ErrorType {
        REQUEST_FAIL,
        RESPONSE_TIMEOUT
    }

    /**
     * callback interface for [setElementSelected]
     */
    interface OnElementSelectedCallback : EventCallback<ErrorType>

    /**
     * Each element has it's own token.
     *
     * This should be called when element selected(clicked) by the renderer.
     *
     * @param templateId the unique identifier for the template card
     * @param token the unique identifier for the element
     * @param focusedItemToken the token which has focus currently.
     * @param visibleTokenList visible token list.
     * @param callback the result callback for element selected event
     * @throws IllegalStateException when received invalid call.
     * for example, when display for given [templateId] is invalid (maybe cleared or not rendered)
     * @return the dialogRequestId for request
     */
    fun setElementSelected(templateId: String, token: String, focusedItemToken: String?, visibleTokenList: List<String>?, callback: OnElementSelectedCallback? = null): String
}