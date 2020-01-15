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
package com.skt.nugu.sdk.agent.playback

/**
 * Used to send a playback control event to handler
 */
interface PlaybackRouter {
    /** Called by the client when a button is pressed(physical button or GUI)
     * Event will be delivered to the handler specified by [setHandler]
     * @param button
     */
    fun buttonPressed(button: PlaybackButton)

//    fun togglePressed(toggle: PlaybackToggle, action: Boolean)

    /**
     * Set the handler that receive [buttonPressed] event
     * @handler handler that receive events
     */
    fun setHandler(handler: PlaybackHandler?)
}