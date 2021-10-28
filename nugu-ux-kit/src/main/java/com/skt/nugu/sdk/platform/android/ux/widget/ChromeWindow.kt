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
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.agent.chips.RenderDirective.Payload.Target as ChipsRenderTarget

class ChromeWindow(
    context: Context,
    private val view: ViewGroup,
    private val nuguClientProvider: NuguClientProvider,
) : DialogUXStateAggregatorInterface.Listener, ASRAgentInterface.OnResultListener, ThemeManagerInterface.ThemeListener {
    companion object {
        private const val TAG = "ChromeWindow"
    }

    interface OnChromeWindowCallback {
        fun onExpandStarted()
        fun onHiddenFinished()
    }

    interface OnChipsClickListener {
        /**
         * Called when a chips clicked.
         */
        fun onChipsClicked(item: NuguChipsView.Item)
    }

    interface CustomChipsProvider {
        /**
         * Called when custom chip could be shown.
         *
         * @param isSpeaking Whether TTS is Speaking or not.
         * If it is true TTS is speaking.
         * If it is false ASR State is EXPECTING_SPEECH which means system is waiting users utterance
         */
        fun onCustomChipsAvailable(isSpeaking: Boolean): Array<Chip>?
    }

    @VisibleForTesting
    internal var callback: OnChromeWindowCallback? = null
    @VisibleForTesting
    internal var onChipsClickListener: OnChipsClickListener? = null

    @VisibleForTesting
    internal var contentLayout: ChromeWindowContentLayout
    private var screenOnWhileASR = false
    private var customChipsProvider: CustomChipsProvider? = null
    private var isDarkMode = false

    private var isDialogMode = false
    private var currentDialogUXState: DialogUXStateAggregatorInterface.DialogUXState? = null

    interface NuguClientProvider {
        fun getNuguClient(): NuguAndroidClient
    }

    init {
        Logger.d(TAG, "[init]")
        contentLayout = ChromeWindowContentLayout(context = context, view = view)
        contentLayout.setOnChromeWindowContentLayoutCallback(object : ChromeWindowContentLayout.OnChromeWindowContentLayoutCallback {
            override fun onHidden() {
                callback?.onHiddenFinished()
            }

            override fun onChipsClicked(item: NuguChipsView.Item) {
                onChipsClickListener?.let {
                    it.onChipsClicked(item)
                    return
                }

                // if callback exist, do not default behavior(following).
                nuguClientProvider.getNuguClient().requestTextInput(item.text)
                nuguClientProvider.getNuguClient().asrAgent?.stopRecognition()
            }

            override fun onOutSideTouch() {
                if (isThinking() || (isDialogMode && isSpeaking())) {
                    return
                }

                if (currentDialogUXState == DialogUXStateAggregatorInterface.DialogUXState.EXPECTING) {
                    nuguClientProvider.getNuguClient().asrAgent?.stopRecognition()
                }
            }
        })
        nuguClientProvider.getNuguClient().themeManager.addListener(this)
        nuguClientProvider.getNuguClient().addDialogUXStateListener(this)
        nuguClientProvider.getNuguClient().addASRResultListener(this)

        applyTheme(nuguClientProvider.getNuguClient().themeManager.theme)
    }

    fun destroy() {
        Logger.d(TAG, "[destroy]")
        nuguClientProvider.getNuguClient().themeManager.removeListener(this)
        nuguClientProvider.getNuguClient().removeDialogUXStateListener(this)
        nuguClientProvider.getNuguClient().removeASRResultListener(this)
    }

    override fun onDialogUXStateChanged(
        newState: DialogUXStateAggregatorInterface.DialogUXState,
        dialogMode: Boolean,
        payload: RenderDirective.Payload?,
        sessionActivated: Boolean,
    ) {
        isDialogMode = dialogMode
        currentDialogUXState = newState

        Logger.d(
            TAG,
            "[onDialogUXStateChanged] newState: $newState, dialogMode: $dialogMode, payload: $payload, sessionActivated: $sessionActivated"
        )

        view.post {
            // animation
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

            when (newState) {
                DialogUXStateAggregatorInterface.DialogUXState.EXPECTING -> {
                    handleExpecting(dialogMode, payload)
                }
                DialogUXStateAggregatorInterface.DialogUXState.LISTENING -> {
                    handleListening()
                }
                DialogUXStateAggregatorInterface.DialogUXState.SPEAKING -> {
                    handleSpeaking(payload, sessionActivated)
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

        if ((payload?.target == ChipsRenderTarget.DM && dialogMode) || payload?.target == ChipsRenderTarget.LISTEN) {
            updateChips(payload)
        } else {
            updateChips(null)
            updateCustomChips(false)
        }

        contentLayout.expand()
        callback?.onExpandStarted()
    }

    private fun updateChips(payload: RenderDirective.Payload?) {
        contentLayout.updateChips(payload)
    }

    private fun updateCustomChips(isSpeaking: Boolean): Boolean {
        customChipsProvider?.onCustomChipsAvailable(isSpeaking)?.let { chips ->
            contentLayout.updateChips(RenderDirective.Payload(chips = chips,
                playServiceId = "", target = ChipsRenderTarget.DM)) //this two values are meaningless
            return true
        }

        return false
    }

    /**
     * set ChromeWindow callback
     */
    fun setOnChromeWindowCallback(callback: OnChromeWindowCallback?) {
        this.callback = callback
    }

    /**
     * Set a listener which invoked on chips clicked.
     * A listener will override a default behavior.
     */
    fun setOnChipsClickListener(listener: OnChipsClickListener?) {
        this.onChipsClickListener = listener
    }

    fun setOnCustomChipsProvider(provider: CustomChipsProvider) {
        customChipsProvider = provider
    }


    /**
     * Returns the visibility of this view
     * @return True if the view is expanded
     */
    fun isShown(): Boolean {
        return contentLayout.isExpanded()
    }

    private fun isThinking() = currentDialogUXState == DialogUXStateAggregatorInterface.DialogUXState.THINKING
    private fun isIdle() = currentDialogUXState == DialogUXStateAggregatorInterface.DialogUXState.IDLE
    private fun isSpeaking() = currentDialogUXState == DialogUXStateAggregatorInterface.DialogUXState.SPEAKING

    /**
     * Dismiss the view
     *
     * This method changed invisible.
     * You must call SpeechRecognizerAggregator.stopListening() for dismissing ChromeWindow.
     */
    private fun dismiss() {
        contentLayout.dismiss()
    }

    /**
     * If some part of this view is not clipped by any of its parents, then
     * return that area in r in global (root) coordinates.
     */
    fun getGlobalVisibleRect(outRect: Rect) {
        contentLayout.getGlobalVisibleRect(outRect)
    }

    /**
     * Control whether we should use the attached view to keep the
     * screen on while asr is occurring.
     * @param screenOn Supply true to keep the screen on, false to allow it to turn off.
     */
    fun setScreenOnWhileASR(screenOn: Boolean) {
        if (screenOnWhileASR != screenOn) {
            screenOnWhileASR = screenOn
            updateLayoutScreenOn()
        }
    }

    fun isChipsEmpty() = contentLayout.isChipsEmpty()

    private fun handleListening() {
        contentLayout.hideText()
        contentLayout.hideChips()
    }

    private fun handleSpeaking(payload: RenderDirective.Payload?, sessionActivated: Boolean) {
        contentLayout.hideText()

        if (payload?.target == ChipsRenderTarget.SPEAKING) {
            updateChips(payload)
            return
        } else {
            contentLayout.hideChips()
            if (updateCustomChips(true)) return
        }

        if (!sessionActivated) {
            dismiss()
        }
    }

    override fun onCancel(cause: ASRAgentInterface.CancelCause, header: Header) {
        // do nothing
    }

    override fun onError(type: ASRAgentInterface.ErrorType, header: Header, allowEffectBeep: Boolean) {
        // do nothing
    }

    override fun onNoneResult(header: Header) {
        // do nothing
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
        val screenOn = screenOnWhileASR && !isIdle()
        if (view.keepScreenOn != screenOn) {
            view.keepScreenOn = screenOn
            Logger.d(TAG, "[updateLayoutScreenOn] ${view.keepScreenOn}")
        }
    }

    override fun onThemeChange(theme: ThemeManagerInterface.THEME) {
        Logger.d(TAG, "[onThemeChange] $theme")
        applyTheme(theme)
    }

    private fun applyTheme(newTheme: ThemeManagerInterface.THEME) {
        val newDarkMode = when (newTheme) {
            ThemeManagerInterface.THEME.LIGHT -> false
            ThemeManagerInterface.THEME.DARK -> true
            ThemeManagerInterface.THEME.SYSTEM -> {
                val uiMode = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        Logger.d(TAG, "[applyTheme] newState=$newTheme, newDarkMode=${newDarkMode}, isDarkMode=$isDarkMode")
        val isChanged = newDarkMode != isDarkMode
        if (isChanged) {
            isDarkMode = newDarkMode
            contentLayout.setDarkMode(newDarkMode, newTheme)
        }
    }
}