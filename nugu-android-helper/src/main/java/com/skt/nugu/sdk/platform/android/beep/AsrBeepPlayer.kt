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

package com.skt.nugu.sdk.platform.android.beep

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.mediaplayer.*
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class AsrBeepPlayer(
    private val focusManager: FocusManagerInterface,
    private val focusChannelName: String,
    asrAgent: ASRAgentInterface,
    private val beepResourceProvider: AsrBeepResourceProvider,
    private val mediaPlayer: UriSourcePlayablePlayer
): MediaPlayerControlInterface.PlaybackEventListener {
    companion object {
        private const val TAG = "AsrBeepPlayer"
    }

    private val asrOnResultListener: ASRAgentInterface.OnResultListener = object: ASRAgentInterface.OnResultListener {
        override fun onNoneResult(dialogRequestId: String) {
            beepResourceProvider.getOnNoneResultResource()?.let {
                tryPlayBeep(it)
            }
        }

        override fun onPartialResult(result: String, dialogRequestId: String) {
            // no-op
        }

        override fun onCompleteResult(result: String, dialogRequestId: String) {
            beepResourceProvider.getOnCompleteResultResource()?.let {
                tryPlayBeep(it)
            }
        }

        override fun onError(type: ASRAgentInterface.ErrorType, dialogRequestId: String) {
            when(type) {
                ASRAgentInterface.ErrorType.ERROR_NETWORK -> beepResourceProvider.getOnErrorNetworkResource()
                ASRAgentInterface.ErrorType.ERROR_AUDIO_INPUT -> beepResourceProvider.getOnErrorAudioInputResource()
                ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT -> beepResourceProvider.getOnErrorListeningTimeoutResource()
                ASRAgentInterface.ErrorType.ERROR_UNKNOWN -> beepResourceProvider.getOnErrorUnknownResource()
                ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT -> beepResourceProvider.getOnErrorResponseTimeoutResource()
            }?.let {
                tryPlayBeep(it)
            }
        }

        override fun onCancel(cause: ASRAgentInterface.CancelCause, dialogRequestId: String) {
            // no-op
        }
    }

    private val asrOnStateChangeListener: ASRAgentInterface.OnStateChangeListener = object : ASRAgentInterface.OnStateChangeListener {
        override fun onStateChanged(state: ASRAgentInterface.State) {
            if(state == ASRAgentInterface.State.LISTENING) {
                beepResourceProvider.getOnStartListeningResource()?.let {
                    tryPlayBeep(it)
                }
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val channelObserverSourceIdMap = ConcurrentHashMap<SourceId, ChannelObserver>()

    init {
        asrAgent.addOnResultListener(asrOnResultListener)
        asrAgent.addOnStateChangeListener(asrOnStateChangeListener)
        mediaPlayer.setPlaybackEventListener(this)
    }

    private fun tryPlayBeep(uri: URI) {
        Logger.d(TAG, "[tryPlayBeep] uri: $uri")
        val interfaceName = "${TAG}_${System.currentTimeMillis()}"
        focusManager.acquireChannel(focusChannelName, object: ChannelObserver {
            var isHandled = false
            override fun onFocusChanged(newFocus: FocusState) {
                executor.submit {
                    if(!isHandled) {
                        when (newFocus) {
                            FocusState.FOREGROUND,
                            FocusState.BACKGROUND -> {
                                isHandled = true
                                mediaPlayer.setSource(uri, null).also {
                                    if (!it.isError() && mediaPlayer.play(it)) {
                                        Logger.d(TAG, "[tryPlayBeep] sourceId: $it")
                                        channelObserverSourceIdMap[it] = this
                                    } else {
                                        focusManager.releaseChannel(focusChannelName, this)
                                    }
                                }
                            }
                            FocusState.NONE -> {
                                //no-op
                            }
                        }
                    }
                }
            }
        }, interfaceName)
    }

    override fun onPlaybackStarted(id: SourceId) {
        // no-op
    }

    override fun onPlaybackFinished(id: SourceId) {
        requestReleaseFocus(id)
    }

    override fun onPlaybackError(id: SourceId, type: ErrorType, error: String) {
        requestReleaseFocus(id)
    }

    override fun onPlaybackPaused(id: SourceId) {
        // no-op
    }

    override fun onPlaybackResumed(id: SourceId) {
        // no-op
    }

    override fun onPlaybackStopped(id: SourceId) {
        requestReleaseFocus(id)
    }

    private fun requestReleaseFocus(id: SourceId) {
        channelObserverSourceIdMap.remove(id)?.let {
            Logger.d(TAG, "[requestReleaseFocus] $id")
            focusManager.releaseChannel(focusChannelName, it)
        }
    }
}