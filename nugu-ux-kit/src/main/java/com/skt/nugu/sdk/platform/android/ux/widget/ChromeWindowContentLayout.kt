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
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R

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

    lateinit var bottomSheet: FrameLayout

    private val behavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    override fun getGlobalVisibleRect(r: Rect?, globalOffset: Point?): Boolean {
        return bottomSheet.getGlobalVisibleRect(r)
    }

    fun getChromeWindowHeight() = bottomSheet.height

    private var sttTextView: TextView

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
        val factory = LayoutInflater.from(context)
        factory.inflate(R.layout.bottom_sheet_chrome_window, view, true).apply {
            chipsView = findViewById(R.id.chipsView)
            bottomSheet = findViewById(R.id.fl_bottom_sheet)
            sttTextView = findViewById(R.id.tv_stt)
            voiceChrome = findViewById(R.id.voice_chrome)
        }

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
        behavior.removeBottomSheetCallback(bottomSheetCallback)
        behavior.addBottomSheetCallback(bottomSheetCallback)
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
}