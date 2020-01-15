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
package com.skt.nugu.sdk.client

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.client.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import java.util.concurrent.Future
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface

/**
 * This is an utility interface that gathers simplified APIs in one place and provides them to clients.
 */
interface ClientHelperInterface {
    // Connection Manage
    /**
     * Connect to NUGU
     */
    fun connect()

    /**
     * Disconnect from NUGU
     */
    fun disconnect()

    // Connection Observer
    /**
     * Add listener to be notified when connection status changed for NUGU
     * @param listener the listener that will add
     */
    fun addConnectionListener(listener: ConnectionStatusListener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeConnectionListener(listener: ConnectionStatusListener)

    // Speaker(Volume) Control Manually
    /**
     * Return [SpeakerManagerInterface] which can control volume & mute
     * @return speaker manager
     */
    fun getSpeakerManager(): SpeakerManagerInterface

    /**
     * Add listener to be notified when speaker status changed
     * @param listener the listener that will add
     */
    fun addSpeakerListener(listener: SpeakerManagerObserver)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeSpeakerListener(listener: SpeakerManagerObserver)

    // Playback Control
    /**
     * Return [PlaybackRouter] which send event to control audio player
     * @return the playback router which send event to control audio player
     */
    fun getPlaybackRouter(): PlaybackRouter

    // AudioPlayer Observer
    /**
     * Add listener to be called when there has been an change of [AudioPlayerAgentInterface.Listener]
     * @param listener the listener that will add
     */
    fun addAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeAudioPlayerListener(listener: AudioPlayerAgentInterface.Listener)

    // DialogUX Observer
    /**
     * Add listener to be called when there has been an change of [DialogUXStateAggregatorInterface.DialogUXState]
     * @param listener the listener that will add
     */
    fun addDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeDialogUXStateListener(listener: DialogUXStateAggregatorInterface.Listener)

    // AIP
    /**
     * Add listener to be called when there has been changes of [ASRAgentInterface.State] or DialogMode
     * @param listener the listener that will add
     */
    fun addASRListener(listener: ASRAgentInterface.OnStateChangeListener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeASRListener(listener: ASRAgentInterface.OnStateChangeListener)

    /**
     * Start recognizing
     *
     * @param audioInputStream the audio input stream to read for recognizing
     * @param audioFormat the format of [audioInputStream]
     * @param wakewordStartPosition the start position of wakeword at [audioInputStream] if exist.
     * @param wakewordEndPosition the end position of wakeword at [audioInputStream] if exist.
     * @param wakewordDetectPosition the detect position of wakeword at [audioInputStream], null if not exist.
     * @return the boolean future, true: if recognize started, false: otherwise
     */
    fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakewordStartPosition: Long?,
        wakewordEndPosition: Long?,
        wakewordDetectPosition: Long?
    ): Future<Boolean>

    /**
     * Stop current recognizing.
     */
    fun stopRecognition()

    /**
     * add listener to be called when receive following events of STT
     * * receive none/partial/complete result
     * * receive error (@see [ASRAgentInterface.ErrorType]
     * * or canceled
     * @param listener the listener that will add
     */
    fun addASRResultListener(listener: ASRAgentInterface.OnResultListener)

    /**
     * remove event listener
     * @param listener the event listener that will removed
     */
    fun removeASRResultListener(listener: ASRAgentInterface.OnResultListener)

    // TEXT
    /**
     * Send request for NUGU with text input.
     * The client receive the same response(directive) as when they requested ASR.
     * @param text : the source text for
     */
    fun requestTextInput(text: String, listener: TextAgentInterface.RequestListener? = null)

    // TTS
    /**
     * Send request for TTS response given [text]
     * @param text the source text for TTS
     * @param playServiceId the playServiceId
     * @param listener the listener for TTS playback
     */
    fun requestTTS(text: String, playServiceId: String, listener: TTSAgentInterface.OnPlaybackListener? = null)

    /**
     * Stop current playing TTS
     */
    fun localStopTTS()

    /**
     * Cancel playing tts and other directives which has same dialog request id.
     */
    fun cancelTTSAndOthers()

    // Display
    /**
     * Return [DisplayAggregatorInterface] to interact with the client display
     * @return the display agent
     */
    fun getDisplay(): DisplayAggregatorInterface?

    /**
     * set renderer to interact with [DisplayAggregatorInterface]
     * @param renderer the renderer to be set
     */
    fun setDisplayRenderer(renderer: DisplayAggregatorInterface.Renderer?)

    // Destructor
    /**
     * Shutdown client
     */
    fun shutdown()

    fun setStateProvider(namespaceAndName: NamespaceAndName, stateProvider: ContextStateProvider?)

    // SystemAgent Listener
    /**
     * Add listener to be notified when receive an event from the System Capability agent
     * @param listener the listener that will add
     */
    fun addSystemAgentListener(listener : SystemAgentInterface.Listener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeSystemAgentListener(listener : SystemAgentInterface.Listener)
}