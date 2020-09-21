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
package com.skt.nugu.sdk.agent.dialog

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.ChipsAgentInterface
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.*
import kotlin.collections.HashSet

class DialogUXStateAggregator(
    private val transitionDelayForIdleState: Long,
    private val sessionManager: SessionManagerInterface,
    private val seamlessFocusManager: SeamlessFocusManagerInterface,
    private val displayAgent: DisplayAgentInterface?
) :
    DialogUXStateAggregatorInterface
    , ASRAgentInterface.OnStateChangeListener
    , TTSAgentInterface.Listener
    , ConnectionStatusListener
    , InteractionControlManagerInterface.Listener
    , ChipsAgentInterface.Listener
{
    companion object {
        private const val TAG = "DialogUXStateAggregator"
    }

    data class NotifyParams(
        val state: DialogUXStateAggregatorInterface.DialogUXState,
        val dialogMode: Boolean,
        val chips: RenderDirective.Payload?
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners: MutableSet<DialogUXStateAggregatorInterface.Listener> = HashSet()
    private var currentState: DialogUXStateAggregatorInterface.DialogUXState =
        DialogUXStateAggregatorInterface.DialogUXState.IDLE
    private var isTtsPreparing = false
    private var isLastReceiveTtsDialogRequestId: String? = null
    private var ttsState = TTSAgentInterface.State.FINISHED
    private var asrState: ASRAgentInterface.State =
        ASRAgentInterface.State.IDLE
    private var dialogModeEnabled = false

    private val multiturnSpeakingToListeningScheduler = ScheduledThreadPoolExecutor(1)

    private var tryEnterIdleStateRunnableFuture: ScheduledFuture<*>? = null
    private var lastReceivedChips: RenderDirective? = null
    private var lastNotifyParams: NotifyParams? = null

    private val tryEnterIdleStateRunnable: Runnable = Runnable {
        executor.submit {
            Logger.d(TAG, "[tryEnterIdleStateRunnable] state: $currentState, dialogModeEnabled: $dialogModeEnabled, asrState: $asrState, ttsState: $ttsState, isTtsPreparing: $isTtsPreparing")
            if (currentState != DialogUXStateAggregatorInterface.DialogUXState.IDLE
                && !dialogModeEnabled
                && !asrState.isRecognizing()
                && (ttsState == TTSAgentInterface.State.FINISHED || ttsState == TTSAgentInterface.State.STOPPED) && !isTtsPreparing
            ) {
                setState(DialogUXStateAggregatorInterface.DialogUXState.IDLE)
            }
        }
    }

    private var displaySustainFuture: ScheduledFuture<*>? = null
    private val renderedDisplayTemplates = CopyOnWriteArraySet<String>()
    private val focusRequester = object: SeamlessFocusManagerInterface.Requester {}

    init {
        displayAgent?.addListener(object: DisplayAgentInterface.Listener {
            override fun onRendered(templateId: String) {
                renderedDisplayTemplates.add(templateId)
            }

            override fun onCleared(templateId: String) {
                renderedDisplayTemplates.remove(templateId)
            }
        })
    }

    override fun addListener(listener: DialogUXStateAggregatorInterface.Listener) {
        executor.submit {
            listeners.add(listener)
            val activeSessions = sessionManager.getActiveSessions()
            listener.onDialogUXStateChanged(currentState, dialogModeEnabled, getCurrentChips(activeSessions), activeSessions.isNotEmpty())
        }
    }

    override fun removeListener(listener: DialogUXStateAggregatorInterface.Listener) {
        executor.submit {
            listeners.remove(listener)
        }
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        Logger.d(TAG, "[onStateChanged-TTS] State: $state")
        isTtsPreparing = false
        ttsState = state

        executor.submit {
            when (state) {
                TTSAgentInterface.State.IDLE -> {
                    // never called.
                }
                TTSAgentInterface.State.PLAYING -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.SPEAKING)
                }
                TTSAgentInterface.State.STOPPED,
                TTSAgentInterface.State.FINISHED -> {
                    tryEnterIdleState()
                }
            }
        }
    }

    override fun onReceiveTTSText(text: String?, dialogRequestId: String) {
        Logger.d(TAG, "[onReceiveTTSText] text: $text, dialogRequestId: $dialogRequestId")
        isTtsPreparing = true
        isLastReceiveTtsDialogRequestId = dialogRequestId
    }

    override fun onError(dialogRequestId: String) {
        isTtsPreparing = false

        executor.submit {
            tryEnterIdleState()
        }
    }

    override fun onStateChanged(state: ASRAgentInterface.State) {
        Logger.d(TAG, "[onStateChanged-ASR] state: $state")
        val prevState = asrState

        asrState = state

        executor.submit {
            when (state) {
                ASRAgentInterface.State.IDLE -> {
                    if(prevState == ASRAgentInterface.State.BUSY) {
                        tryEnterIdleState()
                    } else {
                        tryEnterIdleState(0)
                    }
                }
                ASRAgentInterface.State.RECOGNIZING -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.LISTENING)
                }
                ASRAgentInterface.State.LISTENING -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING)
                }
                ASRAgentInterface.State.BUSY -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.THINKING)
                }
                ASRAgentInterface.State.EXPECTING_SPEECH -> {
                    // ignore
                }
            }
        }
    }

    override fun onMultiturnStateChanged(enabled: Boolean) {
        executor.submit {
            dialogModeEnabled = enabled
            if(!enabled) {
                tryEnterIdleState()
            }
        }
    }

    private fun setState(newState: DialogUXStateAggregatorInterface.DialogUXState) {
        tryEnterIdleStateRunnableFuture?.cancel(true)
        tryEnterIdleStateRunnableFuture = null

        if(newState == DialogUXStateAggregatorInterface.DialogUXState.IDLE) {
            seamlessFocusManager.cancel(focusRequester)
        } else {
            if(currentState == DialogUXStateAggregatorInterface.DialogUXState.IDLE) {
                seamlessFocusManager.prepare(focusRequester)
            }
        }

        currentState = newState
        notifyOnStateChangeIfSomethingChanged()

        displayAgent?.let {
            displaySustainFuture?.cancel(true)
            displaySustainFuture = when(newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING,
                DialogUXStateAggregatorInterface.DialogUXState.LISTENING,
                DialogUXStateAggregatorInterface.DialogUXState.THINKING -> {
                    multiturnSpeakingToListeningScheduler.scheduleAtFixedRate({
                        renderedDisplayTemplates.forEach {templateId->
                            it.notifyUserInteraction(templateId)
                        }
                    },0,1,TimeUnit.SECONDS)
                }
                else -> {
                    null
                }
            }
        }
    }

    private fun notifyOnStateChangeIfSomethingChanged() {
        val activeSessions = sessionManager.getActiveSessions()
        getCurrentChips(activeSessions).let { chips ->
            NotifyParams(currentState, dialogModeEnabled, chips).let {notifyParams ->
                if(notifyParams != lastNotifyParams) {
                    lastNotifyParams = notifyParams
                    listeners.forEach {
                        it.onDialogUXStateChanged(currentState, dialogModeEnabled, chips, activeSessions.isNotEmpty())
                    }
                }
            }
        }
    }

    private fun getCurrentChips(activeSessions: Map<String, SessionManagerInterface.Session>): RenderDirective.Payload? {
        val chips = lastReceivedChips
        Logger.d(TAG, "[getCurrentChips] activeSessions: $activeSessions, lastReceivedChips: $chips")

        if(chips == null) {
            return null
        }

        val chipsTime = UUIDGeneration.fromString(chips.header.dialogRequestId).getTime()
        var recentSessionTime = Long.MIN_VALUE

        // TODO : implementation dependent (dialogRequestId is time based value)
        activeSessions.forEach {
            UUIDGeneration.fromString(it.key).getTime().let {time->
                if(time > recentSessionTime) {
                    recentSessionTime = time
                }
            }
        }

        return when {
            recentSessionTime == chipsTime -> {
                chips.payload
            }
            recentSessionTime > chipsTime -> {
                // not valid lastReceivedChips anymore.
                lastReceivedChips = null
                null
            }
            else -> {
                null
            }
        }
    }

    override fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason: ConnectionStatusListener.ChangedReason) {
        executor.submit {
            if (status != ConnectionStatusListener.Status.CONNECTED) {
                setState(DialogUXStateAggregatorInterface.DialogUXState.IDLE)
            }
        }
    }

    override fun onReceiveChips(directive: RenderDirective) {
        executor.submit {
            lastReceivedChips = directive
        }
    }

    /**
     * Dialog관련 임의의 Activity가 끝났을 시, Idle 상태로 가기위해 시도
     * ex) TTSAgent Finish, Speech Recognition Finish
     */
    private fun tryEnterIdleState(delay: Long = transitionDelayForIdleState) {
        Logger.d(TAG, "[tryEnterIdleState] $dialogModeEnabled")
        tryEnterIdleStateRunnableFuture?.cancel(true)
        tryEnterIdleStateRunnableFuture = multiturnSpeakingToListeningScheduler.schedule(
            tryEnterIdleStateRunnable,
            delay,
            TimeUnit.MILLISECONDS
        )
    }
}