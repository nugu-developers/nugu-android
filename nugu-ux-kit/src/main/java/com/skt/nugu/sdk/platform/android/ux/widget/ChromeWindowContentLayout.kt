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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ChromeWindowContentLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    view: ViewGroup,
) : FrameLayout(context, attrs, defStyleAttr) {
    @VisibleForTesting
    internal var callback: OnChromeWindowContentLayoutCallback? = null

    private var isDark = false
    private var theme: ThemeManagerInterface.THEME = ThemeManager.DEFAULT_THEME

    companion object {
        private const val TAG = "ChromeWindowContentLayout"
    }

    interface OnChromeWindowContentLayoutCallback {
        fun onHidden()
        fun onChipsClicked(item: NuguChipsView.Item)
        fun onOutSideTouch()
    }

    fun setOnChromeWindowContentLayoutCallback(callback: OnChromeWindowContentLayoutCallback?) {
        this.callback = callback
    }

    private var sttTextView: EllipsizedTextView

    private var voiceChrome: NuguVoiceChromeView

    @VisibleForTesting
    internal var chipsView: NuguChipsView

    @VisibleForTesting
    internal var chipsListener: NuguChipsView.OnChipsListener

    private var currentState = STATE.NONE

    private val animationInterpolator = DecelerateInterpolator()

    enum class STATE {
        START_SHOWING,
        SHOWN,
        START_HIDING,
        HIDDEN,
        NONE
    }

    @VisibleForTesting
    internal val parentLayoutChangeListener = OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        (l == oldL && t == oldT && r == oldR && b == oldB).let { boundEqual ->
            Logger.d(TAG, "parentLayout Changed.. boundEqual ? $boundEqual")
            if (boundEqual) return@OnLayoutChangeListener
        }

        when (currentState) {
            STATE.START_HIDING -> {
                dismiss()
            }
            STATE.START_SHOWING -> {
                expand()
            }
            STATE.NONE, STATE.HIDDEN -> {
                ((parent as ViewGroup).height).toFloat().let { newY ->
                    if (y != newY) {
                        y = newY
                    }
                }
            }
            STATE.SHOWN -> {
                ((parent as ViewGroup).height - height).toFloat().let { newY ->
                    if (y != newY) {
                        y = newY
                    }
                }
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.bottom_sheet_chrome_window, this, true).apply {
            chipsView = findViewById(R.id.chipsView)
            sttTextView = findViewById(R.id.tv_stt)
            voiceChrome = findViewById(R.id.voice_chrome)
        }

        view.addView(this)
        with(this.layoutParams) {
            width = MATCH_PARENT
            height = dpToPx(78f, context)  // bottomSheet height(68dp) + shadow height(10dp)
        }

        setDarkMode(isDark, theme)
        clipToPadding = false

        chipsListener = object : NuguChipsView.OnChipsListener {
            override fun onClick(item: NuguChipsView.Item) {
                callback?.onChipsClicked(item)
            }

            override fun onScrolled(dx: Int, dy: Int) {
                if (dx > 0 && sttTextView.visibility != View.GONE) {
                    sttTextView.visibility = View.GONE
                }
            }
        }

        chipsView.setOnChipsListener(chipsListener)

        (parent as ViewGroup).addView(InterceptTouchEventView(context), LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as ViewGroup).addOnLayoutChangeListener(parentLayoutChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (parent as ViewGroup).removeOnLayoutChangeListener(parentLayoutChangeListener)
    }

    fun isExpanded(): Boolean {
        return currentState == STATE.SHOWN
    }

    fun dismiss() {
        Logger.d(TAG, "[dismiss] called... currentState:$currentState")

        if (currentState == STATE.HIDDEN) return

        fun onHidden() {
            callback?.onHidden()
            chipsView.onVoiceChromeHidden()
        }

        animate().y((parent as ViewGroup).height.toFloat())
            .withStartAction { currentState = STATE.START_HIDING }
            .withEndAction {
                currentState = STATE.HIDDEN
                onHidden()
            }.setInterpolator(animationInterpolator)
            .duration = 150
    }

    fun expand() {
        Logger.d(TAG, "[expand] called.. currentState:$currentState")

        if (currentState == STATE.SHOWN) return

        animate().y(((parent as ViewGroup).height - height).toFloat())
            .withStartAction { currentState = STATE.START_SHOWING }
            .withEndAction {
                currentState = STATE.SHOWN
            }.setInterpolator(animationInterpolator)
            .duration = 150
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

    fun setHint(@StringRes resId: Int) {
        sttTextView.text = ""
        sttTextView.setHint(resId)
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

    inner class InterceptTouchEventView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {

        override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
            event ?: return super.onInterceptTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN && (currentState == STATE.SHOWN || currentState == STATE.START_SHOWING)) {
                val outRect = Rect()
                this@ChromeWindowContentLayout.getGlobalVisibleRect(outRect)

                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    callback?.onOutSideTouch()
                }
            }

            return super.onInterceptTouchEvent(event)
        }
    }
}