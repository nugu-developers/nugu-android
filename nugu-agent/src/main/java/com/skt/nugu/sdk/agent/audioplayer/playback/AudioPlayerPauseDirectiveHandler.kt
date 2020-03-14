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
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger

class AudioPlayerPauseDirectiveHandler(
    private val controller: Controller
) : AbstractDirectiveHandler(), DirectiveGroupProcessorInterface.Listener {
    companion object {
        private const val NAME_PAUSE = "Pause"
        private val PAUSE = NamespaceAndName(DefaultAudioPlayerAgent.NAMESPACE, NAME_PAUSE)
    }
    interface Controller {
        fun onReceivePause(directive: Directive)
        fun onExecutePause(directive: Directive)
        fun onCancelPause(directive: Directive)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
        info.result.setCompleted()
        controller.onExecutePause(info.directive)
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
        controller.onCancelPause(info.directive)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        put(
            PAUSE , BlockingPolicy(
            BlockingPolicy.MEDIUM_AUDIO,
            false
        ))
    }

    override fun onReceiveDirectives(directives: List<Directive>) {
        val directive = directives.firstOrNull() {
            it.getNamespaceAndName() == PAUSE
        }

        directive?.let {
            controller.onReceivePause(it)
        }
    }
}