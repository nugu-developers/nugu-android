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
package com.skt.nugu.sdk.core.capabilityagents.asr

import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioFormat
import com.skt.nugu.sdk.core.interfaces.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.network.event.AsrNotifyResultPayload
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider

abstract class AbstractSpeechProcessor(
    override val defaultAudioProvider: AudioProvider?,
    val defaultTimeoutMillis: Long
) : SpeechProcessorInterface
    , AudioEndPointDetector.OnStateChangedListener
    , SpeechToTextConverterInterface.OnStateChangedListener {

    companion object {
        private const val TAG = "AbstractSpeechProcessor"
    }

    override var enablePartialResult: Boolean = true
        set(value) {
            if (field == value) {
                return
            }

            field = value
            speechToTextConverter.enablePartialResult = value
        }

    override var includeWakeupBoundary: Boolean = false
        set(value) {
            if (field == value) {
                return
            }

            field = value
            speechToTextConverter.enableSpeakerRecognition = value
        }

    protected var audioInputStream: SharedDataStream? = null
    protected var audioFormat: AudioFormat? = null
    protected var context: String? = null
    protected var wakeupBoundary: WakeupBoundary? = null
    protected var payload: ExpectSpeechPayload? = null

    abstract val speechToTextConverter: SpeechToTextConverterImpl
    abstract val endPointDetector: AudioEndPointDetector
    var inputProcessor: InputProcessor? = null

    private var state = SpeechProcessorInterface.State.STOP

    private val listeners = HashSet<SpeechProcessorInterface.OnStateChangeListener>()

    final override fun startProcessor(
        audioInputStream: SharedDataStream?,
        audioFormat: AudioFormat?,
        context: String,
        wakeupBoundary: WakeupBoundary?,
        payload: ExpectSpeechPayload?
    ) {
        this.context = context
        this.audioInputStream = audioInputStream
        this.audioFormat = audioFormat
        this.wakeupBoundary = wakeupBoundary
        this.payload = payload

        Logger.d(
            TAG,
            "[startProcessor] wakeupBoundary:$wakeupBoundary, currentInputPosition: ${audioInputStream?.getPosition()}, includeWakeupBoundary: $includeWakeupBoundary"
        )

        if (audioInputStream == null) {
            Logger.e(TAG, "[startProcessor] audioInputProcessor is null")
            return
        }

        if (audioFormat == null) {
            Logger.e(TAG, "[startProcessor] audioFormat is null")
            return
        }

        if(!endPointDetector.startDetector(
            audioInputStream.createReader(),
            audioFormat,
            ((payload?.timeoutInMilliseconds ?: defaultTimeoutMillis) / 1000L).toInt()
        )) {
            Logger.e(TAG, "[startProcessor] failed to start epd.")
        }
    }

    final override fun stopProcessor() {
        endPointDetector.stopDetector()
        speechToTextConverter.stopSpeechToTextConverter()
    }

    final override fun addListener(listener: SpeechProcessorInterface.OnStateChangeListener) {
        listeners.add(listener)
    }

    final override fun removeListener(listener: SpeechProcessorInterface.OnStateChangeListener) {
        listeners.remove(listener)
    }

    fun setState(state: SpeechProcessorInterface.State) {
        if (this.state == state) {
            return
        }

        if (!this.state.isActive() && !state.isActive()) {
            return
        }

        this.state = state

        notifyObservers(state)
    }

    private fun notifyObservers(state: SpeechProcessorInterface.State) {
        listeners.forEach {
            it.onStateChanged(state)
        }
    }

    final override fun notifyResult(state: String, result: String?) {
        val enumState = AsrNotifyResultPayload.State.values().find { it.name == state }
        val payload = AsrNotifyResultPayload(enumState!!, result)

        when (payload.state) {
            AsrNotifyResultPayload.State.PARTIAL -> {
                speechToTextConverter.notifyPartialResult(payload.result)
            }
            AsrNotifyResultPayload.State.COMPLETE -> {
                speechToTextConverter.notifyCompleteResult(payload.result)
            }
            AsrNotifyResultPayload.State.NONE -> {
                speechToTextConverter.notifyEmptyResult()
            }
            AsrNotifyResultPayload.State.SOS -> {
                notifyResultSOS()
            }
            AsrNotifyResultPayload.State.EOS -> {
                notifyResultEOS()
            }
            AsrNotifyResultPayload.State.RESET -> {
                // TODO : Impl when
            }
            AsrNotifyResultPayload.State.FA -> {
                // TODO : Impl
            }
            AsrNotifyResultPayload.State.ERROR -> {
                notifyError("Server Error")
            }
        }
    }

    override fun notifyError(description: String) {
        speechToTextConverter.notifyError(description)
    }

    abstract fun notifyResultSOS()
    abstract fun notifyResultEOS()

    final override fun onStateChanged(state: SpeechToTextConverterInterface.State) {
        if (state == SpeechToTextConverterInterface.State.INACTIVE) {
            speechToTextConverter.finishSpeechToTextConverter()
            setState(SpeechProcessorInterface.State.STOP)
        }
    }

    override fun onStateChanged(state: AudioEndPointDetector.State) {
        val speechProcessorState = when (state) {
            AudioEndPointDetector.State.EXPECTING_SPEECH -> SpeechProcessorInterface.State.EXPECTING_SPEECH
            AudioEndPointDetector.State.SPEECH_START -> SpeechProcessorInterface.State.SPEECH_START
            AudioEndPointDetector.State.SPEECH_END -> {
                speechToTextConverter.finishSpeechToTextConverter()
                speechToTextConverter.getDialogRequestId()?.let {
                    inputProcessor?.onSendEventFinished(it)
                }
                SpeechProcessorInterface.State.SPEECH_END
            }
            AudioEndPointDetector.State.TIMEOUT -> {
                speechToTextConverter.notifyError("Listening Timeout")
                speechToTextConverter.stopSpeechToTextConverter()
                SpeechProcessorInterface.State.TIMEOUT
            }
            AudioEndPointDetector.State.STOP,
            AudioEndPointDetector.State.ERROR -> {
                speechToTextConverter.stopSpeechToTextConverter()
                SpeechProcessorInterface.State.STOP
            }
        }

        setState(speechProcessorState)
    }

    override fun release() {
        // no-op
    }
}