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
package com.skt.nugu.sdk.platform.android.ux.template.webview

import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.common.Direction

object JavaScriptHelper {
    const val FUNC_ON_CONTROL_RESULT = "onControlResult"
    const val PARAM_COUNT_ON_CONTROL_RESULT = 2
    const val PARAM_FOCUS = "focus"
    const val PARAM_SCROLL = "scroll"
    const val RESULT_FOCUS = PARAM_FOCUS
    const val RESULT_SCROLL = PARAM_SCROLL
    const val RESULT_SUCCEEDED = "succeeded"
    const val RESULT_FAILED = "failed"

    private const val FUNC_PLAYER_ON_PLAY_STOPPED = "javascript:nativePlayerListener.onPlayStopped();"
    private const val FUNC_PLAYER_ON_PLAY_STARTED = "javascript:nativePlayerListener.onPlayStarted();"
    private const val FUNC_PLAYER_ON_PLAY_PAUSED = "javascript:nativePlayerListener.onPlayPaused();"
    private const val FUNC_PLAYER_ON_PLAY_PAUSED_SHOW_CONTROLLER = "javascript:nativePlayerListener.onPlayPaused(${true});"

    private const val FUNC_PLAYER_ON_PLAY_END = "javascript:nativePlayerListener.onPlayerEnd();"

    private const val FUNC_PLAYER_SET_CURRENT_TIME = "javascript:nativePlayerListener.setCurrentTime(%s, 'ms');"
    private const val FUNC_PLAYER_SET_PROGRESS = "javascript:nativePlayerListener.setProgress(%.2f);"
    private const val FUNC_PLAYER_SET_END_TIME = "javascript:nativePlayerListener.setEndTime(%s, 'ms');"

    private const val FUNC_PLAYER_SHOW_LYRICS = "javascript:nativePlayerListener.onShowLyric();"
    private const val FUNC_PLAYER_HIDE_LYRICS = "javascript:nativePlayerListener.onCloseLyric();"
    private const val FUNC_PLAYER_UPDATE_METADATA = "javascript:nativePlayerListener.updateMetadata('%s');"

    private const val FUNC_DISPLAY_ON_DUX_RECEIVED = "javascript:nativeEventListener.onDuxReceived('%s','','','%s');"
    private const val FUNC_DISPLAY_CONTROL = "javascript:nativeEventListener.control('%s','%s');"
    private const val FUNC_DISPLAY_UPDATE = "javascript:nativeEventListener.update('%s');"
    private const val FUNC_DISPLAY_CLIENT_INFO_CHANGED = "javascript:nativeEventListener.onClientInfoChanged('%s');"

    fun onPlayStopped(): String {
        return FUNC_PLAYER_ON_PLAY_STOPPED
    }

    fun onPlayStarted(): String {
        return FUNC_PLAYER_ON_PLAY_STARTED
    }

    fun onPlayPaused(showController: Boolean): String {
        if (showController) {
            return FUNC_PLAYER_ON_PLAY_PAUSED_SHOW_CONTROLLER
        }
        return FUNC_PLAYER_ON_PLAY_PAUSED
    }

    fun onPlayEnd(): String {
        return FUNC_PLAYER_ON_PLAY_END
    }

    fun setCurrentTime(offset: Long): String {
        return FUNC_PLAYER_SET_CURRENT_TIME.format(offset)
    }

    fun setProgress(progress: Float): String {
        return FUNC_PLAYER_SET_PROGRESS.format(progress)
    }

    fun setEndTime(duration: Long): String {
        return FUNC_PLAYER_SET_END_TIME.format(duration)
    }

    fun showLyrics(): String {
        return FUNC_PLAYER_SHOW_LYRICS
    }

    fun hideLyrics(): String {
        return FUNC_PLAYER_HIDE_LYRICS
    }

    fun updatePlayerMetadata(metadata: String): String {
        return FUNC_PLAYER_UPDATE_METADATA.format(metadata)
    }

    fun controlFocus(direction: Direction): String {
        return FUNC_DISPLAY_CONTROL.format(PARAM_FOCUS, direction.name)
    }

    fun controlScroll(direction: Direction): String {
        return FUNC_DISPLAY_CONTROL.format(PARAM_SCROLL, direction.name)
    }

    fun updateClientInfo(clientInfoString: String): String {
        return FUNC_DISPLAY_CLIENT_INFO_CHANGED.format(clientInfoString)
    }

    fun onDuxReceived(dialogRequestId: String, template: String): String {
        return FUNC_DISPLAY_ON_DUX_RECEIVED.format(template, dialogRequestId)
    }

    fun updateDisplay(metadata: String): String {
        return FUNC_DISPLAY_UPDATE.format(metadata)
    }

    fun parseBoolean(param: String): Boolean {
        return param.equals("true", true)
    }

    fun parseRepeatMode(param: String): AudioPlayerAgentInterface.RepeatMode {
        return when {
            param.equals(AudioPlayerAgentInterface.RepeatMode.ALL.name, true) -> AudioPlayerAgentInterface.RepeatMode.ALL
            param.equals(AudioPlayerAgentInterface.RepeatMode.ONE.name, true) -> AudioPlayerAgentInterface.RepeatMode.ONE
            else -> AudioPlayerAgentInterface.RepeatMode.NONE
        }
    }
}