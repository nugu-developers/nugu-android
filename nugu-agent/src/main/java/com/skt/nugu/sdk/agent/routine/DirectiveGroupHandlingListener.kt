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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DirectiveGroupHandlingListener(
    private val dialogRequestId: String,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveResultListener: OnDirectiveResultListener,
    private val directiveGroupPrepareListener: OnDirectiveGroupPrepareListener? = null
) : DirectiveGroupProcessorInterface.Listener, DirectiveSequencerInterface.OnDirectiveHandlingListener {
    companion object {
        private const val TAG = "DirectiveGroupHandlingListener"
    }

    interface OnDirectiveGroupPrepareListener {
        /**
         * Called when all directive's prepared for handling.
         * @param directives the directives which prepared.
         */
        fun onPrepared(directives: List<Directive>)
    }

    sealed class Result {
        object COMPLETE : Result()
        object CANCELED : Result()
        object FAILED : Result()
        object SKIPPED: Result()
    }

    interface OnDirectiveResultListener {
        /**
         * Called when all directive's handling finished
         * @param results the results for all directive's handling
         */
        fun onFinish(results: Map<Directive, Result>)

        /**
         * Called when a [directive] completed
         * @param directive a completed directive
         */
        fun onCompleted(directive: Directive)

        /**
         * Called when a [directive] canceled
         * @param directive a canceled directive
         */
        fun onCanceled(directive: Directive)

        /**
         * Called when a [directive] failed
         * @param directive a failed directive
         */
        fun onFailed(directive: Directive)

        /**
         * Called when a [directive] skipped
         * @param directive a skipped directive
         */
        fun onSkipped(directive: Directive)
    }

    private val directives = CopyOnWriteArrayList<Directive>()
    private val results = ConcurrentHashMap<Directive, Result>()

    init {
        directiveSequencer.addOnDirectiveHandlingListener(this)
        directiveGroupProcessor.addListener(this)
    }

    override fun onPostProcessed(directives: List<Directive>) {
        if (directives.firstOrNull()?.header?.dialogRequestId == dialogRequestId) {
            Logger.d(TAG, "[onReceiveDirectives] dialogRequestId: $dialogRequestId")
            directiveGroupProcessor.removeListener(this)
            this.directives.addAll(directives)
            directiveGroupPrepareListener?.onPrepared(directives)
        }
    }

    override fun onRequested(directive: Directive) {
        // no-op
    }

    override fun onCompleted(directive: Directive) {
        if(!directives.remove(directive)) {
            return
        }

        Logger.d(TAG, "[onCompleted] ${directive.header}")
        results[directive] = Result.COMPLETE
        directiveResultListener.onCompleted(directive)
        notifyIfAllDirectiveHandled()
    }

    override fun onCanceled(directive: Directive) {
        if(!directives.remove(directive)) {
            return
        }

        Logger.d(TAG, "[onCanceled] ${directive.header}")
        results[directive] = Result.CANCELED
        directiveResultListener.onCanceled(directive)
        notifyIfAllDirectiveHandled()
    }

    override fun onFailed(directive: Directive, description: String) {
        if(!directives.remove(directive)) {
            return
        }

        Logger.d(TAG, "[onFailed] ${directive.header}")
        results[directive] = Result.FAILED
        directiveResultListener.onFailed(directive)
        notifyIfAllDirectiveHandled()
    }

    override fun onSkipped(directive: Directive) {
        if(!directives.remove(directive)) {
            return
        }

        Logger.d(TAG, "[onSkipped] ${directive.header}")
        results[directive] = Result.SKIPPED
        directiveResultListener.onSkipped(directive)
        notifyIfAllDirectiveHandled()
    }

    private fun notifyIfAllDirectiveHandled() {
        if(directives.isEmpty()) {
            Logger.d(TAG, "[notifyResultIfEmpty] dialogRequestId: $dialogRequestId")
            directiveSequencer.removeOnDirectiveHandlingListener(this)
            directiveResultListener.onFinish(results)
        }
    }
}