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
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.ChipsAgentInterface
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration
import java.util.concurrent.*

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
    , DirectiveSequencerInterface.OnDirectiveHandlingListener
{
    companion object {
        private const val TAG = "DialogUXStateAggregator"
    }

    data class NotifyParams(
        val state: DialogUXStateAggregatorInterface.DialogUXState,
        val dialogMode: Boolean,
        val chips: RenderDirective.Payload?,
        val sessionActivated: Boolean
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners: MutableSet<DialogUXStateAggregatorInterface.Listener> = HashSet()
    private var currentState: DialogUXStateAggregatorInterface.DialogUXState =
        DialogUXStateAggregatorInterface.DialogUXState.IDLE
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
            Logger.d(TAG, "[tryEnterIdleStateRunnable] state: $currentState, dialogModeEnabled: $dialogModeEnabled, asrState: $asrState, ttsState: $ttsState, isTtsPreparing: ${handlingTTSSpeakDirective.isNotEmpty()}")
            if (currentState != DialogUXStateAggregatorInterface.DialogUXState.IDLE
                && !dialogModeEnabled
                && !asrState.isRecognizing()
                && (ttsState == TTSAgentInterface.State.FINISHED || ttsState == TTSAgentInterface.State.STOPPED) && handlingTTSSpeakDirective.isEmpty()
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
            override fun onRendered(templateId: String, dialogRequestId: String) {
                renderedDisplayTemplates.add(templateId)
            }

            override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
                renderedDisplayTemplates.remove(templateId)
            }
        })
    }

    override fun addListener(listener: DialogUXStateAggregatorInterface.Listener) {
        executor.submit {
            listeners.add(listener)
            val notifyParams = lastNotifyParams ?: NotifyParams(currentState, dialogModeEnabled, null, sessionManager.getActiveSessions().isNotEmpty())

            listeners.forEach {
                it.onDialogUXStateChanged(
                    notifyParams.state,
                    notifyParams.dialogMode,
                    notifyParams.chips,
                    notifyParams.sessionActivated
                )
            }
        }
    }

    override fun removeListener(listener: DialogUXStateAggregatorInterface.Listener) {
        executor.submit {
            listeners.remove(listener)
        }
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        Logger.d(TAG, "[onStateChanged-TTS] State: $state")
        ttsState = state
        handlingTTSSpeakDirective.remove(dialogRequestId)

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
        // no-op
    }

    override fun onError(dialogRequestId: String) {
        // no-op
    }

    override fun onStateChanged(state: ASRAgentInterface.State) {
        Logger.d(TAG, "[onStateChanged-ASR] state: $state")
        val prevState = asrState

        asrState = state

        executor.submit {
            when (state) {
                is ASRAgentInterface.State.IDLE -> {
                    if(prevState == ASRAgentInterface.State.BUSY) {
                        tryEnterIdleState()
                    } else {
                        tryEnterIdleState(0)
                    }
                }
                is ASRAgentInterface.State.LISTENING -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING)
                }
                is ASRAgentInterface.State.RECOGNIZING -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.LISTENING)
                }
                is ASRAgentInterface.State.BUSY -> {
                    setState(DialogUXStateAggregatorInterface.DialogUXState.THINKING)
                }
                is ASRAgentInterface.State.EXPECTING_SPEECH -> {
                    // ignore
                }
            }
        }
    }

    override fun onMultiturnStateChanged(enabled: Boolean) {
        executor.submit {
            dialogModeEnabled = enabled

            // only notify if currentState is SPEAKING
            if(currentState == DialogUXStateAggregatorInterface.DialogUXState.SPEAKING) {
                notifyOnStateChangeIfSomethingChanged()
            }

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

        val chips: RenderDirective.Payload? = lastReceivedChips?.let { renderDirective ->
            // remove nudge chips if ASR not initiated by EXPECT_SPEECH Directive.
            val tempAsrState = asrState
            val targetChips = if(tempAsrState is ASRAgentInterface.State.LISTENING && tempAsrState.initiator != ASRAgentInterface.Initiator.EXPECT_SPEECH) {
                if(renderDirective.payload.chips.any{it.type == Chip.Type.NUDGE}) {
                    lastReceivedChips = null
                    null
                } else {
                    renderDirective.payload
                }
            } else {
                renderDirective.payload
            }

            if ((targetChips?.target == RenderDirective.Payload.Target.LISTEN && currentState == DialogUXStateAggregatorInterface.DialogUXState.EXPECTING)
                || (targetChips?.target == RenderDirective.Payload.Target.SPEAKING && currentState == DialogUXStateAggregatorInterface.DialogUXState.SPEAKING)) {
                lastReceivedChips = null
                targetChips
            } else {
                null
            }
        } ?: getCurrentDMChips(activeSessions)

        NotifyParams(currentState, dialogModeEnabled, chips, activeSessions.isNotEmpty()).let {notifyParams ->
            if(notifyParams != lastNotifyParams) {
                lastNotifyParams = notifyParams
                listeners.forEach {
                    it.onDialogUXStateChanged(notifyParams.state, notifyParams.dialogMode, notifyParams.chips, notifyParams.sessionActivated)
                }
            }
        }
    }

    private fun getCurrentDMChips(activeSessions: Map<String, SessionManagerInterface.Session>): RenderDirective.Payload? {
        val chips = lastReceivedChips
        Logger.d(TAG, "[getCurrentDMChips] activeSessions: $activeSessions, lastReceivedChips: $chips")

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

    override fun renderChips(directive: RenderDirective) {
        executor.submit {
            lastReceivedChips = directive
        }
    }

    override fun clearChips(directive: RenderDirective) {
        executor.submit {
            if(lastReceivedChips == directive) {
                lastReceivedChips = null
            }
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

    private val handlingTTSSpeakDirective = HashSet<String>()

    override fun onRequested(directive: Directive) {
        if(directive.getNamespace() == "TTS" && directive.getName() == "Speak") {
            handlingTTSSpeakDirective.add(directive.getDialogRequestId())
        }
    }

    override fun onCompleted(directive: Directive) {
        onTTSFinished(directive)
    }

    override fun onCanceled(directive: Directive) {
        onTTSFinished(directive)
    }

    override fun onFailed(directive: Directive, description: String) {
        onTTSFinished(directive)
    }

    private fun onTTSFinished(directive: Directive) {
        if(directive.getNamespace() == "TTS" && directive.getName() == "Speak") {
            handlingTTSSpeakDirective.remove(directive.getDialogRequestId())

            executor.submit {
                tryEnterIdleState()
            }
        }
    }
}