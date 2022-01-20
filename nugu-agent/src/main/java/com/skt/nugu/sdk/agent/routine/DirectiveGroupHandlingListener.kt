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

package com.skt.nugu.sdk.agent.routine

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArrayList

class DirectiveGroupHandlingListener(
    private val dialogRequestId: String,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveResultListener: OnDirectiveResultListener
) : DirectiveGroupProcessorInterface.Listener, DirectiveSequencerInterface.OnDirectiveHandlingListener {
    companion object {
        private const val TAG = "DirectiveGroupHandlingListener"
    }

    interface OnDirectiveResultListener {
        fun onFinish(isExistCanceledOrFailed: Boolean)
        fun onCanceled(directive: Directive)
        fun onFailed(directive: Directive)
    }

    private val directives = CopyOnWriteArrayList<Directive>()
    private var existCanceledOrFailed = false

    init {
        directiveSequencer.addOnDirectiveHandlingListener(this)
        directiveGroupProcessor.addListener(this)
    }

    override fun onPostProcessed(directives: List<Directive>) {
        if (directives.firstOrNull()?.header?.dialogRequestId == dialogRequestId) {
            Logger.d(TAG, "[onReceiveDirectives] dialogRequestId: $dialogRequestId")
            directiveGroupProcessor.removeListener(this)
            this.directives.addAll(directives)
        }
    }

    override fun onRequested(directive: Directive) {
        // no-op
    }

    override fun onCompleted(directive: Directive) {
        if(directives.remove(directive)) {
            Logger.d(TAG, "[onCompleted] ${directive.header}")
            notifyResultIfEmpty()
        }
    }

    override fun onCanceled(directive: Directive) {
        if(directives.remove(directive)) {
            Logger.d(TAG, "[onCanceled] ${directive.header}")
            existCanceledOrFailed = true
            directiveResultListener.onCanceled(directive)
            notifyResultIfEmpty()
        }
    }

    override fun onFailed(directive: Directive, description: String) {
        if(directives.remove(directive)) {
            Logger.d(TAG, "[onFailed] ${directive.header}")
            existCanceledOrFailed = true
            directiveResultListener.onFailed(directive)
            notifyResultIfEmpty()
        }
    }

    override fun onSkipped(directive: Directive) {
        if(directives.remove(directive)) {
            Logger.d(TAG, "[onSkipped] ${directive.header}")
            notifyResultIfEmpty()
        }
    }

    private fun notifyResultIfEmpty() {
        if(directives.isEmpty()) {
            Logger.d(TAG, "[notifyResultIfEmpty] dialogRequestId: $dialogRequestId, existCanceledOrFailed: $existCanceledOrFailed")
            directiveSequencer.removeOnDirectiveHandlingListener(this)
            directiveResultListener.onFinish(existCanceledOrFailed)
        }
    }
}