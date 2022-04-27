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
package com.skt.nugu.sdk.platform.android.focus

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.client.NuguClientInterface
import com.skt.nugu.sdk.client.channel.DefaultFocusChannel
import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CountDownLatch

/**
 * This class manage an external audio player (such as music or video player apps) focus changes.
 *
 * To do so, this requires two audio focus manager. One is android audio focus manager(AAFM), and the other is NUGU audio focus manager(NAFM).
 */
class AndroidAudioFocusInteractor {
    companion object {
        private const val TAG = "AndAudioFocusInteractor"
    }

    /**
     * @param audioManager android audio manager to manage AAFM
     */
    class Factory(private val audioManager: AudioManager, private val useMainThreadOnRequestAudioManager: Boolean = true, private val alwaysRequestAudioFocus: Boolean = true): AudioFocusInteractorFactory {
        override fun create(
            focusManager: FocusManagerInterface,
            nuguClient: NuguClientInterface
        ): AudioFocusInteractor = Impl(
            audioManager,
            focusManager,
            nuguClient.audioPlayerAgent,
            if (useMainThreadOnRequestAudioManager) {
                Handler(Looper.getMainLooper())
            } else {
                Handler(HandlerThread("request_audio_manager_thread").apply { start() }.looper)
            },
            alwaysRequestAudioFocus
        ).apply {
            focusManager.setExternalFocusInteractor(this)
        }
    }

    /**
     * @param audioManager android audio manager to manage AAFM
     * @param audioFocusManager NUGU audio focus manager
     */
    internal class Impl(
        private val audioManager: AudioManager,
        private val audioFocusManager: FocusManagerInterface,
        private val audioPlayerAgent: AudioPlayerAgentInterface?,
        private val handler: Handler,
        private val alwaysRequestAudioFocus: Boolean
    ) : AudioFocusInteractor,
        FocusManagerInterface.ExternalFocusInteractor, ChannelObserver {

        private val channelName = DefaultFocusChannel.INTERACTION_CHANNEL_NAME
        private val releaseCallbackMap = HashMap<String, Runnable>()
        private var focusOwnerReferences = HashSet<String>()

        override fun acquire(channelName: String, requesterName: String): Boolean {
            if(requesterName == TAG) {
                // do not acquire/release AAFM when ExternalAudioPlayer focus changed.
                return true
            }

            Logger.d(TAG, "[acquire] requesterName: $requesterName")
            releaseCallbackMap.remove(requesterName)?.let {
                handler.removeCallbacks(it)
            }
            var result = false
            val latch = CountDownLatch(1)

            handler.post {
                // whenever acquired, request audio focus
                result = if(requestAudioFocus(audioFocusChangeListener)) {
                    audioFocusManager.releaseChannel(this.channelName, this)
                    focusOwnerReferences.add(requesterName)
                    true
                } else {
                    false
                }

                Logger.d(TAG, "[acquire] requestAudioFocus - result $result")
                latch.countDown()
            }

            latch.await()

            return result
        }

        override fun release(channelName: String, requesterName: String) {
            if(requesterName == TAG) {
                // do not acquire/release AAFM when ExternalAudioPlayer focus changed.
                return
            }

            Logger.d(TAG, "[release] focusOwner: $requesterName")
            releaseCallbackMap.remove(requesterName)?.let {
                handler.removeCallbacks(it)
            }

            Runnable {
                focusOwnerReferences.remove(requesterName)
                if (focusOwnerReferences.isEmpty()) {
                    abandonAudioFocus(audioFocusChangeListener)

                    // Since the change of the audio focus is no longer known and don't care about anymore, we need to release the focus here.
                    audioFocusManager.releaseChannel(channelName, this)
                    Logger.d(TAG, "[release] abandonAudioFocus")
                }
            }.apply {
                releaseCallbackMap[requesterName] = this
                handler.postDelayed(this, 500)
            }
        }

        override fun onFocusChanged(newFocus: FocusState) {
            // no-op
        }

        private var currentAudioFocus = AudioManager.AUDIOFOCUS_LOSS
        private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
            AudioManager.OnAudioFocusChangeListener {
                Logger.d(TAG, "[onAudioFocusChange] focusChange: $it")
                currentAudioFocus = it
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                        audioFocusManager.releaseChannel(channelName, this)
                    }
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if(it == AudioManager.AUDIOFOCUS_LOSS) {
                            audioPlayerAgent?.pause()
                        }
                        // 외부 App에 의해 Focus를 잃는 시점
                        // 현재 Nugu 시나리오를 Pause해야하는데 어떻게 해결?
                        // 현재 외부 App이 Foreground로 가도록 하고, 지금 것은 Background로 가야한다.
                        // Nugu의 특정 Interaction이 발생하는 순간 외부 App은 Focus를 Loss 해야한다.
                        audioFocusManager.acquireChannel(
                            channelName, this,
                            TAG
                        )
                    }
                }
            }

        private fun hasAudioFocus(): Boolean = when(currentAudioFocus) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> true
            else -> false
        }

        private fun requestAudioFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean {
            if(!alwaysRequestAudioFocus && hasAudioFocus()) {
                return true
            }

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setOnAudioFocusChangeListener(
                        listener
                    ).build()
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }

            Logger.d(TAG, "[requestAudioFocus] result: $result")
            return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                currentAudioFocus = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                true
            } else {
                false
            }
        }

        private fun abandonAudioFocus(listener: AudioManager.OnAudioFocusChangeListener) {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setOnAudioFocusChangeListener(
                        listener
                    ).build()
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(listener)
            }

            if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                currentAudioFocus = AudioManager.AUDIOFOCUS_LOSS
            }
            Logger.d(TAG, "[abandonAudioFocus] result: $result")
        }
    }
}