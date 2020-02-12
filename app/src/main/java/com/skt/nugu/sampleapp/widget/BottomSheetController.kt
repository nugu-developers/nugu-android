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
import android.widget.*
import com.skt.nugu.sdk.platform.android.ux.widget.ChipTrayView
import com.skt.nugu.sdk.platform.android.ux.widget.VoiceChromeView
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper
import com.skt.nugu.sampleapp.utils.SoundPoolCompat

class BottomSheetController(
    private val activity: Activity,
    private val callback: OnBottomSheetCallback
) : SpeechRecognizerAggregatorInterface.OnStateChangeListener
    , DialogUXStateAggregatorInterface.Listener
    , ASRAgentInterface.OnResultListener {

    companion object {
        private const val TAG = "BottomSheetController"

        private const val DELAY_TIME_LONG = 1500L
        private const val DELAY_TIME_SHORT = 150L
    }

    interface OnBottomSheetCallback {
        fun onExpandStarted()
        fun onHiddenFinished()
    }

    private var dialogMode: Boolean = false

    private val bottomSheet: FrameLayout by lazy {
        activity.findViewById<FrameLayout>(R.id.fl_bottom_sheet)
    }

    private val bottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    private val voiceChromeInfoLayout: FrameLayout by lazy {
        activity.findViewById<FrameLayout>(R.id.fl_voice_chrome_info)
    }

    private val sttResult:TextView by lazy {
        activity.findViewById<TextView>(R.id.tv_stt)
    }

    private val voiceChrome : VoiceChromeView by lazy {
        activity.findViewById<VoiceChromeView>(R.id.voice_chrome)
    }

    private val chipTray : ChipTrayView by lazy {
        activity.findViewById<ChipTrayView>(R.id.chipTray)
    }

    private val btnClose: ImageView by lazy {
        activity.findViewById<ImageView>(R.id.btn_close)
    }

    init {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // no-op
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> callback.onHiddenFinished()
                }
            }
        })

        chipTray.apply {
//            addAll(activity.resources.getStringArray(R.array.chips))
            setOnChipsClickListener(object : ChipTrayView.OnChipsClickListener {
                override fun onClick(text: String) {
                    ClientManager.getClient().requestTextInput(text)
                    sttResult.text = text
                }
            })
        }

        btnClose.setOnClickListener {
            ClientManager.speechRecognizerAggregator.stopListening(false)
        }
    }

    override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
        bottomSheet.post {
            Log.d(TAG, "[onStateChanged] state: $state")
            voiceChromeController.onStateChanged(state)
            when(state){
                SpeechRecognizerAggregatorInterface.State.WAKEUP -> {

                }
                SpeechRecognizerAggregatorInterface.State.WAITING -> {
//                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
                SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH -> {
                    sttResult.text = ""
                    cancelFinishDelayed()
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    callback.onExpandStarted()
                }
                SpeechRecognizerAggregatorInterface.State.SPEECH_START -> {
                    cancelFinishDelayed()
                }
                SpeechRecognizerAggregatorInterface.State.SPEECH_END -> {
                    
                }
                SpeechRecognizerAggregatorInterface.State.STOP -> {
                    finishImmediately()
                }
                SpeechRecognizerAggregatorInterface.State.ERROR,
                SpeechRecognizerAggregatorInterface.State.TIMEOUT -> {
                    if (PreferenceHelper.enableRecognitionBeep(activity)) {
                        SoundPoolCompat.play(SoundPoolCompat.LocalBeep.FAIL)
                    }
                }
            }
        }
    }

    override fun onDialogUXStateChanged(newState: DialogUXStateAggregatorInterface.DialogUXState, dialogMode: Boolean) {
        bottomSheet.post {
            Log.d(TAG, "[onDialogUXStateChanged] newState: $newState, dialogMode: $dialogMode")
            this.dialogMode = dialogMode

            if(dialogMode) {
                voiceChromeInfoLayout.visibility = View.GONE
                btnClose.visibility = View.GONE
            } else {
                voiceChromeInfoLayout.visibility = View.VISIBLE
                btnClose.visibility = View.VISIBLE
            }
            bottomSheet.invalidate()

            voiceChromeController.onDialogUXStateChanged(newState, dialogMode)

            when(newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                    if (PreferenceHelper.enableWakeupBeep(activity)) {
                        SoundPoolCompat.play(SoundPoolCompat.LocalBeep.WAKEUP)
                    }
                }
                DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                    if (!dialogMode) {
                        finishImmediately()
                    }
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

    override fun onCancel() {
        // no-op
    }

    override fun onError(type: ASRAgentInterface.ErrorType) {
        when(type) {
            ASRAgentInterface.ErrorType.ERROR_NETWORK ,
            ASRAgentInterface.ErrorType.ERROR_AUDIO_INPUT ,
            ASRAgentInterface.ErrorType.ERROR_LISTENING_TIMEOUT -> {
                bottomSheet.post {
                    if (PreferenceHelper.enableRecognitionBeep(activity)) {
                        SoundPoolCompat.play(SoundPoolCompat.LocalBeep.FAIL)
                    }
                }
            }
            ASRAgentInterface.ErrorType.ERROR_UNKNOWN -> {
                bottomSheet.post {
                    SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_NOTACCEPTABLE_ERROR)
                }
            }
            ASRAgentInterface.ErrorType.ERROR_RESPONSE_TIMEOUT -> {
                bottomSheet.post {
                    SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_REQUEST_TIMEOUT_ERROR)
                }
            }
        }
    }

    override fun onNoneResult() {
        bottomSheet.post {
            if(PreferenceHelper.enableRecognitionBeep(activity)) {
                SoundPoolCompat.play(SoundPoolCompat.LocalBeep.FAIL)
            }
        }
    }

    override fun onPartialResult(result: String) {
        bottomSheet.post {
            sttResult.text = result
        }
    }

    override fun onCompleteResult(result: String) {
        bottomSheet.post {
            sttResult.text = result

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

    private val finishRunnable = Runnable {
        Log.d(TAG, "[finishRunnable] called")
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private val voiceChromeController = object : SpeechRecognizerAggregatorInterface.OnStateChangeListener, DialogUXStateAggregatorInterface.Listener {
        override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
            when (state) {
                SpeechRecognizerAggregatorInterface.State.ERROR,
                SpeechRecognizerAggregatorInterface.State.TIMEOUT,
                SpeechRecognizerAggregatorInterface.State.STOP -> {
                    voiceChrome.stopAnimation()
                }
                else -> {
                    // nothing to do
                }
            }
        }

        override fun onDialogUXStateChanged(newState: DialogUXStateAggregatorInterface.DialogUXState, dialogMode: Boolean) {
            when (newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                    voiceChrome.startAnimation(VoiceChromeView.Animation.WAITING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                    voiceChrome.startAnimation(VoiceChromeView.Animation.LISTENING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.THINKING -> {
                    voiceChrome.startAnimation(VoiceChromeView.Animation.THINKING)
                }
                DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                    voiceChrome.startAnimation(VoiceChromeView.Animation.SPEAKING)
                }
                else -> {
                    // nothing to do
                }
            }
        }
    }
}