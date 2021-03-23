/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.nudge

import com.google.gson.Gson
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreProcessor
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger

class NudgeDirectivePreProcessor : DirectiveGroupPreProcessor {
    companion object {
        private const val TAG = "NudgeDirectivePreProcessor"
    }

    override fun preProcess(directives: List<Directive>): List<Directive> {
        fun getNudgeDirective() = directives.find { NudgeDirectiveHandler.isAppendDirective(it.header.namespace, it.header.name) }

        fun isExpectSpeechDirectiveExist() =
            directives.any { it.header.namespace == DefaultASRAgent.NAMESPACE && it.header.name == DefaultASRAgent.NAME_EXPECT_SPEECH }

        fun isSpeakTTSDirectiveExist() =
            directives.any { it.header.namespace == DefaultTTSAgent.SPEAK.namespace && it.header.name == DefaultTTSAgent.SPEAK.name }

        // todo. what about media template
        fun isDisplayDirectiveExist() = directives.any { it.header.namespace == DefaultDisplayAgent.NAMESPACE }

        getNudgeDirective()?.let { nudgeDirective ->
            val payload = MessageFactory.create(nudgeDirective.payload, NudgeDirectiveHandler.Payload::class.java)
            payload?.apply {
                expectSpeechExist = isExpectSpeechDirectiveExist()
                speakTTSExist = isSpeakTTSDirectiveExist()
                displayTemplateExist = isDisplayDirectiveExist()

                Logger.d(TAG, "update nudge.append directive payload to $this")
                nudgeDirective.payload = Gson().toJson(this)
            }
        }

        return directives
    }
}