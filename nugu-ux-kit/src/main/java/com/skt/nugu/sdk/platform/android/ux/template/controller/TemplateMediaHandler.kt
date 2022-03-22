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

import androidx.annotation.VisibleForTesting
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface.State
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import java.util.*
import kotlin.concurrent.fixedRateTimer

/**
 * TemplateHandler focused on Media state observing and interaction with it
 */
open class TemplateMediaHandler(
    protected val nuguProvider: TemplateRenderer.NuguClientProvider,
    override val templateInfo: TemplateHandler.TemplateInfo,
) : TemplateHandler {

    companion object {
        private const val TAG = "TemplateMediaHandler"
    }

    protected var audioDurationMs = 0L
    protected var mediaProgressJob: Timer? = null
    protected var mediaListener: TemplateHandler.ClientListener? = null
        private set

    @VisibleForTesting
    internal val mediaDurationListener = object : AudioPlayerAgentInterface.OnDurationListener {
        override fun onRetrieved(duration: Long?, context: AudioPlayerAgentInterface.Context) {
            Logger.d(TAG, "onDurationRetrieved $duration")
            if (context.templateId == templateInfo.templateId) {
                audioDurationMs = duration ?: 0L
                mediaListener?.onMediaDurationRetrieved(audioDurationMs)
                mediaListener?.onMediaProgressChanged(getMediaProgressPercentage(), getMediaCurrentTimeMs())
            }
        }
    }

    @VisibleForTesting
    internal val mediaStateListener = object : AudioPlayerAgentInterface.Listener {
        override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
            Logger.d(TAG, "mediaStateListener.onStateChanged $activity, $context")
            mediaListener?.onMediaStateChanged(activity, getMediaCurrentTimeMs(), getMediaProgressPercentage(), false)

            if (activity == State.PLAYING) startMediaProgressSending()
            else stopMediaProgressSending()
        }
    }

    @VisibleForTesting
    internal val directiveHandlingListener = object : DirectiveSequencerInterface.OnDirectiveHandlingListener {
        private fun Directive.isAudioPlayerPauseDirective(): Boolean {
            return getNamespace() == DefaultAudioPlayerAgent.NAMESPACE && getName() == DefaultAudioPlayerAgent.NAME_PAUSE
        }

        override fun onCompleted(directive: Directive) {
            if (directive.isAudioPlayerPauseDirective()) {
                mediaListener?.onMediaStateChanged(State.PAUSED, getMediaCurrentTimeMs(), getMediaProgressPercentage(), true)
            }
        }

        override fun onRequested(directive: Directive) = Unit
        override fun onCanceled(directive: Directive) = Unit
        override fun onFailed(directive: Directive, description: String) = Unit
    }

    override fun setClientListener(listener: TemplateHandler.ClientListener?) {
        mediaListener = listener
        observeMediaState()
    }

    override fun onPlayerCommand(command: PlayerCommand, param: String) {
        Logger.i(TAG, "onPlayerCommand() $command, $param ")
        getNuguClient().run {
            when (command) {
                PlayerCommand.PLAY -> getPlaybackRouter().buttonPressed(PlaybackButton.PLAY)
                PlayerCommand.STOP -> getPlaybackRouter().buttonPressed(PlaybackButton.STOP)
                PlayerCommand.PAUSE -> getPlaybackRouter().buttonPressed(PlaybackButton.PAUSE)
                PlayerCommand.PREV -> getPlaybackRouter().buttonPressed(PlaybackButton.PREVIOUS)
                PlayerCommand.NEXT -> getPlaybackRouter().buttonPressed(PlaybackButton.NEXT)
                PlayerCommand.SHUFFLE -> audioPlayerAgent?.requestShuffleCommand(param.equals("true", true))
                PlayerCommand.REPEAT -> {
                    runCatching {
                        AudioPlayerAgentInterface.RepeatMode.valueOf(param)
                    }.getOrNull()?.let { repeatMode ->
                        audioPlayerAgent?.requestRepeatCommand(repeatMode)
                    }
                }
                PlayerCommand.FAVORITE -> audioPlayerAgent?.requestFavoriteCommand(param.equals("true", true))
                else -> Unit
            }
        }
    }

    @VisibleForTesting
    internal fun startMediaProgressSending() {
        Logger.d(TAG, "startProgressMessageSending")
        mediaProgressJob?.cancel()

        mediaProgressJob = fixedRateTimer(period = 100, initialDelay = 0, action = {
            mediaListener?.onMediaProgressChanged(getMediaProgressPercentage(), getMediaCurrentTimeMs())
        })

    }

    @VisibleForTesting
    internal fun stopMediaProgressSending() {
        Logger.d(TAG, "stopProgressMessageSending")
        mediaProgressJob?.cancel()
        mediaProgressJob = null
    }

    protected fun getMediaCurrentTimeMs(): Long {
        return getNuguClient().audioPlayerAgent?.getOffset(com.skt.nugu.sdk.agent.util.TimeUnit.MILLISECONDS) ?: 0L
    }

    protected fun getMediaProgressPercentage(): Float {
        val offset = getMediaCurrentTimeMs().toFloat()
        val duration = audioDurationMs.coerceAtLeast(1L)
        return (offset / duration * 100f).coerceIn(0f, 100f)
    }

    protected fun observeMediaState() {
        Logger.i(TAG, "observeMediaState")
        getNuguClient().audioPlayerAgent?.run {
            removeListener(mediaStateListener)
            addListener(mediaStateListener, true)
            removeOnDurationListener(mediaDurationListener)
            addOnDurationListener(mediaDurationListener, true)
        }
        getNuguClient().addOnDirectiveHandlingListener(directiveHandlingListener)
    }

    override fun clear() {
        Logger.i(TAG, "clear")
        getNuguClient().audioPlayerAgent?.run {
            Logger.i(TAG, "mediaStateListener removed successfully")
            removeListener(mediaStateListener)
            removeOnDurationListener(mediaDurationListener)
        }
        getNuguClient().removeOnDirectiveHandlingListener(directiveHandlingListener)

        stopMediaProgressSending()
        mediaListener = null
    }

    override fun getNuguClient(): NuguAndroidClient = nuguProvider.getNuguClient()
}