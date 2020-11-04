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

package com.skt.nugu.sdk.agent.ext.mediaplayer

import com.skt.nugu.sdk.agent.ext.mediaplayer.handler.*

/**
 * MediaPlayer interface for MediaPlayerAgent
 */
interface MediaPlayer
    : PlayDirectiveHandler.Controller
    , StopDirectiveHandler.Controller
    , SearchDirectiveHandler.Controller
    , PreviousDirectiveHandler.Controller
    , NextDirectiveHandler.Controller
    , MoveDirectiveHandler.Controller
    , ResumeDirectiveHandler.Controller
    , PauseDirectiveHandler.Controller
    , RewindDirectiveHandler.Controller
    , ToggleDirectiveHandler.Controller
    , GetInfoDirectiveHandler.Controller
    , HandlePlaylistDirectiveHandler.Controller
    , HandleLyricsDirectiveHandler.Controller
{
    fun getContext(): Context
}