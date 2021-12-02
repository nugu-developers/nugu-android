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

package com.skt.nugu.sdk.agent.audioplayer.playback

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive

class PlaybackDirectiveHandler(
    private val controller: Controller,
    private val configuration: Pair<NamespaceAndName, BlockingPolicy>
) : AbstractDirectiveHandler(), DirectiveGroupProcessorInterface.Listener {
    interface Controller {
        fun onReceive(directive: Directive)
        fun onPreExecute(directive: Directive): Boolean
        fun onExecute(directive: Directive)
        fun onCancel(directive: Directive)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        if(!controller.onPreExecute(info.directive)) {
            info.result.setFailed("[preHandleDirective] failed")
        }
    }

    override fun handleDirective(info: DirectiveInfo) {
        info.result.setCompleted()
        controller.onExecute(info.directive)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        controller.onCancel(info.directive)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[configuration.first] = configuration.second
    }

    override fun onPostProcessed(directives: List<Directive>) {
        val directive = directives.firstOrNull() {
            it.getNamespaceAndName() == configuration.first
        }

        directive?.let {
            controller.onReceive(it)
        }
    }
}