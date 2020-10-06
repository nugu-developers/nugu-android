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

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreprocessor
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import java.util.concurrent.CopyOnWriteArraySet

class DirectiveGroupProcessor(
    private val directiveSequencer: DirectiveSequencerInterface
) : DirectiveGroupHandler,
    DirectiveGroupProcessorInterface {
    private val directiveGroupPreprocessors = HashSet<DirectiveGroupPreprocessor>()
    private val prePreprocessedListeners = HashSet<DirectiveGroupProcessorInterface.Listener>()
    private val postPreprocessedListeners = CopyOnWriteArraySet<DirectiveGroupProcessorInterface.Listener>()

    override fun onReceiveDirectives(directives: List<Directive>) {
        prePreprocessedListeners.forEach {
            it.onReceiveDirectives(directives)
        }

        var processedDirectives = directives

        directiveGroupPreprocessors.forEach {
            processedDirectives = it.preprocess(processedDirectives)
        }

        postPreprocessedListeners.forEach {
            it.onReceiveDirectives(processedDirectives)
        }
        
        processedDirectives.forEach {
            directiveSequencer.onDirective(it)
        }
    }

    override fun addPreProcessedListener(listener: DirectiveGroupProcessorInterface.Listener) {
        prePreprocessedListeners.add(listener)
    }

    override fun removePreProcessedListener(listener: DirectiveGroupProcessorInterface.Listener) {
        prePreprocessedListeners.remove(listener)
    }

    override fun addPostProcessedListener(listener: DirectiveGroupProcessorInterface.Listener) {
        postPreprocessedListeners.add(listener)
    }

    override fun removePostProcessedListener(listener: DirectiveGroupProcessorInterface.Listener) {
        postPreprocessedListeners.remove(listener)
    }

    override fun addDirectiveGroupPreprocessor(directiveGroupPreprocessor: DirectiveGroupPreprocessor) {
        directiveGroupPreprocessors.add(directiveGroupPreprocessor)
    }

    override fun removeDirectiveGroupPreprocessor(directiveGroupPreprocessor: DirectiveGroupPreprocessor) {
        directiveGroupPreprocessors.remove(directiveGroupPreprocessor)
    }
}