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

import com.skt.nugu.sdk.agent.chips.RenderDirective

interface DialogUXStateAggregatorInterface {
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    /**
     * Enum class for Dialog UX state
     */
    enum class DialogUXState {
        /**
         * Neither SPEAKING nor RECOGNIZING (LISTENING, EXPECTING, THINKING)
         */
        IDLE,
        /**
         * Waiting to start speech for speech recognition
         */
        EXPECTING,
        /**
         * Listening for speech recognition
         */
        LISTENING,
        /**
         * Processing speech to get result(text)
         */
        THINKING,
        /**
         * TTS Speaking
         */
        SPEAKING
    }

    /**
     * Interface of a listener to be called when there has been an change of state
     * The DialogUX state means speech recognition(ASR) and speaking(TTS)
     */
    interface Listener {
        /**
         * Called to notify an change of state.
         *
         * About [dialogMode] @see [com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface.OnMultiturnListener]
         * @param newState : changed state.
         * @param dialogMode : true if dialog mode, false otherwise.
         * @param chips : the chips which is valid currently
         * @param sessionActivated : true if session activated, false otherwise.
         */
        fun onDialogUXStateChanged(newState: DialogUXState, dialogMode: Boolean, chips: RenderDirective.Payload?, sessionActivated: Boolean)
    }
}