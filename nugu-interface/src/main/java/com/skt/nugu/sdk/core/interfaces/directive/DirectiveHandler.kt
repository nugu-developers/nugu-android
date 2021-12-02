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
package com.skt.nugu.sdk.core.interfaces.directive

import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName

/**
 * The interface for directive handler
 */
interface DirectiveHandler {
    /**
     * Perform pre-processing before handleDirective.
     * This will be called regardless of [BlockingPolicy]
     * @param directive the directive
     * @param result the handler for directive result
     */
    fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult)
    /**
     * Handle directive which preHandled.
     * @param messageId the messageId for directive
     */
    fun handleDirective(messageId: String): Boolean
    /**
     * Cancel directive which prehandled or handling.
     * @param messageId the messageId for directive
     */
    fun cancelDirective(messageId: String)
    /**
     *
     * @return namespace and name for directive which can handle and it's blocking policy.
     */
    val configurations : Map<NamespaceAndName, BlockingPolicy>
}