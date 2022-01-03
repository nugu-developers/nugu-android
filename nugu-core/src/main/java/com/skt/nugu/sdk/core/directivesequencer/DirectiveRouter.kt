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
package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DirectiveRouter {
    companion object {
        private const val TAG = "DirectiveRouter"
    }

    private val lock = ReentrantLock()
    private val handlers = HashSet<DirectiveHandler>()

    fun addDirectiveHandler(handler: DirectiveHandler) {
        lock.withLock {
            handlers.add(handler)
        }
    }

    fun removeDirectiveHandler(handler: DirectiveHandler) {
        lock.withLock {
            handlers.remove(handler)
        }
    }

    fun preHandleDirective(directive: Directive, result: DirectiveHandlerResult): Boolean {
        val handler = getDirectiveHandler(directive)
        return if (handler != null) {
            handler.preHandleDirective(directive, result)
            true
        } else {
            Logger.w(TAG, "[preHandleDirective] no handler for ${directive.getNamespaceAndName()}")
            false
        }
    }

    fun handleDirective(directive: Directive): Boolean {
        val handler = getDirectiveHandler(directive)
        return if (handler != null) {
            handler.handleDirective(directive.getMessageId())
            true
        } else {
            false
        }
    }

    fun cancelDirective(directive: Directive): Boolean {
        val handler = getDirectiveHandler(directive)
        return if (handler != null) {
            handler.cancelDirective(directive.getMessageId())
            true
        } else {
            false
        }
    }

    private fun getDirectiveHandler(directive: Directive): DirectiveHandler? = lock.withLock {
        handlers.find { it.getConfiguration().containsKey(directive.getNamespaceAndName()) }
    }

    fun getPolicy(directive: Directive): BlockingPolicy =
        getDirectiveHandler(directive)?.getConfiguration()?.get(directive.getNamespaceAndName())
            ?: BlockingPolicy()
}