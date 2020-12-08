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

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.activity.MainActivity
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler.TemplateInfo
import kotlinx.android.synthetic.main.fragment_template.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TemplateFragment : Fragment() {
    companion object {
        private const val TAG = "TemplateFragment"

        private const val layoutId = R.layout.fragment_template

        private const val ARG_DIALOG_REQUEST_ID = "dialog_request_id"
        private const val ARG_NAME = "name"
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_TEMPLATE = "template"
        private const val ARG_DISPLAY_TYPE = "display_type"
        private const val ARG_PLAY_SERVICE_ID = "play_service_id"

        private const val deviceTypeCode = "YOUR_DEVICE_TYPE_CODE"

        fun newInstance(
            name: String,
            dialogRequestId: String,
            templateId: String,
            template: String,
            displayType: String,
            playServiceId: String
        ): TemplateFragment {
            return TemplateFragment().apply {
                arguments = createBundle(name, dialogRequestId, templateId, template, displayType, playServiceId)
            }
        }

        fun createBundle(
            name: String,
            dialogRequestId: String,
            templateId: String,
            template: String,
            displayType: String,
            playServiceId: String
        ): Bundle =
            Bundle().apply {
                putString(ARG_NAME, name)
                putString(ARG_DIALOG_REQUEST_ID, dialogRequestId)
                putString(ARG_TEMPLATE_ID, templateId)
                putString(ARG_TEMPLATE, template)
                putString(ARG_DISPLAY_TYPE, displayType)
                putString(ARG_PLAY_SERVICE_ID, playServiceId)
            }
    }

    private var templateView: TemplateView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        loadTemplate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        templateView = TemplateView.createView(getTemplateType(), requireContext().applicationContext)

        with(templateView!!) {
            (this as? WebView)?.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request?.url.toString() == "nugu://home") {
                        (activity as? MainActivity)?.clearAllTemplates()
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }

            templateHandler = SampleTemplateHandler(
                ClientManager.getClient(),
                TemplateInfo(getTemplateId()),
                this@TemplateFragment)

            template_view?.addView(this.asView())
            template_view?.setOnTouchListener { _, _ ->
                ClientManager.getClient().localStopTTS()
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        templateView?.templateHandler?.clear()
    }

    private fun loadTemplate() {
        val dialogRequestId = arguments?.getString(ARG_DIALOG_REQUEST_ID, "") ?: ""
        val template = arguments?.getString(ARG_TEMPLATE, "") ?: ""

        Log.i(TAG, "[load] dialogRequestId: $dialogRequestId, template: $template")

        if (template.isBlank()) {
            return
        }

        templateView?.run {
            GlobalScope.launch(Dispatchers.Main) {
                load(template, deviceTypeCode, dialogRequestId)
            }
        }
    }

    fun getTemplateId(): String {
        return arguments?.getString(ARG_TEMPLATE_ID, "") ?: ""
    }

    fun getDialogRequestedId(): String {
        return arguments?.getString(ARG_DIALOG_REQUEST_ID, "") ?: ""
    }

    fun getDisplayType(): String {
        return arguments?.getString(ARG_DISPLAY_TYPE, "") ?: ""
    }

    fun getTemplateType(): String {
        return arguments?.getString(ARG_NAME, "") ?: ""
    }

    fun getPlayServiceId(): String {
        return arguments?.getString(ARG_PLAY_SERVICE_ID, "") ?: ""
    }

    fun isMediaTemplate(): Boolean {
        return TemplateView.mediaTemplateTypes.contains(getTemplateType())
    }

    fun reload(templateContent: String) {
        GlobalScope.launch(Dispatchers.Main) {
            templateView?.run {
                (templateHandler as? SampleTemplateHandler)?.templateInfo = TemplateInfo(getTemplateId())
                GlobalScope.launch(Dispatchers.Main) {
                    load(templateContent, deviceTypeCode, getDialogRequestedId(), onLoadingComplete = {
                        ClientManager.getClient().getDisplay()
                            ?.displayCardRendered(getTemplateId(), (templateHandler as? SampleTemplateHandler)?.displayController)
                    })
                }
            }
        }
    }

    fun update(templateContent: String) {
        GlobalScope.launch(Dispatchers.Main) {
            templateView?.update(templateContent, getDialogRequestedId())
        }
    }

    fun close() {
        activity?.run {
            supportFragmentManager.beginTransaction().remove(this@TemplateFragment).commitAllowingStateLoss()
            ClientManager.getClient().getDisplay()?.displayCardCleared(getTemplateId())
        }
    }

    fun startListening() {
        ClientManager.speechRecognizerAggregator.startListening()
    }
}