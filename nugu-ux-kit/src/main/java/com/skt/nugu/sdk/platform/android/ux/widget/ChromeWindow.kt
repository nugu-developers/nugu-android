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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
    private val view: View,
    private val nuguClientProvider: NuguClientProvider) :
     DialogUXStateAggregatorInterface.Listener, ASRAgentInterface.OnResultListener, ThemeManagerInterface.ThemeListener{
    companion object {
        private const val TAG = "ChromeWindow"
    }

    interface OnChromeWindowCallback {
        fun onExpandStarted()
        fun onHiddenFinished()
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
        fun onCustomChipsAvailable(isSpeaking : Boolean) : Array<Chip>?
    }

    private var callback: OnChromeWindowCallback? = null
    private var contentLayout: ChromeWindowContentLayout
    private var screenOnWhileASR = false
    private var customChipsProvider: CustomChipsProvider? = null
    private var isDarkMode = false
    /**
     * set ChromeWindow callback
    */
    fun setOnChromeWindowCallback(callback: OnChromeWindowCallback?) {
        this.callback = callback
    }

    fun setOnCustomChipsProvider(provider: CustomChipsProvider) {
        customChipsProvider = provider
    }

    interface NuguClientProvider {
        fun getNuguClient(): NuguAndroidClient
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
        Logger.d(TAG, "[init]")
        val parent = view.findSuitableParent()
        if (parent == null) {
            throw IllegalArgumentException("No suitable parent found from the given view. Please provide a valid view.")
        } else {
            contentLayout = ChromeWindowContentLayout(context = context, view = parent)
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
        chips: RenderDirective.Payload?,
        sessionActivated: Boolean
    ) {
        isDialogMode = dialogMode
        isThinking = newState == DialogUXStateAggregatorInterface.DialogUXState.THINKING
        isIdle = newState == DialogUXStateAggregatorInterface.DialogUXState.IDLE
        isSpeaking = newState == DialogUXStateAggregatorInterface.DialogUXState.SPEAKING

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
                    handleSpeaking(dialogMode, chips)
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

        if (payload?.target == ChipsRenderTarget.DM && dialogMode || payload?.target == ChipsRenderTarget.LISTEN) {
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

    private fun updateCustomChips(isSpeaking : Boolean) : Boolean {
        customChipsProvider?.onCustomChipsAvailable(isSpeaking)?.let { chips ->
            contentLayout.updateChips(RenderDirective.Payload(chips = chips,
                playServiceId = "", target = ChipsRenderTarget.DM)) //this two values are meaningless
            return true
        }

        return false
    }

    fun isChipsEmpty() = contentLayout.isChipsEmpty()

    private fun handleListening() {
        contentLayout.hideText()
        contentLayout.hideChips()
    }

    private fun handleSpeaking(dialogMode: Boolean, payload: RenderDirective.Payload?) {
        contentLayout.hideText()

        if (payload?.target == ChipsRenderTarget.SPEAKING) {
            updateChips(payload)
            return
        } else {
            contentLayout.hideChips()
            if (updateCustomChips(true)) return
        }

        if (!dialogMode) {
            dismiss()
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
        object : DialogUXStateAggregatorInterface.Listener {
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
        if(isChanged) {
            isDarkMode = newDarkMode
            contentLayout.setDarkMode(newDarkMode)
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