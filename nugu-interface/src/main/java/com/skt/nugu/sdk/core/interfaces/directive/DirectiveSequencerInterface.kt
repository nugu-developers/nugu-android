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

/**
 * Interface for sequencing and handling directives.
 */
interface DirectiveSequencerInterface {
    interface OnDirectiveHandlingListener {
        fun onRequested(directive: Directive)
        fun onCompleted(directive: Directive)
        fun onCanceled(directive: Directive)
        fun onFailed(directive: Directive, description: String)
    }

    fun addOnDirectiveHandlingListener(listener: OnDirectiveHandlingListener)
    fun removeOnDirectiveHandlingListener(listener: OnDirectiveHandlingListener)
    fun addDirectiveHandler(handler: DirectiveHandler): Boolean
    fun removeDirectiveHandler(handler: DirectiveHandler): Boolean
    fun onDirective(directive: Directive): Boolean
    fun disable()
    fun enable()
}