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
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class NudgeAgent(
    private val contextManager: ContextManagerInterface
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    NudgeInfoObserver,
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

    private val executor = Executors.newSingleThreadExecutor()
    private var dialogRequestId: String? = null
    private var nudgeInfo: NudgeDirectiveHandler.Payload? = null

    override fun getInterfaceName(): String = NAMESPACE

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {

        executor.submit {
            if (nudgeInfo == null) {
                Logger.e(TAG, "provideState(). nudgeInfo null")
            }

            if (contextType == ContextType.COMPACT || nudgeInfo == null) {
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
                    StateContext(nudgeInfo!!.toString()),
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    private fun setStateProvider() {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    private fun releaseStateProvider() {
        contextManager.setStateProvider(namespaceAndName, null)
        nudgeInfo = null
        dialogRequestId = null
    }

    override fun onNudgeAppendDirective(dialogRequestId: String, nudgeInfo: NudgeDirectiveHandler.Payload) {
        this.nudgeInfo = nudgeInfo
        this.dialogRequestId = dialogRequestId

        setStateProvider()
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        (this.dialogRequestId == dialogRequestId).let { isDialogIdSame ->
            Logger.d(TAG, "onTTSStateChanged $state, is dialogRequestId same?  $isDialogIdSame")

            if (isDialogIdSame) {
                when (state) {
                    TTSAgentInterface.State.IDLE,
                    TTSAgentInterface.State.FINISHED
                    -> {
                        if (nudgeInfo?.speakTTSExist == true &&
                            nudgeInfo?.displayTemplateExist != true &&
                            nudgeInfo?.expectSpeechExist != true
                        ) {
                            releaseStateProvider()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onStateChanged(state: ASRAgentInterface.State) {
        Logger.d(TAG, "onASRStateChanged $state")

        //todo. dialogRequestID may be needed.
        when (state) {
            ASRAgentInterface.State.BUSY,
            ASRAgentInterface.State.IDLE
            -> {
                if (nudgeInfo?.expectSpeechExist == true &&
                    nudgeInfo?.displayTemplateExist != true
                ) {
                    releaseStateProvider()
                }
            }
            else -> Unit
        }
    }

    override fun onRendered(templateId: String, dialogRequestId: String) {
        //skip
    }

    override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
        (this.dialogRequestId == dialogRequestId).let { isDialogIdSame ->
            Logger.d(TAG, "onDisplayCleared. is dialogRequestId same?  $isDialogIdSame")

            if (this.dialogRequestId == dialogRequestId) {
                releaseStateProvider()
            }
        }
    }
}