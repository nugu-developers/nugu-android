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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx

@SuppressLint("ViewConstructor")
class ChromeWindowContentLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    view: ViewGroup
) : FrameLayout(context, attrs, defStyleAttr) {
    private var callback: OnChromeWindowContentLayoutCallback? = null
    @VisibleForTesting
    var currentState : Int = BottomSheetBehavior.STATE_COLLAPSED

    private var isDark = false
    private var theme: ThemeManagerInterface.THEME = ThemeManager.DEFAULT_THEME

    companion object {
        private const val TAG = "ChromeWindowContentLayout"
    }

    interface OnChromeWindowContentLayoutCallback {
        fun shouldCollapsed(): Boolean
        fun onHidden()
        fun onChipsClicked(item: NuguChipsView.Item)
    }

    fun setOnChromeWindowContentLayoutCallback(callback: OnChromeWindowContentLayoutCallback?) {
        this.callback = callback
        (behavior as? ChromeWindowBottomSheetBehavior)?.callback = callback
    }

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>

    fun getChromeWindowHeight() = height

    private var sttTextView: EllipsizedTextView

    private var voiceChrome: NuguVoiceChromeView

    lateinit var chipsView : NuguChipsView

    private val bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, @BottomSheetBehavior.State newState: Int) {
            Logger.d(TAG, "[onStateChanged] $newState")
            val currentState = currentState
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    if (callback?.shouldCollapsed() == true) {
                        setState(BottomSheetBehavior.STATE_HIDDEN)
                    }
                }
                BottomSheetBehavior.STATE_HIDDEN -> {
                    callback?.onHidden()
                    chipsView.onVoiceChromeHidden()
                }
                BottomSheetBehavior.STATE_DRAGGING ,
                BottomSheetBehavior.STATE_SETTLING -> {
                    return
                }
            }
            if( currentState != newState ) {
                behavior.state = currentState
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_sheet_chrome_window, this, true).apply{
            chipsView = findViewById(R.id.chipsView)
            sttTextView = findViewById(R.id.tv_stt)
            voiceChrome = findViewById(R.id.voice_chrome)

        }
        view.addView(this)
        with((this.layoutParams as CoordinatorLayout.LayoutParams)) {
            behavior = ChromeWindowBottomSheetBehavior<FrameLayout>(context, null)
            height = dpToPx(78f, context)  // bottomSheet height(68dp) + shadow height(10dp)
        }

        setDarkMode(false, theme)
        clipToPadding = false

        chipsView.setOnChipsListener(object : NuguChipsView.OnChipsListener {
            override fun onClick(item: NuguChipsView.Item) {
                callback?.onChipsClicked(item)
            }

            override fun onScrolled(dx: Int, dy: Int) {
                if (dx > 0) {
                    if (sttTextView.visibility != View.GONE) {
                        sttTextView.visibility = View.GONE
                    }
                }
            }
        })

       // setState(BottomSheetBehavior.STATE_HIDDEN)
        behavior = BottomSheetBehavior.from(this)
        behavior.removeBottomSheetCallback(bottomSheetCallback)
        behavior.addBottomSheetCallback(bottomSheetCallback)
        behavior.isDraggable = false
        behavior.isHideable = true
    }

    fun isExpanded(): Boolean {
        return behavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    @VisibleForTesting
    fun setState(newState: Int) {
        if (currentState == newState) {
            Logger.w(TAG, "[setState] currentState=$currentState, newState=$newState")
            return
        }
        currentState = newState

        when (behavior.state) {
            BottomSheetBehavior.STATE_DRAGGING,
            BottomSheetBehavior.STATE_SETTLING-> {
                /** The state is changing, so wait in onStateChanged() for it to complete. **/
                Logger.d(TAG, "[setState] currentState=$currentState, newState=$newState")
            }
            else -> {
                behavior.state = newState
            }
        }
    }

    fun dismiss() {
        setState(BottomSheetBehavior.STATE_HIDDEN)
    }

    fun expand() {
        setState(BottomSheetBehavior.STATE_EXPANDED)
    }

    fun hideChips() {
        chipsView.visibility = View.GONE
    }

    fun hideText() {
        sttTextView.visibility = View.GONE
    }

    fun showText() {
        if (sttTextView.visibility != View.VISIBLE) {
            sttTextView.visibility = View.VISIBLE
        }
    }

    fun setHint(resid: Int) {
        sttTextView.text = ""
        sttTextView.setHint(resid)
    }

    fun setText(text: String) {
        sttTextView.text = text
        if (sttTextView.visibility != View.VISIBLE) {
            sttTextView.visibility = View.VISIBLE
        }
    }

    fun updateChips(payload: RenderDirective.Payload?) {
        val items = ArrayList<NuguChipsView.Item>()
        payload?.chips?.forEach {
            NuguChipsView.Item(it.text, it.type).let { chip ->
                if (it.type == Chip.Type.NUDGE) items.add(0, chip)
                else items.add(chip)
            }
        }
        chipsView.addAll(items)
        if (chipsView.size() > 0) {
            chipsView.visibility = View.VISIBLE
        }
    }

    fun isChipsEmpty() = chipsView.size() == 0

    fun startAnimation(animation: NuguVoiceChromeView.Animation) {
        voiceChrome.startAnimation(animation)
    }

    /**
     * Sets the value of a style attribute
     */
    private fun applyThemeAttrs(@StyleRes resId: Int) {
        val attrs = intArrayOf(android.R.attr.background)
        val a: TypedArray = context.obtainStyledAttributes(resId, attrs)
        try {
            background = a.getDrawable(0)
        } finally {
            a.recycle()
        }
    }

    /**
     * Sets the dark mode.
     * @param darkMode the dark mode to set
     */
    fun setDarkMode(darkMode: Boolean, theme: ThemeManagerInterface.THEME) {
        applyThemeAttrs(
            when (darkMode) {
                true -> R.style.Nugu_Widget_Chrome_Window_Dark
                false -> R.style.Nugu_Widget_Chrome_Window_Light
            }
        )

        isDark = darkMode
        this.theme = theme

        sttTextView.setDarkMode(darkMode)
        chipsView.setDarkMode(darkMode)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig ?: return

        if (theme == ThemeManagerInterface.THEME.SYSTEM) {
            val newIsDark = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if (isDark != newIsDark) {
                setDarkMode(newIsDark, theme)
            }
        }
    }
}