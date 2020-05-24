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
package com.skt.nugu.sampleapp.template

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.template.view.AbstractDisplayView
import com.skt.nugu.sampleapp.template.view.DisplayAudioPlayer
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelSlideListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import java.util.*

internal class TemplateFragment : Fragment(), AudioPlayerAgentInterface.Listener {
    companion object {
        private const val TAG = "TemplateFragment"
        private const val ARG_NAME = "name"
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_TEMPLATE = "template"
        private const val ARG_DISPLAY_TYPE = "display_type"
        private const val AUDIOPLAYER_NAMESPACE = "AudioPlayer"

        fun newInstance(
            name: String,
            templateId: String,
            template: String,
            displayType: String
        ): TemplateFragment {
            return TemplateFragment().apply {
                arguments = createBundle(name, templateId, template, displayType)
            }
        }

        fun createBundle(
            name: String,
            templateId: String,
            template: String,
            displayType: String
        ): Bundle =
            Bundle().apply {
                putString(ARG_NAME, name)
                putString(ARG_TEMPLATE_ID, templateId)
                putString(ARG_TEMPLATE, template)
                putString(ARG_DISPLAY_TYPE, displayType)
            }
    }

    private lateinit var containerLayout: FrameLayout
    private lateinit var templateView: View
    private var progressTimer: Timer? = null

    private val slidingLayout: SlidingUpPanelLayout? by lazy {
        activity?.findViewById<SlidingUpPanelLayout>(R.id.sliding_layout)
    }

    var controller = object : DisplayAggregatorInterface.Controller {
        override fun controlFocus(direction: Direction): Boolean {
            // TODO : XXX
            Log.d(TAG, "[controlFocus] $direction (not implemented yet)")
            return false
        }

        override fun controlScroll(direction: Direction): Boolean {
            // TODO : XXX
            Log.d(TAG, "[controlScroll] $direction (not implemented yet)")
            return false
        }

        override fun getFocusedItemToken(): String? {
            // TODO : XXX
            Log.d(TAG, "[getFocusedItemToken] (not implemented yet)")
            return null
        }

        override fun getVisibleTokenList(): List<String>? {
            // TODO : XXX
            Log.d(TAG, "[getVisibleTokenList] (not implemented yet)")
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "[onCreate] ${getTemplateId()}")

        ClientManager.getClient().getDisplay()?.displayCardRendered(getTemplateId(), controller)
        ClientManager.getClient().addAudioPlayerListener(this)
        ClientManager.getClient().audioPlayerAgent?.setLyricsPresenter(object : LyricsPresenter {
            override fun getVisibility(): Boolean {
                val view = templateView
                if (view is DisplayAudioPlayer) {
                    return view.lyricsView.visibility == View.VISIBLE
                }
                return false
            }

            override fun show(): Boolean {
                val view = templateView
                if (view is DisplayAudioPlayer) {
                    view.lyricsView.visibility = View.VISIBLE
                    return true
                }
                return false
            }

            override fun hide(): Boolean {
                val view = templateView
                if (view is DisplayAudioPlayer) {
                    view.lyricsView.visibility = View.GONE
                    return true
                }
                return false
            }

            override fun controlPage(direction: Direction): Boolean {
                return false
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(context!!).apply {
            containerLayout = this
            updateView(getName(), getTemplateId(), getTemplate(), getDisplayType())
            slidingLayout?.addPanelSlideListener(object : PanelSlideListener {
                override fun onPanelSlide(panel: View?, slideOffset: Float) {
                    val dragView = panel?.findViewById<LinearLayout>(R.id.audio_bar_layout)
                    dragView?.alpha = 1 - (Math.min(100f, (slideOffset * 100f * 3f))) / 100f
                }

                override fun onPanelStateChanged(
                    panel: View?,
                    previousState: PanelState?,
                    newState: PanelState?
                ) {
                    Log.i(TAG, "onPanelStateChanged " + newState)
                }
            })
        }
    }

    fun updateView(name: String, templateId: String, template: String, displayType: String) {
        updateArguments(name, templateId, template, displayType)
        updateSlidingLayout()
        containerLayout.apply {
            removeAllViews()
            addView(TemplateViews.createView(context!!, name, templateId, template).also { view ->
                templateView = view
                updatePlayButton(ClientManager.playerActivity)
                if (view is AbstractDisplayView) {
                    view.close.setOnClickListener {
                        if (view is DisplayAudioPlayer) {
                            if (view.lyricsView.visibility == View.VISIBLE) {
                                view.lyricsView.visibility = View.GONE
                                return@setOnClickListener
                            }
                        }
                        activity?.supportFragmentManager?.let {
                            it.beginTransaction().remove(this@TemplateFragment).commit()
                        }
                    }
                    view.collapsed.setOnClickListener {
                        slidingLayout?.panelState = PanelState.COLLAPSED
                    }
                }

                view.setOnClickListener {
                    ClientManager.getClient().localStopTTS()
                }
            })
        }
    }

    private fun updateSlidingLayout() {
        slidingLayout?.post {
            var slidingHeight = 0

            if (getNamespace() == AUDIOPLAYER_NAMESPACE) {
                slidingLayout?.panelState =
                    if (slidingLayout?.panelState == PanelState.COLLAPSED)
                        PanelState.COLLAPSED else PanelState.EXPANDED
            } else {
                val hasAudioPlayer =
                    slidingLayout?.panelState == PanelState.COLLAPSED || slidingLayout?.panelState == PanelState.EXPANDED
                slidingLayout?.panelState =
                    if (hasAudioPlayer) PanelState.COLLAPSED else PanelState.HIDDEN
                slidingHeight = slidingLayout?.panelHeight ?: 0
            }
            containerLayout.setPadding(0, slidingHeight, 0, 0)
        }
    }

    override fun onDestroyView() {
        if (getNamespace() == AUDIOPLAYER_NAMESPACE) {
            slidingLayout?.panelState = PanelState.HIDDEN
        }
        super.onDestroyView()
    }

    private fun updateArguments(
        name: String,
        templateId: String,
        template: String,
        displayType: String
    ) {
        arguments = createBundle(name, templateId, template, displayType)
    }

    override fun onDestroy() {
        Log.d(TAG, "[onDestroy] ${getTemplateId()}")
        ClientManager.getClient().removeAudioPlayerListener(this)
        ClientManager.getClient().getDisplay()?.displayCardCleared(getTemplateId())
        super.onDestroy()
    }

    fun getNamespace() = getName().split(".").first()
    fun getName(): String {
        return arguments?.getString(ARG_NAME, "") ?: ""
    }

    fun getTemplateId(): String {
        return arguments?.getString(ARG_TEMPLATE_ID, "") ?: ""
    }

    fun getTemplate(): String {
        return arguments?.getString(ARG_TEMPLATE, "") ?: ""
    }

    fun getDisplayType(): String {
        return arguments?.getString(ARG_DISPLAY_TYPE, "") ?: ""
    }

    override fun onStateChanged(
        activity: AudioPlayerAgentInterface.State,
        context: AudioPlayerAgentInterface.Context
    ) {
        Log.d(TAG, "[onStateChanged] activity: $activity")
        updatePlayButton(activity)
        updatePlayOffset(activity)
    }

    private fun updatePlayOffset(activity: AudioPlayerAgentInterface.State) {
        val view = templateView

        progressTimer?.cancel()
        progressTimer = Timer()
        progressTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                    if (view is DisplayAudioPlayer) {
                        val offset =
                            ClientManager.getClient().audioPlayerAgent?.getOffset()?.toInt() ?: 0
                        view.post {
                            view.progress.progress = offset
                            view.playtime.text = TemplateUtils.convertToTime(offset)
                            view.lyricsView.setCurrentTime(offset)
                            view.smallLyricsView.setCurrentTime(offset)
                        }
                    }
                } else {
                    progressTimer?.cancel()
                    progressTimer = null
                }
            }
        }, 500L, 1000L)
    }

    private fun updatePlayButton(activity: AudioPlayerAgentInterface.State) {
        val view = templateView

        if (view is DisplayAudioPlayer) {
            view.post {
                if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                    view.play.setImageResource(R.drawable.btn_pause_48)
                    view.bar_play.setImageResource(R.drawable.btn_pause_32)
                } else {
                    view.play.setImageResource(R.drawable.btn_play_48)
                    view.bar_play.setImageResource(R.drawable.btn_play_32)
                }
            }
        }
    }

}