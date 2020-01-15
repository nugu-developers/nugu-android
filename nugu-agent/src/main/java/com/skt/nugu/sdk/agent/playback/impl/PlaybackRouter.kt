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
package com.skt.nugu.sdk.agent.playback.impl

import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackHandler
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.core.utils.Logger

class PlaybackRouter : PlaybackRouter {
    companion object {
        private const val TAG = "PlaybackRouter"
    }

    private var handler: PlaybackHandler? = null

    override fun buttonPressed(button: PlaybackButton) {
        val currentHandler = handler
        if(currentHandler != null) {
            currentHandler.onButtonPressed(button)
        } else {
            Logger.w(TAG, "[buttonPressed] no handler to route")
        }
    }

//    override fun togglePressed(toggle: PlaybackToggle, action: Boolean) {
//        val currentHandler = handler
//        if(currentHandler != null) {
//            currentHandler.onTogglePressed(toggle, action)
//        } else {
//            Logger.w(TAG, "[togglePressed] no handler to route")
//        }
//    }

    override fun setHandler(handler: PlaybackHandler?) {
        this.handler = handler
    }
}