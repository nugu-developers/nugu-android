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
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class DialogUXStateAggregator(
    private val transitionDelayForIdleState: Long,
    private val sessionManager: SessionManagerInterface,
    private val displayAgent: DisplayAgentInterface?
) :
    DialogUXStateAggregatorInterface
    , ASRAgentInterface.OnStateChangeListener
    , TTSAgentInterface.Listener
    , ConnectionStatusListener
    , ASRAgentInterface.OnMultiturnListener
    , ChipsAgentInterface.Listener
{
    companion object {
        private const val TAG = "DialogUXStateAggregator"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners: MutableSet<DialogUXStateAggregatorInterface.Listener> = HashSet()
    private var currentState: DialogUXStateAggregatorInterface.DialogUXState =
        DialogUXStateAggregatorInterface.DialogUXState.IDLE
    private var isTtsPreparing = false
    private var ttsState = TTSAgentInterface.State.FINISHED
    private var asrState: ASRAgentInterface.State =
        ASRAgentInterface.State.IDLE
    private var dialogModeEnabled = false

    private val multiturnSpeakingToListeningScheduler = ScheduledThreadPoolExecutor(1)

    private var tryEnterIdleStateRunnableFuture: ScheduledFuture<*>? = null
    private var lastReceivedChips: RenderDirective? = null

    private val tryEnterIdleStateRunnable: Runnable = Runnable {
        executor.submit {
            if (currentState != DialogUXStateAggregatorInterface.DialogUXState.IDLE
                && !dialogModeEnabled
                && !asrState.isRecognizing()
                && (ttsState == TTSAgentInterface.State.FINISHED || ttsState == TTSAgentInterface.State.STOPPED) && !isTtsPreparing
            ) {
                Logger.d(TAG, "[tryEnterIdleStateRunnable]")
                setState(DialogUXStateAggregatorInterface.DialogUXState.IDLE)
            }
        }
    }

    private var displaySustainFuture: ScheduledFuture<*>? = null
    private val renderedDisplayTemplates = CopyOnWriteArraySet<String>()

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
            listener.onDialogUXStateChanged(currentState, dialogModeEnabled, getCurrentChips())
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
    }

    override fun onError(dialogRequestId: String) {
        isTtsPreparing = false

        executor.submit {
            tryEnterIdleState()
        }
    }

    override fun onStateChanged(state: ASRAgentInterface.State) {
        Logger.d(TAG, "[onStateChanged-ASR] state: $state")
        asrState = state

        executor.submit {
            when (state) {
                ASRAgentInterface.State.IDLE -> tryEnterIdleState()
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
            val changed = dialogModeEnabled != enabled
            dialogModeEnabled = enabled
            if(!enabled) {
                tryEnterIdleState()
            }

            if(changed) {
                notifyObserversOfState()
            }
        }
    }

    private fun setState(newState: DialogUXStateAggregatorInterface.DialogUXState) {
        if (newState == currentState) {
            return
        }

        tryEnterIdleStateRunnableFuture?.cancel(true)
        tryEnterIdleStateRunnableFuture = null

        currentState = newState
        notifyObserversOfState()

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

    private fun notifyObserversOfState() {
        getCurrentChips().let { chips ->
            listeners.forEach {
                it.onDialogUXStateChanged(currentState, dialogModeEnabled, chips)
            }
        }
    }

    private fun getCurrentChips(): RenderDirective.Payload? {
        val activeSessions = sessionManager.getActiveSessions()
        Logger.d(TAG, "[getCurrentChips] activeSessions: $activeSessions, lastReceivedChips: $lastReceivedChips")
        var keyForRecentSession: String? = null

        // TODO : implementation dependent - getActiveSessions ordered by set.
        activeSessions.forEach {
            keyForRecentSession = it.key
        }

        return if(keyForRecentSession == lastReceivedChips?.header?.dialogRequestId) {
            lastReceivedChips?.payload
        } else {
            null
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
    private fun tryEnterIdleState() {
        Logger.d(TAG, "[tryEnterIdleState] $dialogModeEnabled")
        tryEnterIdleStateRunnableFuture?.cancel(true)
        tryEnterIdleStateRunnableFuture = multiturnSpeakingToListeningScheduler.schedule(
            tryEnterIdleStateRunnable,
            transitionDelayForIdleState,
            TimeUnit.MILLISECONDS
        )
    }
}