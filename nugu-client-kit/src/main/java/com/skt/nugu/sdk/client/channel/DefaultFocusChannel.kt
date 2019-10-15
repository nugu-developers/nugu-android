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
        // If you add other channel, only allow to use positive number for priority
        const val INTRUSION_CHANNEL_NAME = "Intrusion"
        const val INTRUSION_CHANNEL_PRIORITY = 50
        const val DIALOG_CHANNEL_NAME = "Dialog"
        const val DIALOG_CHANNEL_PRIORITY = 100
        const val COMMUNICATIONS_CHANNEL_NAME = "Communications"
        const val COMMUNICATIONS_CHANNEL_PRIORITY = 150
        const val ALERTS_CHANNEL_NAME = "Alerts"
        const val ALERTS_CHANNEL_PRIORITY = 200
        const val CONTENT_CHANNEL_NAME = "Content"
        const val CONTENT_CHANNEL_PRIORITY = 300

        fun getDefaultAudioChannels(): List<FocusManagerInterface.ChannelConfiguration> {
            return listOf(
                FocusManagerInterface.ChannelConfiguration(
                    INTRUSION_CHANNEL_NAME,
                    INTRUSION_CHANNEL_PRIORITY, true),
                FocusManagerInterface.ChannelConfiguration(
                    DIALOG_CHANNEL_NAME,
                    DIALOG_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    COMMUNICATIONS_CHANNEL_NAME,
                    COMMUNICATIONS_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    ALERTS_CHANNEL_NAME,
                    ALERTS_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    CONTENT_CHANNEL_NAME,
                    CONTENT_CHANNEL_PRIORITY
                )
            )
        }

        fun getDefaultVisualChannels(): List<FocusManagerInterface.ChannelConfiguration> {
            return listOf(
                FocusManagerInterface.ChannelConfiguration(
                    INTRUSION_CHANNEL_NAME,
                    INTRUSION_CHANNEL_PRIORITY, true),
                FocusManagerInterface.ChannelConfiguration(
                    DIALOG_CHANNEL_NAME,
                    DIALOG_CHANNEL_PRIORITY, true),
                FocusManagerInterface.ChannelConfiguration(
                    COMMUNICATIONS_CHANNEL_NAME,
                    COMMUNICATIONS_CHANNEL_PRIORITY, true),
                FocusManagerInterface.ChannelConfiguration(
                    ALERTS_CHANNEL_NAME,
                    ALERTS_CHANNEL_PRIORITY, true),
                FocusManagerInterface.ChannelConfiguration(
                    CONTENT_CHANNEL_NAME,
                    CONTENT_CHANNEL_PRIORITY, true)
            )
        }
    }
}