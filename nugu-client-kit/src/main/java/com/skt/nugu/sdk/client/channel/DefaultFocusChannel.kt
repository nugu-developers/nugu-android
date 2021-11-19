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
package com.skt.nugu.sdk.client.channel

import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface

class DefaultFocusChannel {
    companion object {
        private const val TOP_PRIORITY = 10
        private const val FIRST_PRIORITY = 100
        private const val SECOND_PRIORITY = 200
        private const val THIRD_PRIORITY = 300
        private const val FOURTH_PRIORITY = 400
        private const val FIFTH_PRIORITY = 500
        private const val NO_PRIORITY = 10000

        // If you add other channel, only allow to use positive number for priority
        // Also, each priority must be a multiple of 2.
        const val INTERACTION_CHANNEL_NAME = "Interaction"
        const val INTERACTION_CHANNEL_ACQUIRE_PRIORITY = TOP_PRIORITY
        const val INTERACTION_CHANNEL_RELEASE_PRIORITY = NO_PRIORITY

        @Deprecated("Use CALL_CHANNEL_NAME", ReplaceWith("CALL_CHANNEL_NAME"))
        const val COMMUNICATIONS_CHANNEL_NAME = "Call"
        const val CALL_CHANNEL_NAME = "Call"

        @Deprecated("Use TTS_CHANNEL_NAME", ReplaceWith("TTS_CHANNEL_NAME"))
        const val DIALOG_CHANNEL_NAME = "TTS"
        const val TTS_CHANNEL_NAME = "TTS"

        const val ALERTS_CHANNEL_NAME = "Alerts"

        @Deprecated("Use MEDIA_CHANNEL_NAME", ReplaceWith("MEDIA_CHANNEL_NAME"))
        const val CONTENT_CHANNEL_NAME = "Media"
        const val MEDIA_CHANNEL_NAME = "Media"

        @Deprecated("Use USER_ASR_CHANNEL_NAME", ReplaceWith("USER_ASR_CHANNEL_NAME"))
        const val USER_DIALOG_CHANNEL_NAME = "UserASR"
        const val USER_ASR_CHANNEL_NAME = "UserASR"

        @Deprecated("Use DM_ASR_CHANNEL_NAME", ReplaceWith("DM_ASR_CHANNEL_NAME"))
        const val INTERNAL_DIALOG_CHANNEL_NAME = "DMASR"
        const val DM_ASR_CHANNEL_NAME = "DMASR"

        const val SOUND_BEEP_CHANNEL_NAME = "Sound"

        const val ASR_BEEP_CHANNEL_NAME = "ASRBeep"
    }

    data class Builder(
        internal var callChannelAcquirePriority: Int = FIRST_PRIORITY,
        internal var callChannelReleasePriority: Int = FIRST_PRIORITY,

        internal var ttsChannelAcquirePriority: Int = SECOND_PRIORITY,
        internal var ttsChannelReleasePriority: Int = THIRD_PRIORITY,

        internal var alertsChannelAcquirePriority: Int = SECOND_PRIORITY,
        internal var alertsChannelReleasePriority: Int = THIRD_PRIORITY,

        internal var mediaChannelAcquirePriority: Int = THIRD_PRIORITY,
        internal var mediaChannelReleasePriority: Int = FIFTH_PRIORITY,

        internal var userASRChannelAcquirePriority: Int = FIRST_PRIORITY,
        internal var userASRChannelReleasePriority: Int = SECOND_PRIORITY,

        internal var dmASRChannelAcquirePriority: Int = FOURTH_PRIORITY,
        internal var dmASRChannelReleasePriority: Int = THIRD_PRIORITY,

        internal var soundBeepChannelAcquirePriority: Int = FIFTH_PRIORITY,
        internal var soundBeepChannelReleasePriority: Int = FIFTH_PRIORITY,

        internal var asrBeepChannelAcquirePriority: Int = FIFTH_PRIORITY,
        internal var asrBeepChannelReleasePriority: Int = FOURTH_PRIORITY,
    ) {
        fun build(): List<FocusManagerInterface.ChannelConfiguration> = listOf(
            FocusManagerInterface.ChannelConfiguration(
                INTERACTION_CHANNEL_NAME,
                INTERACTION_CHANNEL_ACQUIRE_PRIORITY,
                INTERACTION_CHANNEL_RELEASE_PRIORITY
            ),
            FocusManagerInterface.ChannelConfiguration(
                CALL_CHANNEL_NAME,
                callChannelAcquirePriority,
                callChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                TTS_CHANNEL_NAME,
                ttsChannelAcquirePriority,
                ttsChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                ALERTS_CHANNEL_NAME,
                alertsChannelAcquirePriority,
                alertsChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                MEDIA_CHANNEL_NAME,
                mediaChannelAcquirePriority,
                mediaChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                USER_ASR_CHANNEL_NAME,
                userASRChannelAcquirePriority,
                userASRChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                DM_ASR_CHANNEL_NAME,
                dmASRChannelAcquirePriority,
                dmASRChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                SOUND_BEEP_CHANNEL_NAME,
                soundBeepChannelAcquirePriority,
                soundBeepChannelReleasePriority
            ),
            FocusManagerInterface.ChannelConfiguration(
                ASR_BEEP_CHANNEL_NAME,
                asrBeepChannelAcquirePriority,
                asrBeepChannelReleasePriority
            )
        )
    }
}