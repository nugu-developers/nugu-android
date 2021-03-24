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

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.DefaultDisplayAgent
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.nudge.NudgeDirectiveHandler.Companion.isAppendDirective
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class NudgeAgent(
    contextManager: ContextManagerInterface
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    NudgeDirectiveObserver,
    DirectiveGroupProcessorInterface.Listener,
    TTSAgentInterface.Listener,
    ASRAgentInterface.OnStateChangeListener, DisplayAgentInterface.Listener {

    companion object {
        private const val TAG = "NudgeAgent"
        const val NAMESPACE = "Nudge"

        val VERSION = Version(1, 0)
    }

    internal data class StateContext(private val nudgeInfo: String) : BaseContextState {
        companion object {
            private val CompactState = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = CompactState.toString()
            }
        }

        override fun value(): String = CompactState.apply {
            addProperty("nudgeInfo", nudgeInfo)
        }.toString()
    }

    internal data class NudgeData(
        var nudgeInfo: JsonObject?,
        var dialogRequestId: String,
        var expectSpeechExist: Boolean,
        var speakTTSExist: Boolean,
        var displayTemplateExist: Boolean
    )

    private val executor = Executors.newSingleThreadExecutor()
    private var nudgeData: NudgeData? = null

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun getInterfaceName(): String = NAMESPACE

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {

        executor.submit {
            if (contextType == ContextType.COMPACT) {
                contextSetter.setState(
                    namespaceAndName,
                    StateContext.CompactContextState,
                    StateRefreshPolicy.NEVER,
                    contextType,
                    stateRequestToken
                )
            } else {
                contextSetter.setState(
                    namespaceAndName,
                    nudgeData?.nudgeInfo.let {
                        if (it == null) {
                            StateContext.CompactContextState
                        } else {
                            StateContext(it.toString())
                        }
                    },
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    private fun clearNudgeData() {
        executor.submit {
            nudgeData = null
        }
    }

    override fun onPreProcessed(directives: List<Directive>) {
        val nudgeDirective = directives.find { isAppendDirective(it.header.namespace, it.header.name) }

        fun isExpectSpeechDirectiveExist() =
            directives.any { it.header.namespace == DefaultASRAgent.NAMESPACE && it.header.name == DefaultASRAgent.NAME_EXPECT_SPEECH }

        fun isSpeakTTSDirectiveExist() =
            directives.any { it.header.namespace == DefaultTTSAgent.SPEAK.namespace && it.header.name == DefaultTTSAgent.SPEAK.name }

        fun isDisplayDirectiveExist() = directives.any { it.header.namespace == DefaultDisplayAgent.NAMESPACE }

        Logger.d(TAG, "onDirectiveGroupPreProcessed(). nudgeDirective :  $nudgeDirective")

        nudgeDirective?.let { nudgeDirective ->
            executor.submit {
                nudgeData = NudgeData(null,
                    nudgeDirective.getDialogRequestId(),
                    isExpectSpeechDirectiveExist(),
                    isSpeakTTSDirectiveExist(),
                    isDisplayDirectiveExist())
            }
        }
    }

    override fun onNudgeAppendDirective(dialogRequestId: String, nudgePayload: NudgeDirectiveHandler.Payload) {
        executor.submit {
            nudgeData?.let { nudgeData ->
                if (nudgeData.dialogRequestId == dialogRequestId) {
                    nudgeData.nudgeInfo = nudgePayload.nudgeInfo
                }
            }
        }
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        executor.submit {
            (nudgeData?.dialogRequestId == dialogRequestId).let { isDialogIdSame ->
                Logger.d(TAG, "onTTSStateChanged $state, is dialogRequestId same?  $isDialogIdSame")

                if (isDialogIdSame) {
                    when (state) {
                        TTSAgentInterface.State.IDLE,
                        TTSAgentInterface.State.FINISHED
                        -> {
                            if (nudgeData?.speakTTSExist == true &&
                                nudgeData?.displayTemplateExist != true &&
                                nudgeData?.expectSpeechExist != true
                            ) {
                                clearNudgeData()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onStateChanged(state: ASRAgentInterface.State) {
        Logger.d(TAG, "onASRStateChanged $state")

        executor.submit {
            //todo. dialogRequestID may be needed.
            when (state) {
                ASRAgentInterface.State.BUSY,
                ASRAgentInterface.State.IDLE
                -> {
                    if (nudgeData?.expectSpeechExist == true &&
                        nudgeData?.displayTemplateExist != true
                    ) {
                        clearNudgeData()
                    }
                }
                else -> Unit
            }
        }
    }

    override fun onRendered(templateId: String, dialogRequestId: String) {
        //skip
    }

    override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
        executor.submit {
            (nudgeData?.dialogRequestId == dialogRequestId).let { isDialogIdSame ->
                Logger.d(TAG, "onDisplayCleared. is dialogRequestId same?  $isDialogIdSame")

                if (isDialogIdSame) {
                    clearNudgeData()
                }
            }
        }
    }
}