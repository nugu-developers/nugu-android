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
package com.skt.nugu.sdk.platform.android.ux.template.view.media

import androidx.annotation.Keep

@Keep
enum class PlayerCommand(val command: String) {
    UNKNOWN("unknown"),
    PLAY("play"),
    STOP("stop"),
    PAUSE("pause"),
    PREV("prev"),
    NEXT("next"),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    FAVORITE("favorite");

    companion object {
        fun from(command: String): PlayerCommand {
            return when (command) {
                PLAY.command -> PLAY
                STOP.command -> STOP
                PAUSE.command -> PAUSE
                PREV.command -> PREV
                NEXT.command -> NEXT
                SHUFFLE.command -> SHUFFLE
                REPEAT.command -> REPEAT
                FAVORITE.command -> FAVORITE
                else -> UNKNOWN
            }
        }
    }
}