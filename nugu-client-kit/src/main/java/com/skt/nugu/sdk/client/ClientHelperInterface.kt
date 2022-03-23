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
import com.skt.nugu.sdk.agent.asr.EndPointDetectorParam
import com.skt.nugu.sdk.agent.asr.WakeupInfo
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerInterface
import com.skt.nugu.sdk.agent.speaker.SpeakerManagerObserver
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.context.ContextStateProvider
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

/**
 * This is an utility interface that gathers simplified APIs in one place and provides them to clients.
 */
interface ClientHelperInterface {
    // Connection Manage
    /**
     * Connect to NUGU
     */
    @Deprecated(message = "No longer used by ClientHelperInterface")
    fun connect()

    /**
     * Disconnect from NUGU
     */
    @Deprecated(message = "No longer used by ClientHelperInterface", replaceWith = ReplaceWith(expression = "this.networkManager.shutdown()"))
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
    fun getSpeakerManager(): SpeakerManagerInterface?

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
     * @param wakeupInfo the wakeupInfo of wakeword for [audioInputStream] if exist.
     * @param param the params for EPD
     * @param callback the callback for request
     * @param initiator the initiator causing recognition
     */
    fun startRecognition(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        wakeupInfo: WakeupInfo?,
        param: EndPointDetectorParam?,
        callback: ASRAgentInterface.StartRecognitionCallback?,
        initiator: ASRAgentInterface.Initiator
    )

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
     * @param playServiceId the playServiceId for request
     * @param token: the token for request
     * @param source: the source for request
     * @param referrerDialogRequestId the referrerDialogRequestId for request
     * @param includeDialogAttribute the flag to include or not dialog's attribute
     * @param listener the listener for request
     * @return the dialogRequestId for request
     */
    fun requestTextInput(
        text: String,
        playServiceId: String? = null,
        token: String? = null,
        source: String? = null,
        referrerDialogRequestId: String? = null,
        includeDialogAttribute: Boolean = true,
        listener: TextAgentInterface.RequestListener? = null
    ): String?

    // TTS
    /**
     * Send request for TTS response given [text]
     * @param text the source text for TTS
     * @param format the format of [text]
     * @param playServiceId the playServiceId which request tts, null if not specified.
     * @param listener the listener for TTS playback
     * @return the dialog request id for the request, null if failed.
     */
    fun requestTTS(text: String, format: TTSAgentInterface.Format = TTSAgentInterface.Format.TEXT, playServiceId: String? = null, listener: TTSAgentInterface.OnPlaybackListener? = null): String?

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

    /**
     * Add listener to be notified when receive directives from NUGU platform
     * @param listener the listener that will add
     */
    fun addReceiveDirectivesListener(listener: DirectiveGroupProcessorInterface.Listener)

    /**
     * Remove listener to be notified when receive directives from NUGU platform
     * @param listener the listener that will removed
     */
    fun removeReceiveDirectivesListener(listener: DirectiveGroupProcessorInterface.Listener)

    /**
     * Add listener to be notified when send message
     * @param listener the listener that will add
     */
    fun addOnSendMessageListener(listener: MessageSender.OnSendMessageListener)

    /**
     * Remove listener to be notified when send message
     * @param listener the listener that will removed
     */
    fun removeOnSendMessageListener(listener: MessageSender.OnSendMessageListener)

    /**
     * Add listener to be notified when occur event of directive handling.
     * @param listener the listener that will add
     */
    fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener)

    /**
     * Remove listener
     * @param listener the listener that will removed
     */
    fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener)
}