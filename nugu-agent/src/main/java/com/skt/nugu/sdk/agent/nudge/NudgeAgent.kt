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
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.battery.DefaultBatteryAgent
import com.skt.nugu.sdk.agent.display.DisplayAgent
import com.skt.nugu.sdk.agent.nudge.NudgeDirectiveHandler.Companion.isAppendDirective
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.Executors

class NudgeAgent(
    contextManager: ContextManagerInterface,
    playSynchronizer: PlaySynchronizerInterface
) : CapabilityAgent,
    SupportedInterfaceContextProvider,
    NudgeDirectiveObserver,
    DirectiveGroupProcessorInterface.Listener {

    companion object {
        internal const val TAG = "NudgeAgent"
        const val NAMESPACE = "Nudge"

        val VERSION = Version(1, 0)
    }

    internal data class StateContext(private val nudgeInfo: JsonObject) : BaseContextState {
        companion object {
            private val CompactState = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = CompactState.toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = CompactState.apply {
            add("nudgeInfo", nudgeInfo)
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
    internal var nudgeData: NudgeData? = null

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        contextManager.setStateProvider(namespaceAndName, this)
        playSynchronizer.addListener(object : PlaySynchronizerInterface.Listener {
            override fun onSyncStateChanged(
                prepared: Set<PlaySynchronizerInterface.SynchronizeObject>,
                started: Set<PlaySynchronizerInterface.SynchronizeObject>
            ) {
                var log = "onSyncStateChanged() nudgeData Exist: ${nudgeData != null}, "

                nudgeData?.run {
                    val preparedObjectExist = prepared.any { it.dialogRequestId == dialogRequestId }
                    val startedObjectExist = started.any { it.dialogRequestId == dialogRequestId }
                    log += "preparedObject Exist: $preparedObjectExist, startedObject Exist: $startedObjectExist"
                    if (!preparedObjectExist && !startedObjectExist) {
                        clearNudgeData()
                    }
                }

                Logger.d(TAG, log)
            }
        })
    }

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
                            StateContext(it)
                        }
                    },
                    StateRefreshPolicy.ALWAYS,
                    contextType,
                    stateRequestToken
                )
            }
        }
    }

    internal fun clearNudgeData() {
        executor.submit {
            Logger.d(TAG, "clearNudgeData")
            nudgeData = null
        }
    }

    override fun onPreProcessed(directives: List<Directive>) {
        val nudgeDirective = directives.find { isAppendDirective(it.header.namespace, it.header.name) }

        fun isExpectSpeechDirectiveExist() =
            directives.any { it.header.namespace == DefaultASRAgent.NAMESPACE && it.header.name == DefaultASRAgent.NAME_EXPECT_SPEECH }

        fun isSpeakTTSDirectiveExist() =
            directives.any { it.header.namespace == DefaultTTSAgent.SPEAK.namespace && it.header.name == DefaultTTSAgent.SPEAK.name }

        fun isDisplayDirectiveExist() = directives.any { it.header.namespace == DisplayAgent.NAMESPACE }

        Logger.d(TAG, "onDirectiveGroupPreProcessed(). nudgeDirective :  $nudgeDirective")

        nudgeDirective?.let {
            executor.submit {
                nudgeData = NudgeData(null,
                    it.getDialogRequestId(),
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
}