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
package com.skt.nugu.sampleapp.widget

import android.app.Activity
import android.support.design.widget.BottomSheetBehavior
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sampleapp.utils.SoundPoolCompat
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.ux.widget.NuguChipsView
import com.skt.nugu.sdk.platform.android.ux.widget.NuguVoiceChromeView

class ChromeWindowController(
    private val activity: Activity,
    private val callback: OnChromeWindowCallback
) : SpeechRecognizerAggregatorInterface.OnStateChangeListener
    , DialogUXStateAggregatorInterface.Listener
    , ASRAgentInterface.OnResultListener
    , TTSAgentInterface.Listener {
    companion object {
        private const val TAG = "ChromeWindowController"

        private const val DELAY_TIME_LONG = 1500L
        private const val DELAY_TIME_SHORT = 150L
    }

    interface OnChromeWindowCallback {
        fun onExpandStarted()
        fun onHiddenFinished()
    }

    private val finishRunnable = Runnable {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private val bottomSheet: FrameLayout by lazy {
        activity.findViewById<FrameLayout>(R.id.fl_bottom_sheet)
    }

    private val bottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    private val sttTextView:TextView by lazy {
        activity.findViewById<TextView>(R.id.tv_stt)
    }

    private val voiceChrome : NuguVoiceChromeView by lazy {
        activity.findViewById<NuguVoiceChromeView>(R.id.voice_chrome)
    }

    private val chipsView : NuguChipsView by lazy {
        activity.findViewById<NuguChipsView>(R.id.chipsView)
    }

    fun getHeight() : Int {
        return bottomSheet.height
    }

    fun isShown() : Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    fun dismiss() {
        finishImmediately()
    }

    private var isThinking = false
    private var isSpeaking = false
    private var isDialogMode = false

    init {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // no-op
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                Logger.d(TAG, "[onStateChanged] $newState")
                when(newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        if(isThinking  || (isDialogMode && isSpeaking)) {
                            return
                        }
                        finishImmediately()
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> callback.onHiddenFinished()
                }
            }
        })
        chipsView.setOnChipsListener(object : NuguChipsView.OnChipsListener {
            override fun onClick(item: NuguChipsView.Item) {
                ClientManager.getClient().requestTextInput(item.text)
            }

            override fun onScrolled(dx: Int, dy: Int) {
                if(dx > 0) {
                    if(sttTextView.visibility != View.GONE) {
                        sttTextView.visibility = View.GONE
                    }
                }
            }
        })
    }
    
    private fun updateChips(payload: RenderDirective.Payload?) {
        val items = ArrayList<NuguChipsView.Item>()
        payload?.chips?.forEach {
            items.add(NuguChipsView.Item(it.text, it.type == Chip.Type.ACTION))
        }
        chipsView.addAll(items)
        if(chipsView.size() > 0) {
            chipsView.visibility = View.VISIBLE
        }
    }

    override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
        bottomSheet.post {
            Log.d(TAG, "[onStateChanged] state: $state")
            voiceChromeController.onStateChanged(state)
        }
    }

    override fun onStateChanged(state: TTSAgentInterface.State, dialogRequestId: String) {
        isSpeaking = state == TTSAgentInterface.State.PLAYING
    }

    override fun onReceiveTTSText(text: String?, dialogRequestId: String) {
        // no op
    }

    override fun onError(dialogRequestId: String) {
        // no op
    }

    override fun onDialogUXStateChanged(
        newState: DialogUXStateAggregatorInterface.DialogUXState,
        dialogMode: Boolean,
        chips: RenderDirective.Payload?,
        sessionActivated: Boolean
    ) {
        isDialogMode = dialogMode
        isThinking = newState == DialogUXStateAggregatorInterface.DialogUXState.THINKING

        bottomSheet.post {
            Log.d(TAG, "[onDialogUXStateChanged] newState: $newState, dialogMode: $dialogMode, chips: $chips, sessionActivated: $sessionActivated")

            voiceChromeController.onDialogUXStateChanged(newState, dialogMode, chips, sessionActivated)

            when(newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                    handleExpecting(dialogMode, chips)
                }
                DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                    handleListening()
                }
                DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                    handleSpeaking(dialogMode)
                }
                DialogUXStateAggregatorInterface.DialogUXState.IDLE -> {
                    finishDelayed(DELAY_TIME_LONG)
                }
                else -> {
                    // nothing to do
                }
            }
        }
    }

    private fun handleExpecting(dialogMode: Boolean, payload: RenderDirective.Payload?) {
        if(!dialogMode) {
            sttTextView.text = ""
            sttTextView.setHint(R.string.guide_text)
            sttTextView.visibility = View.VISIBLE
        } else {
            sttTextView.visibility = View.GONE
        }

        updateChips(payload)
        cancelFinishDelayed()

        if (PreferenceHelper.enableWakeupBeep(activity)) {
            SoundPoolCompat.play(SoundPoolCompat.LocalBeep.WAKEUP)
        }
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        callback.onExpandStarted()
    }

    private fun handleListening() {
        sttTextView.visibility = View.GONE
        chipsView.visibility = View.GONE
    }

    private fun handleSpeaking(dialogMode: Boolean) {
        sttTextView.visibility = View.GONE
        chipsView.visibility = View.GONE

        if(!dialogMode) {
            finishImmediately()
            return
        }
        cancelFinishDelayed()
    }

    override fun onCancel(cause: ASRAgentInterface.CancelCause, dialogRequestId: String) {
        Logger.d(TAG, "[onCancel] $cause")
    }

    override fun onError(type: ASRAgentInterface.ErrorType, dialogRequestId: String) {
        bottomSheet.post {
            when(type) {
                ASRAgentInterface.ErrorType.ERROR_NETWORK ,
                ASRAgentInterface.ErrorType.ERROR_AUDIO_INPUT ,
                ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT -> {
                    if (PreferenceHelper.enableRecognitionBeep(activity)) {
                        SoundPoolCompat.play(SoundPoolCompat.LocalBeep.FAIL)
                    }
                }
                ASRAgentInterface.ErrorType.ERROR_UNKNOWN -> {
                    SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_NOTACCEPTABLE_ERROR)
                }
                ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT -> {
                    SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_REQUEST_TIMEOUT_ERROR)
                }
            }
        }
    }

    override fun onNoneResult(dialogRequestId: String) {
        bottomSheet.post {
            if (PreferenceHelper.enableRecognitionBeep(activity)) {
                SoundPoolCompat.play(SoundPoolCompat.LocalBeep.FAIL)
            }
        }
    }

    override fun onPartialResult(result: String, dialogRequestId: String) {
        bottomSheet.post {
            sttTextView.text = result

            if(sttTextView.visibility != View.VISIBLE) {
                sttTextView.visibility = View.VISIBLE
            }
        }
    }

    override fun onCompleteResult(result: String, dialogRequestId: String) {
        bottomSheet.post {
            sttTextView.text = result

            if(sttTextView.visibility != View.VISIBLE) {
                sttTextView.visibility = View.VISIBLE
            }

            if(PreferenceHelper.enableRecognitionBeep(activity)) {
                SoundPoolCompat.play(SoundPoolCompat.LocalBeep.SUCCESS)
            }
        }
    }

    private fun cancelFinishDelayed() {
        bottomSheet.removeCallbacks(finishRunnable)
    }

    private fun finishDelayed(time: Long) {
        bottomSheet.removeCallbacks(finishRunnable)
        bottomSheet.postDelayed(finishRunnable, time)
    }

    private fun finishImmediately() {
        bottomSheet.removeCallbacks(finishRunnable)
        bottomSheet.post(finishRunnable)
    }

    private val voiceChromeController = object : SpeechRecognizerAggregatorInterface.OnStateChangeListener, DialogUXStateAggregatorInterface.Listener {
        override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
            when (state) {
                SpeechRecognizerAggregatorInterface.State.ERROR,
                SpeechRecognizerAggregatorInterface.State.TIMEOUT,
                SpeechRecognizerAggregatorInterface.State.STOP -> {
                  //  voiceChrome.stopAnimation()
                }
                else -> {
                    // nothing to do
                }
            }
        }

        override fun onDialogUXStateChanged(
            newState: DialogUXStateAggregatorInterface.DialogUXState,
            dialogMode: Boolean,
            chips: RenderDirective.Payload?,
            sessionActivated: Boolean
        ) {
            when (newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                    voiceChrome.startAnimation(NuguVoiceChromeView.Animation.WAITING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                    voiceChrome.startAnimation(NuguVoiceChromeView.Animation.LISTENING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.THINKING -> {
                    voiceChrome.startAnimation(NuguVoiceChromeView.Animation.THINKING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                    voiceChrome.startAnimation(NuguVoiceChromeView.Animation.SPEAKING)
                }
                else -> {
                    // nothing to do
                }
            }
        }
    }
}