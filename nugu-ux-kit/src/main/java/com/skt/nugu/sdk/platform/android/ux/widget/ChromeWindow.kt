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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.ux.R

class ChromeWindow(context: Context, val view: View) :
    SpeechRecognizerAggregatorInterface.OnStateChangeListener
    , DialogUXStateAggregatorInterface.Listener
    , ASRAgentInterface.OnResultListener
    , TTSAgentInterface.Listener {
    companion object {
        private const val TAG = "ChromeWindow"
    }

    interface OnChromeWindowCallback {
        fun onExpandStarted()
        fun onHiddenFinished()
        fun onChipsClicked(item: NuguChipsView.Item)
    }

    private var callback: OnChromeWindowCallback? = null
    private var contentLayout: ChromeWindowContentLayout
    private var screenOnWhileASR = false

    /**
     * set ChromeWindow callback
    */
    fun setOnChromeWindowCallback(callback: OnChromeWindowCallback?) {
        this.callback = callback
    }

    /**
     * Returns the visibility of this view
     * @return True if the view is expanded
     */
    fun isShown(): Boolean {
        return contentLayout.isExpanded()
    }

    /**
     * Dismiss the view
     */
    fun dismiss() {
        contentLayout.dismiss()
    }

    /**
     * If some part of this view is not clipped by any of its parents, then
     * return that area in r in global (root) coordinates.
     */
    fun getGlobalVisibleRect(outRect: Rect){
        contentLayout.getGlobalVisibleRect(outRect)
    }
    /**
     * Control whether we should use the attached view to keep the
     * screen on while asr is occurring.
     * @param screenOn Supply true to keep the screen on, false to allow it to turn off.
     */
    fun setScreenOnWhileASR (screenOn: Boolean) {
        if (screenOnWhileASR != screenOn) {
            screenOnWhileASR = screenOn
            updateLayoutScreenOn()
        }
    }

    private var isThinking = false
    private var isSpeaking = false
    private var isDialogMode = false
    private var isIdle = false

    init {
        val parent = view.findSuitableParent()
        if (parent == null) {
            throw IllegalArgumentException("No suitable parent found from the given view. Please provide a valid view.")
        } else {
            contentLayout = ChromeWindowContentLayout(context, parent)
            contentLayout.setOnChromeWindowContentLayoutCallback(object : ChromeWindowContentLayout.OnChromeWindowContentLayoutCallback {
                override fun shouldCollapsed(): Boolean {
                    if (isThinking || (isDialogMode && isSpeaking)) {
                        return false
                    }
                    return true
                }

                override fun onHidden() {
                    callback?.onHiddenFinished()
                }

                override fun onChipsClicked(item: NuguChipsView.Item) {
                    callback?.onChipsClicked(item)
                }
            })
        }
    }


    override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
        Logger.d(TAG, "[onStateChanged] state: $state")
        voiceChromeController.onStateChanged(state)
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
        isIdle = newState == DialogUXStateAggregatorInterface.DialogUXState.IDLE

        view.post {
            Logger.d(
                TAG,
                "[onDialogUXStateChanged] newState: $newState, dialogMode: $dialogMode, chips: $chips, sessionActivated: $sessionActivated"
            )

            voiceChromeController.onDialogUXStateChanged(
                newState,
                dialogMode,
                chips,
                sessionActivated
            )

            when (newState) {
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
                    dismiss()
                }
                else -> {
                    // nothing to do
                }
            }
            updateLayoutScreenOn()
        }
    }

    private fun handleExpecting(dialogMode: Boolean, payload: RenderDirective.Payload?) {
        if (!dialogMode) {
            contentLayout.setHint(R.string.nugu_guide_text)
            contentLayout.showText()
        } else {
            contentLayout.hideText()
        }

        contentLayout.updateChips(payload)
        contentLayout.expand()
        callback?.onExpandStarted()
    }

    fun updateChips(payload: RenderDirective.Payload) {
        contentLayout.updateChips(payload)
    }

    fun isChipsEmpty() = contentLayout.isChipsEmpty()

    private fun handleListening() {
        contentLayout.hideText()
        contentLayout.hideChips()
    }

    private fun handleSpeaking(dialogMode: Boolean) {
        contentLayout.hideText()
        contentLayout.hideChips()

        if (!dialogMode) {
            dismiss()
            return
        }
    }

    override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
    }

    override fun onError(type: ASRAgentInterface.ErrorType, header: Header, allowEffectBeep: Boolean) {
    }

    override fun onNoneResult(header: Header) {
    }

    override fun onPartialResult(result: String, header: Header) {
        view.post {
            contentLayout.setText(result)
        }
    }

    override fun onCompleteResult(result: String, header: Header) {
        view.post {
            contentLayout.setText(result)
        }
    }

    private fun updateLayoutScreenOn() {
        val screenOn = screenOnWhileASR && !isIdle
        if(view.keepScreenOn != screenOn) {
            view.keepScreenOn = screenOn
            Logger.d(TAG, "[updateLayoutScreenOn] ${view.keepScreenOn}")
        }
    }
    private val voiceChromeController =
        object : SpeechRecognizerAggregatorInterface.OnStateChangeListener,
            DialogUXStateAggregatorInterface.Listener {
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
                        contentLayout.startAnimation(NuguVoiceChromeView.Animation.WAITING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                        contentLayout.startAnimation(NuguVoiceChromeView.Animation.LISTENING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.THINKING -> {
                        contentLayout.startAnimation(NuguVoiceChromeView.Animation.THINKING)
                    }
                    DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                        contentLayout.startAnimation(NuguVoiceChromeView.Animation.SPEAKING)
                    }
                    else -> {
                        // nothing to do
                    }
                }
            }
        }
}

private fun View.findSuitableParent() : ViewGroup? {
    var view: View? = this
    var fallback: ViewGroup? = null
    do {
        if (view is CoordinatorLayout) {
            return view
        }
        if (view is FrameLayout) {
            if (view.getId() == android.R.id.content) {
                return view
            }
            fallback = view
        }
        if (view != null) {
            val parent = view.parent
            view = if (parent is View) parent else null
        }
    } while (view != null)
    return fallback
}