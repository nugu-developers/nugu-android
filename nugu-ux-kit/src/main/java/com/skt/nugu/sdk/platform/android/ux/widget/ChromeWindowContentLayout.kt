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
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentLinkedQueue

class ChromeWindowContentLayout @JvmOverloads constructor(
    context: Context,
    view: ViewGroup,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var callback: OnChromeWindowContentLayoutCallback? = null

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
        (bottomSheetBehavior as? ChromeWindowBottomSheetBehavior)?.callback = callback
    }

    lateinit var bottomSheet: FrameLayout

    private val bottomSheetBehavior: BottomSheetBehavior<FrameLayout> by lazy {
        BottomSheetBehavior.from(bottomSheet)
    }

    override fun getGlobalVisibleRect(r: Rect?, globalOffset: Point?): Boolean {
        return bottomSheet.getGlobalVisibleRect(r)
    }
    fun getChromeWindowHeight() = bottomSheet.height

    private lateinit var sttTextView: TextView

    private lateinit var voiceChrome: NuguVoiceChromeView

    private lateinit var chipsView: NuguChipsView

    private val statusQueue = ConcurrentLinkedQueue<Int>()

    init {
        val inflater = LayoutInflater.from(context)
        val content = inflater.inflate(
            R.layout.bottom_sheet_chrome_window,
            view,
            true
        )
        chipsView = content.findViewById(R.id.chipsView)
        bottomSheet = content.findViewById(R.id.fl_bottom_sheet)
        sttTextView = content.findViewById(R.id.tv_stt)
        voiceChrome = content.findViewById(R.id.voice_chrome)

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

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.setBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // no-op
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                Logger.d(TAG, "[onStateChanged] $newState")
                if(statusQueue.peek() == newState) {
                    statusQueue.remove()
                }
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        if (callback?.shouldCollapsed() == true) {
                            addState(BottomSheetBehavior.STATE_HIDDEN)
                        }
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        bottomSheetBehavior.state = statusQueue.poll() ?: return
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        callback?.onHidden()
                        bottomSheetBehavior.state = statusQueue.poll() ?: return
                    }
                }
            }
        })
    }

    fun isExpanded(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }

    @Throws(IllegalStateException::class)
    private fun addState(newState : Int?) {
        if(newState == null) {
            return
        }
        when(newState) {
            BottomSheetBehavior.STATE_EXPANDED,
            BottomSheetBehavior.STATE_HIDDEN -> {/* no op */}
            else -> throw IllegalStateException("unsupported state: $newState")
        }

        if(statusQueue.isEmpty() && bottomSheetBehavior.state == newState) {
            return
        }
        statusQueue.add(newState)

        if(statusQueue.size == 1) {
            bottomSheetBehavior.state = newState
        }
    }

    fun dismiss() {
        addState(BottomSheetBehavior.STATE_HIDDEN)
    }

    fun expand() {
        addState(BottomSheetBehavior.STATE_EXPANDED)
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
            items.add(NuguChipsView.Item(it.text, it.type == Chip.Type.ACTION))
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