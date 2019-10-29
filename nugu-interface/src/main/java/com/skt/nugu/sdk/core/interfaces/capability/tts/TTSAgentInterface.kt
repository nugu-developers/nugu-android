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
package com.skt.nugu.sdk.core.interfaces.capability.tts

/**
 * Interface for TTS Capability Agent
 */
interface TTSAgentInterface {
    /**
     * State of TTSAgent
     */
    enum class State {
        /**
         * initial state
         */
        IDLE,

        /**
         * playing the speech
         */
        PLAYING,
        /**
         * the speech stopped
         */
        STOPPED,
        /**
         * the speech finished
         */
        FINISHED,
    }

    /**
     * Interface of a listener to be called when there has been an change of state
     */
    interface Listener {
        /** Called to notify an change of state
         * @param state current state
         * @param dialogRequestId the dialog request id
         */
        fun onStateChanged(state: State, dialogRequestId: String)

        /** Called to receive a text will be playing
         * @param text the text will be playing soon
         * @param dialogRequestId the dialog request id
         */
        fun onReceiveTTSText(text: String?, dialogRequestId: String)
    }

    /** Add a listener to be called when a state changed.
     * @param listener the listener that added
     */
    fun addListener(listener: Listener)

    /**
     * Remove a listener
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)

    fun stopTTS()

    interface OnPlaybackListener {
        fun onStart()
        fun onFinish()
        fun onError()
    }

    fun requestTTS(text: String, playServiceId:  String, listener: OnPlaybackListener?)
}