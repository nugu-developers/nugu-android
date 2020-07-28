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
        // Also, each priority must be a multiple of 2.
        const val INTERACTION_CHANNEL_NAME = "Interaction"
        const val INTERACTION_CHANNEL_ACQUIRE_PRIORITY = 50
        const val INTERACTION_CHANNEL_RELEASE_PRIORITY = 900
        const val DIALOG_CHANNEL_NAME = "Dialog"
        const val DIALOG_CHANNEL_PRIORITY = 100
        const val COMMUNICATIONS_CHANNEL_NAME = "Communications"
        const val COMMUNICATIONS_CHANNEL_PRIORITY = 150
        const val ALERTS_CHANNEL_NAME = "Alerts"
        const val ALERTS_CHANNEL_PRIORITY = 200
        const val CONTENT_CHANNEL_NAME = "Content"
        const val CONTENT_CHANNEL_PRIORITY = 400

        const val INTERNAL_DIALOG_CHANNEL_NAME = "InternalDialog"
        const val INTERNAL_DIALOG_CHANNEL_ACQUIRE_PRIORITY = 300
        const val INTERNAL_DIALOG_CHANNEL_RELEASE_PRIORITY = 500

        fun getDefaultAudioChannels(): List<FocusManagerInterface.ChannelConfiguration> {
            return listOf(
                FocusManagerInterface.ChannelConfiguration(
                    INTERACTION_CHANNEL_NAME,
                    INTERACTION_CHANNEL_ACQUIRE_PRIORITY, INTERACTION_CHANNEL_RELEASE_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    INTERNAL_DIALOG_CHANNEL_NAME,
                    INTERNAL_DIALOG_CHANNEL_ACQUIRE_PRIORITY, INTERNAL_DIALOG_CHANNEL_RELEASE_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    DIALOG_CHANNEL_NAME,
                    DIALOG_CHANNEL_PRIORITY,
                    DIALOG_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    COMMUNICATIONS_CHANNEL_NAME,
                    COMMUNICATIONS_CHANNEL_PRIORITY,
                    COMMUNICATIONS_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    ALERTS_CHANNEL_NAME,
                    ALERTS_CHANNEL_PRIORITY,
                    ALERTS_CHANNEL_PRIORITY
                ),
                FocusManagerInterface.ChannelConfiguration(
                    CONTENT_CHANNEL_NAME,
                    CONTENT_CHANNEL_PRIORITY,
                    CONTENT_CHANNEL_PRIORITY
                )
            )
        }
    }
}