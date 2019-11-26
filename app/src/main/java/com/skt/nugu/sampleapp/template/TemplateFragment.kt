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

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.template.view.AbstractDisplayView
import com.skt.nugu.sampleapp.template.view.DisplayAudioPlayer

internal class TemplateFragment : Fragment(), AudioPlayerAgentInterface.Listener {
    companion object {
        private const val TAG = "TemplateFragment"
        private const val ARG_NAME = "name"
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_TEMPLATE = "template"

        fun newInstance(name: String, templateId: String, template: String): TemplateFragment {
            return TemplateFragment().apply {
                arguments = createBundle(name, templateId, template)
            }
        }

        fun createBundle(name: String, templateId: String, template: String): Bundle = Bundle().apply {
            putString(ARG_NAME, name)
            putString(ARG_TEMPLATE_ID, templateId)
            putString(ARG_TEMPLATE, template)
        }
    }

    private lateinit var containerLayout: FrameLayout
    private lateinit var templateView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "[onCreate] ${getTemplateId()}")
        ClientManager.getClient().getDisplay()?.displayCardRendered(getTemplateId())
        ClientManager.getClient().addAudioPlayerListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FrameLayout(context!!).apply {
            containerLayout = this
            updateView(getName(), getTemplateId(), getTemplate())
        }
    }

    fun updateView(name: String, templateId: String, template: String) {
        updateArguments(name, templateId, template)
        containerLayout.apply {
            removeAllViews()
            addView(TemplateViews.createView(context!!, name, templateId, template).also {
                templateView = it
                updatePlayButton(ClientManager.playerActivity)
                if(it is AbstractDisplayView) {
                    it.close.setOnClickListener {
                        val fm = activity?.supportFragmentManager
                        fm?.let {
                            it.beginTransaction().remove(this@TemplateFragment).commit()
                        }
                    }
                }

                it.setOnClickListener {
                    ClientManager.getClient().localStopTTS()
                }
            })
        }
    }

    private fun updateArguments(name: String, templateId: String, template: String) {
        arguments = createBundle(name, templateId, template)
    }

    override fun onDestroy() {
        Log.d(TAG, "[onDestroy] ${getTemplateId()}")
        ClientManager.getClient().removeAudioPlayerListener(this)
        ClientManager.getClient().getDisplay()?.displayCardCleared(getTemplateId())
        super.onDestroy()
    }

    fun getName(): String {
        return arguments?.getString(ARG_NAME, "") ?: ""
    }

    fun getTemplateId(): String {
        return arguments?.getString(ARG_TEMPLATE_ID, "") ?: ""
    }

    fun getTemplate(): String {
        return arguments?.getString(ARG_TEMPLATE, "") ?: ""
    }

    fun getBackgroundColor(): Int {
        return templateView.background?.let {
            return if (it is ColorDrawable) {
                it.color
            } else {
                0
            }
        } ?: 0
    }

    override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
        updatePlayButton(activity)
    }

    private fun updatePlayButton(activity: AudioPlayerAgentInterface.State) {
        val view = templateView

        if(view is DisplayAudioPlayer) {
            view.post {
                if(activity == AudioPlayerAgentInterface.State.PLAYING) {
                    view.play.setImageResource(R.drawable.ic_btn_pause)
                } else {
                    view.play.setImageResource(R.drawable.ic_btn_play)
                }
            }
        }
    }
}