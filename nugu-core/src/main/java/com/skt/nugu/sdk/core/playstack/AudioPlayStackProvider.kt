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
package com.skt.nugu.sdk.core.playstack

import com.skt.nugu.sdk.core.context.PlayStackProvider
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class AudioPlayStackProvider(
    audioFocusManager: FocusManagerInterface,
    audioPlayerAgent: AudioPlayerAgentInterface?,
    ttsPlayerAgent: TTSAgentInterface?,
    private val timeoutForContextHold: Long = 7000L
) : PlayStackProvider
    , FocusManagerInterface.OnFocusChangedListener
    , AudioPlayerAgentInterface.Listener
    , TTSAgentInterface.Listener {
    companion object {
        private const val TAG = "AudioPlayStackProvider"
    }

    init {
        audioFocusManager.addListener(this)
        audioPlayerAgent?.addListener(this)
        ttsPlayerAgent?.addListener(this)
    }

    private val lock = ReentrantLock()
    private val activeChannelAndPlayServiceIdMap = HashMap<FocusManagerInterface.ChannelConfiguration, String>()

    private val endTimestampForChannels = HashMap<FocusManagerInterface.ChannelConfiguration, Long>()
    private var lastAudioPlayerActivity = AudioPlayerAgentInterface.State.IDLE
    private var lastTTSState = TTSAgentInterface.State.IDLE


    override fun getPlayStack(): List<PlayStackProvider.PlayStackContext> {
        lock.withLock {
            trimTimeoutChannelLocked()

            val playStack = ArrayList<PlayStackProvider.PlayStackContext>()
            activeChannelAndPlayServiceIdMap.forEach {
                playStack.add(PlayStackProvider.PlayStackContext(it.value, it.key.priority))
            }

            Logger.d(TAG, "[getPlayStack] $playStack")

            return playStack
        }
    }

    private fun trimTimeoutChannelLocked() {
        val timeoutChannels = HashSet<FocusManagerInterface.ChannelConfiguration>()
        val currentTimestamp = System.currentTimeMillis()

        endTimestampForChannels.forEach {
            if(currentTimestamp - it.value > timeoutForContextHold) {
                timeoutChannels.add(it.key)
            }
        }

        timeoutChannels.forEach {
            activeChannelAndPlayServiceIdMap.remove(it)
            endTimestampForChannels.remove(it)
        }

        Logger.d(TAG, "[trimTimeoutChannelLocked] trimmed: $timeoutChannels")
    }

    override fun onFocusChanged(
        channelConfiguration: FocusManagerInterface.ChannelConfiguration,
        newFocus: FocusState,
        interfaceName: String,
        playServiceId: String?
    ) {
        Logger.d(
            TAG,
            "[onFocusChanged] $channelConfiguration, $newFocus, $interfaceName, $playServiceId"
        )
        if (playServiceId.isNullOrBlank()) {
            return
        }

        lock.withLock {
            when (newFocus) {
                FocusState.FOREGROUND -> {
                    clearLowerPriorityChannelsThanLocked(channelConfiguration)
                    endTimestampForChannels.remove(channelConfiguration)
                    activeChannelAndPlayServiceIdMap[channelConfiguration] = playServiceId
                }
                FocusState.NONE -> {
                    var holdTimeout = !existHigherPriorityChannelLocked(channelConfiguration)

                    if(holdTimeout) {
                        if (interfaceName == AbstractASRAgent.NAMESPACE && lastAudioPlayerActivity == AudioPlayerAgentInterface.State.STOPPED) {
                            holdTimeout = false
                        }
                        if (interfaceName == AbstractTTSAgent.NAMESPACE && lastTTSState == TTSAgentInterface.State.STOPPED) {
                            holdTimeout = false
                        }
                    }

                    Logger.d(TAG, "[onFocusChanged] holdTimeout: $holdTimeout")
                    if(holdTimeout) {
                        endTimestampForChannels[channelConfiguration] = System.currentTimeMillis()
                    } else {
                        clearChannelLocked(channelConfiguration)
                    }
                }
                else -> {
                    // nothing
                }
            }
        }
    }

    private fun clearLowerPriorityChannelsThanLocked(lowerThanChannel: FocusManagerInterface.ChannelConfiguration) {
        val lowerPriorityChannels = HashSet<FocusManagerInterface.ChannelConfiguration>()

        activeChannelAndPlayServiceIdMap.forEach {
            if(it.key.priority < lowerThanChannel.priority) {
                lowerPriorityChannels.add(it.key)
            }
        }

        Logger.d(TAG, "[clearLowerPriorityChannelsThanLocked] lowerThanChannel: $lowerThanChannel, lowerPriorityChannels: $lowerPriorityChannels")

        lowerPriorityChannels.forEach {
            clearChannelLocked(it)
        }
    }


    private fun clearChannelLocked(channelConfiguration: FocusManagerInterface.ChannelConfiguration) {
        endTimestampForChannels.remove(channelConfiguration)
        activeChannelAndPlayServiceIdMap.remove(channelConfiguration)
    }
    override fun onStateChanged(
        activity: AudioPlayerAgentInterface.State,
        context: AudioPlayerAgentInterface.Context
    ) {
        Logger.d(TAG, "[onStateChanged-AudioPlayer] $activity, $context")
        lastAudioPlayerActivity = activity
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        Logger.d(TAG, "[onStateChanged-TTS] $state")
        lastTTSState = state
    }

    override fun onReceiveTTSText(text: String?, dialogRequestId: String) {
        // nothing to do
    }

    private fun existHigherPriorityChannelLocked(channelConfiguration: FocusManagerInterface.ChannelConfiguration): Boolean {
        activeChannelAndPlayServiceIdMap.forEach {
            if(!it.key.volatile) {
                if(it.key.priority > channelConfiguration.priority) {
                    return true
                }
            }
        }

        return false
    }
}