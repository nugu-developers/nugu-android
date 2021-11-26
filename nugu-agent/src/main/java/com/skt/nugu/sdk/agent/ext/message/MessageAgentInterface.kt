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

package com.skt.nugu.sdk.agent.ext.message

import com.skt.nugu.sdk.agent.ext.message.payload.ReadMessageDirective
import com.skt.nugu.sdk.agent.mediaplayer.ErrorType

interface MessageAgentInterface {
    interface OnPlaybackListener {
        fun onPlaybackStarted(directive: ReadMessageDirective)
        fun onPlaybackFinished(directive: ReadMessageDirective)
        fun onPlaybackStopped(directive: ReadMessageDirective)
        fun onPlaybackError(directive: ReadMessageDirective, type: ErrorType, error: String)
    }

    fun addOnPlaybackListener(listener: OnPlaybackListener)
    fun removeOnPlaybackListener(listener: OnPlaybackListener)
}