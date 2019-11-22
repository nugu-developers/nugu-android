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
package com.skt.nugu.sdk.core.interfaces.capability

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a base class for CapabilityAgent which should perform following roles:
 * * should provide configurations for directives which can handle
 * * should handle directives which provided
 * * should provide an capability context's state
 */
abstract class CapabilityAgent :
    DirectiveHandler
    , ContextStateProvider {
    /**
     * The wrapping class for [Directive] & [DirectiveHandlerResult]
     * @param directive the directive
     * @param result the result handler for directive
     */
    data class DirectiveInfo(
        val directive: Directive,
        val result: DirectiveHandlerResult
    ) {
        /**
         * flag for cancellation
         */
        var isCancelled = false
    }

    private val directiveInfoMap = ConcurrentHashMap<String, DirectiveInfo>()

    override fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult) {
        val messageId = directive.getMessageId()

        if (getDirectiveInfo(messageId) != null) {
            // already exist
            return
        }

        val info = createDirectiveInfo(directive, result)
        directiveInfoMap[directive.getMessageId()] = info

        preHandleDirective(info)
    }

    override fun handleDirective(messageId: String): Boolean {
        val info = getDirectiveInfo(messageId)

        return if (info != null) {
            handleDirective(info)
            true
        } else {
            false
        }
    }

    override fun cancelDirective(messageId: String) {
        val info = getDirectiveInfo(messageId)

        if (info == null) {
            return
        }

        info.isCancelled = true
        cancelDirective(info)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        // default no op
    }

    private fun getDirectiveInfo(messageId: String): DirectiveInfo? = directiveInfoMap[messageId]

    private fun createDirectiveInfo(
        directive: Directive,
        result: DirectiveHandlerResult
    ): DirectiveInfo =
        DirectiveInfo(directive, result)

    /**
     * Convenient wrapper for [DirectiveHandler.preHandleDirective]
     */
    protected abstract fun preHandleDirective(info: DirectiveInfo)

    /**
     * Convenient wrapper for [DirectiveHandler.handleDirective]
     */
    protected abstract fun handleDirective(info: DirectiveInfo)

    /**
     * Convenient wrapper for [DirectiveHandler.cancelDirective]
     */
    protected abstract fun cancelDirective(info: DirectiveInfo)

    /**
     * Remove directive from map which managed.
     * @param messageId the messageId for directive.
     */
    protected fun removeDirective(messageId: String) {
        directiveInfoMap.remove(messageId)
    }
}