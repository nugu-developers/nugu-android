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
package com.skt.nugu.sdk.core.dialog

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogUXStateAggregatorInterface
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DialogUXStateAggregator :
    DialogUXStateAggregatorInterface
    , ASRAgentInterface.OnStateChangeListener
    , TTSAgentInterface.Listener
    , ConnectionStatusListener
    , DialogSessionManagerInterface.OnSessionStateChangeListener{
    companion object {
        private const val TAG = "DialogUXStateAggregator"
        private const val LONG_TIME = 200L
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

    private val tryEnterIdleStateRunnable: Runnable = Runnable {
        executor.submit {
            if (currentState != DialogUXStateAggregatorInterface.DialogUXState.IDLE &&
                !asrState.isRecognizing() && (ttsState == TTSAgentInterface.State.FINISHED || ttsState == TTSAgentInterface.State.STOPPED) && !isTtsPreparing
            ) {
                Logger.d(TAG, "[tryEnterIdleStateRunnable]")
                setState(DialogUXStateAggregatorInterface.DialogUXState.IDLE)
            }
        }
    }

    override fun addListener(listener: DialogUXStateAggregatorInterface.Listener) {
        executor.submit {
            listeners.add(listener)
            listener.onDialogUXStateChanged(currentState, dialogModeEnabled)
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
            }
        }
    }

    override fun onSessionOpened(
        sessionId: String,
        property: String?,
        domainTypes: Array<String>?,
        playServiceId: String?
    ) {
        executor.submit {
            dialogModeEnabled = true
        }
    }

    override fun onSessionClosed(sessionId: String) {
        executor.submit {
            dialogModeEnabled = false
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
    }

    private fun notifyObserversOfState() {
        listeners.forEach {
            it.onDialogUXStateChanged(currentState, dialogModeEnabled)
        }
    }

    override fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason: ConnectionStatusListener.ChangedReason) {
        executor.submit {
            if (status != ConnectionStatusListener.Status.CONNECTED) {
                setState(DialogUXStateAggregatorInterface.DialogUXState.IDLE)
            }
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
            LONG_TIME,
            TimeUnit.MILLISECONDS
        )
    }
}