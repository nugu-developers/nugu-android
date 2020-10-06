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
    private val finishListener: OnFinishListener
) : DirectiveGroupProcessorInterface.Listener, DirectiveSequencerInterface.OnDirectiveHandlingListener {
    companion object {
        private const val TAG = "DirectiveGroupHandlingListener"
    }

    interface OnFinishListener {
        fun onFinish()
    }

    init {
        directiveSequencer.addOnDirectiveHandlingListener(this)
        directiveGroupProcessor.addPostProcessedListener(this)
    }

    private var directives = CopyOnWriteArrayList<Directive>()

    override fun onReceiveDirectives(directives: List<Directive>) {
        if(directives.firstOrNull()?.header?.dialogRequestId == dialogRequestId) {
            Logger.d(TAG, "[onReceiveDirectives] dialogRequestId: $dialogRequestId")
            directiveGroupProcessor.removePostProcessedListener(this)
            this.directives.addAll(directives)
        }
    }

    override fun onRequested(directive: Directive) {
        // no-op
    }

    override fun onCompleted(directive: Directive) {
        Logger.d(TAG, "[onCompleted] ${directive.header}")
        if(directives.remove(directive)) {
            finishIfEmpty()
        }
    }

    override fun onCanceled(directive: Directive) {
        Logger.d(TAG, "[onCanceled] ${directive.header}")
        if(directives.remove(directive)) {
            finishIfEmpty()
        }
    }

    override fun onFailed(directive: Directive, description: String) {
        Logger.d(TAG, "[onFailed] ${directive.header}")
        if(directives.remove(directive)) {
            finishIfEmpty()
        }
    }

    private fun finishIfEmpty() {
        if(directives.isEmpty()) {
            Logger.d(TAG, "[finishIfEmpty] dialogRequestId: $dialogRequestId, finished")
            directiveSequencer.removeOnDirectiveHandlingListener(this)
            finishListener.onFinish()
        }
    }
}