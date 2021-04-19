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

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreProcessor
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import java.util.concurrent.CopyOnWriteArraySet

class DirectiveGroupProcessor(
    private val directiveSequencer: DirectiveSequencerInterface
) : DirectiveGroupHandler,
    DirectiveGroupProcessorInterface {
    private val directiveGroupPreprocessors = HashSet<DirectiveGroupPreProcessor>()
    private val listeners = CopyOnWriteArraySet<DirectiveGroupProcessorInterface.Listener>()

    override fun onReceiveDirectives(directives: List<Directive>) {
        listeners.forEach {
            it.onPreProcessed(directives)
        }

        var processedDirectives = directives

        directiveGroupPreprocessors.forEach {
            processedDirectives = it.preProcess(processedDirectives)
        }

        listeners.forEach {
            it.onPostProcessed(processedDirectives)
        }

        directiveSequencer.onDirectives(processedDirectives)
    }

    override fun addListener(listener: DirectiveGroupProcessorInterface.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: DirectiveGroupProcessorInterface.Listener) {
        listeners.remove(listener)
    }

    override fun addDirectiveGroupPreprocessor(directiveGroupPreProcessor: DirectiveGroupPreProcessor) {
        directiveGroupPreprocessors.add(directiveGroupPreProcessor)
    }

    override fun removeDirectiveGroupPreprocessor(directiveGroupPreProcessor: DirectiveGroupPreProcessor) {
        directiveGroupPreprocessors.remove(directiveGroupPreProcessor)
    }
}