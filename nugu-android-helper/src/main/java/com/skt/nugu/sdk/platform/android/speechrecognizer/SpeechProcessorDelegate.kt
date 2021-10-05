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
package com.skt.nugu.sdk.platform.android.speechrecognizer

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioEndPointDetector
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.utils.Logger

/**
 * This class delegate SpeechProcessor to NUGU SDK to avoid conflict with NUGU SDK.
 */
class SpeechProcessorDelegate(
    private val asrAgent: ASRAgentInterface
) {

    companion object {
        // To remove error, shorten TAG
//        const val TAG = "SpeechProcessorDelegate"
        private const val TAG = "SpeechProcessorD"
    }

    interface Listener: AudioEndPointDetector.OnStateChangedListener {
        fun onIdle()
    }

    private var epdState = AudioEndPointDetector.State.STOP
    private val listeners = HashMap<Listener, ASRAgentInterface.OnStateChangeListener>()

    /**
     * Request starting epd to [ASRAgentInterface]
     */
    fun start(audioInputStream: SharedDataStream?, audioFormat: AudioFormat?, wakeupInfo: WakeupInfo?, epdParam: EndPointDetectorParam?, callback: ASRAgentInterface.StartRecognitionCallback?, initiator: ASRAgentInterface.Initiator) {
        Logger.d(TAG, "[startDetector]")
        asrAgent.startRecognition(audioInputStream, audioFormat, wakeupInfo, epdParam, callback, initiator)
    }

    /**
     * Request stop epd to [ASRAgentInterface]
     */
    fun stop(cancel: Boolean = true) {
        asrAgent.stopRecognition(cancel)
    }

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: Listener) {
        object : ASRAgentInterface.OnStateChangeListener {
            override fun onStateChanged(state: ASRAgentInterface.State) {
                if(!epdState.isActive() && !state.isRecognizing()) {
                    if(epdState == AudioEndPointDetector.State.SPEECH_END && state == ASRAgentInterface.State.IDLE) {
                        listener.onIdle()
                    } else {
                        Logger.d(
                            TAG,
                            "[AudioEndPointDetectorStateObserverInterface] invalid state change : $epdState / $state"
                        )
                    }
                    return
                }

                if(state != ASRAgentInterface.State.EXPECTING_SPEECH) {
                    epdState = when (state) {
                        is ASRAgentInterface.State.IDLE -> AudioEndPointDetector.State.STOP
//                    ASRAgentListener.State.TIMEOUT -> AudioEndPointDetector.State.TIMEOUT
                        is ASRAgentInterface.State.EXPECTING_SPEECH,
                        is ASRAgentInterface.State.LISTENING -> AudioEndPointDetector.State.EXPECTING_SPEECH
                        is ASRAgentInterface.State.RECOGNIZING -> AudioEndPointDetector.State.SPEECH_START
                        is ASRAgentInterface.State.BUSY -> AudioEndPointDetector.State.SPEECH_END
                    }

                    when(epdState) {
                        AudioEndPointDetector.State.EXPECTING_SPEECH -> listener.onExpectingSpeech()
                        AudioEndPointDetector.State.SPEECH_START -> listener.onSpeechStart(null)
                        AudioEndPointDetector.State.SPEECH_END -> listener.onSpeechEnd(null)
                        else -> listener.onStop()
                    }
                }
            }
        }.apply {
            listeners[listener] = this
            asrAgent.addOnStateChangeListener(this)
        }
    }

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)?.apply {
            asrAgent.removeOnStateChangeListener(this)
        }
    }

    fun getState() = epdState
}