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
package com.skt.nugu.sdk.agent

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Directive
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractDirectiveHandler: DirectiveHandler {
    /**
     * The wrapping interface for [Directive] & [DirectiveHandlerResult]
     * @property directive the directive
     * @property result the result handler for directive
     */
    interface DirectiveInfo {
        val directive: Directive
        val result: DirectiveHandlerResult
    }

    private data class DirectiveInfoImpl(
        override val directive: Directive,
        override val result: DirectiveHandlerResult
    ) : DirectiveInfo {
        /**
         * flag for cancellation
         */
        internal var isCancelled = false
    }

    private val directiveInfoMap = ConcurrentHashMap<String, DirectiveInfoImpl>()

    override fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult) {
        val messageId = directive.getMessageId()

        if (getDirectiveInfo(messageId) != null) {
            // already exist
            return
        }

        val info = createDirectiveInfo(directive,
            // wrap result to remove directive from map.
            object : DirectiveHandlerResult {
                override fun setCompleted() {
                    directiveInfoMap.remove(messageId)?.let {
                        result.setCompleted()
                    }
                }

                override fun setFailed(
                    description: String,
                    cancelPolicy: DirectiveHandlerResult.CancelPolicy
                ) {
                    directiveInfoMap.remove(messageId)?.let {
                        result.setFailed(description, cancelPolicy)
                    }
                }
            })
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
        // It is ok to remove here.
        val info = directiveInfoMap.remove(messageId) ?: return

        info.isCancelled = true
        cancelDirective(info)
    }

    private fun getDirectiveInfo(messageId: String) = directiveInfoMap[messageId]

    private fun createDirectiveInfo(
        directive: Directive,
        result: DirectiveHandlerResult
    ) = DirectiveInfoImpl(
            directive,
            result
        )

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
    @Deprecated("removed soon")
    protected fun removeDirective(messageId: String) {
        directiveInfoMap.remove(messageId)
    }
}