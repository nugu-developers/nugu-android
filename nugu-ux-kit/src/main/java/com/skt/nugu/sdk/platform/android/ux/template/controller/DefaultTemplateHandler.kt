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
package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface.State
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import java.lang.ref.WeakReference
import java.util.*
import kotlin.concurrent.fixedRateTimer

open class DefaultTemplateHandler(androidClient: NuguAndroidClient, var templateInfo: TemplateInfo) : TemplateHandler {

    data class TemplateInfo(val templateId: String)

    companion object {
        private const val TAG = "DefaultTemplateHandler"
    }

    val androidClientRef = WeakReference(androidClient)

    private var audioDurationMs = 0L
    private var mediaProgressJob: Timer? = null
    var currentMediaState: AudioPlayerAgentInterface.State = State.IDLE
    private var clientListener: TemplateHandler.ClientListener? = null

    private val mediaDurationListener = object : AudioPlayerAgentInterface.OnDurationListener {
        override fun onRetrieved(duration: Long?, context: AudioPlayerAgentInterface.Context) {
            Logger.d(TAG, "onDurationRetrieved $duration")
            if (context.templateId == templateInfo.templateId) {
                audioDurationMs = duration ?: 0L
                clientListener?.onMediaDurationRetrieved(audioDurationMs)
            }
        }
    }

    private val mediaStateListener = object : AudioPlayerAgentInterface.Listener {
        override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
            Logger.d(TAG, "mediaStateListener.onStateChanged $activity, $context")

            currentMediaState = activity
            clientListener?.onMediaStateChanged(activity, getMediaCurrentTimeMs(), getMediaProgressPercentage())

            if (activity == State.PLAYING) startMediaProgressSending()
            else stopMediaProgressSending()
        }
    }

    val displayController = object : DisplayAggregatorInterface.Controller {
        override fun controlFocus(direction: Direction): Boolean {
            return (clientListener?.controlFocus(direction) ?: false).also {
                Logger.i(TAG, "controlFocus() $direction. return $it")
            }
        }

        override fun controlScroll(direction: Direction): Boolean {
            return (clientListener?.controlScroll(direction) ?: false).also {
                Logger.i(TAG, "controlScroll() $direction. return $it")
            }
        }

        override fun getFocusedItemToken(): String? {
            return clientListener?.getFocusedItemToken().also {
                Logger.i(TAG, "getFocusedItemToken(). return $it")
            }
        }

        override fun getVisibleTokenList(): List<String>? {
            return clientListener?.getVisibleTokenList().also {
                Logger.i(TAG, "getVisibleTokenList(). return $it")
            }
        }
    }

    override fun onElementSelected(tokenId: String) {
        Logger.i(TAG, "onElementSelected() $tokenId")
        androidClientRef.get()?.run { getDisplay()?.setElementSelected(templateInfo.templateId, tokenId) }
    }

    override fun onChipSelected(text: String) {
        Logger.i(TAG, "ohChipSelected() $text")
        androidClientRef.get()?.run { requestTextInput(text) }
    }

    override fun onCloseClicked() {
        Logger.w(TAG, "onClose() need to be implemented in application side")
    }

    override fun onNuguButtonSelected() {
        Logger.w(TAG, "onNuguButtonSelected() need to be implemented in application side")
    }

    override fun onContextChanged(context: String) {
        Logger.i(TAG, "onContextChanged() $context")
    }

    override fun onControlResult(action: String, result: String) {
        Logger.i(TAG, "onControlResult() action: $action, result : $result")
    }

    override fun showToast(text: String) {
        Logger.w(TAG, "onToastRequested() need to be implemented in application side")
    }

    override fun showActivity(className: String) {
        Logger.w(TAG, "onActivityRequested() need to be implemented in application side")
    }

    override fun playTTS(text: String) {
        Logger.i(TAG, "onTTSRequested() $text")
        androidClientRef.get()?.run { requestTTS(text) }
    }

    override fun setClientListener(listener: TemplateHandler.ClientListener) {
        clientListener = listener
    }

    override fun onPlayerCommand(command: String, param: String) {
        Logger.i(TAG, "onPlayerCommand() $command, $param ")
        androidClientRef.get()?.run {
            when (PlayerCommand.from(command)) {
                PlayerCommand.PLAY -> getPlaybackRouter().buttonPressed(PlaybackButton.PLAY)
                PlayerCommand.STOP -> getPlaybackRouter().buttonPressed(PlaybackButton.STOP)
                PlayerCommand.PAUSE -> getPlaybackRouter().buttonPressed(PlaybackButton.PAUSE)
                PlayerCommand.PREV -> getPlaybackRouter().buttonPressed(PlaybackButton.PREVIOUS)
                PlayerCommand.NEXT -> getPlaybackRouter().buttonPressed(PlaybackButton.NEXT)
                PlayerCommand.SHUFFLE -> audioPlayerAgent?.requestShuffleCommand(param.equals("true", true))
                PlayerCommand.REPEAT -> audioPlayerAgent?.requestRepeatCommand(AudioPlayerAgentInterface.RepeatMode.valueOf(param))
                PlayerCommand.FAVORITE -> audioPlayerAgent?.requestFavoriteCommand(param.equals("true", true))
                else -> Unit
            }
        }
    }

    private fun startMediaProgressSending() {
        Logger.d(TAG, "startProgressMessageSending")
        mediaProgressJob?.cancel()

        mediaProgressJob = fixedRateTimer(period = 1000, initialDelay = 1000, action = {
            clientListener?.onMediaProgressChanged(getMediaProgressPercentage(), getMediaCurrentTimeMs())
        })
    }

    private fun stopMediaProgressSending() {
        Logger.d(TAG, "stopProgressMessageSending")
        mediaProgressJob?.cancel()
    }

    private fun getMediaCurrentTimeMs(): Long {
        return androidClientRef.get()?.audioPlayerAgent?.getOffset()?.times(1000L) ?: 0L
    }

    private fun getMediaProgressPercentage(): Float {
        val offset = getMediaCurrentTimeMs().toFloat()
        val duration = audioDurationMs.coerceAtLeast(1L)
        return (offset / duration * 100f).coerceIn(0f, 100f)
    }

    fun observeMediaState() {
        Logger.i(TAG, "observeMediaState")
        androidClientRef.get()?.audioPlayerAgent?.addListener(mediaStateListener)
        androidClientRef.get()?.audioPlayerAgent?.addOnDurationListener(mediaDurationListener)
    }

    override fun clear() {
        Logger.i(TAG, "clear")
        androidClientRef.get()?.audioPlayerAgent?.run {
            Logger.i(TAG, "mediaStateListener removed successfully")
            removeListener(mediaStateListener)
            removeOnDurationListener(mediaDurationListener)
        }

        stopMediaProgressSending()
    }
}