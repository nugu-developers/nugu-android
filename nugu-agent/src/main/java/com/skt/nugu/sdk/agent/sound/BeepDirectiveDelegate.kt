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

package com.skt.nugu.sdk.agent.sound

import com.skt.nugu.sdk.agent.mediaplayer.UriSourcePlayablePlayer

interface BeepDirectiveDelegate {
    /**
     * Get beep from SoundProvider and play a beep using player.
     * Get the beep name at [BeepDirective.Payload.beepName]
     * @param player the player for beep
     * @param soundProvider  the provider for beep
     * @param directive the directive
     * @return true: play beep, false: otherwise
     */
    fun beep(player: UriSourcePlayablePlayer, soundProvider: SoundProvider, directive: BeepDirective): Boolean
}