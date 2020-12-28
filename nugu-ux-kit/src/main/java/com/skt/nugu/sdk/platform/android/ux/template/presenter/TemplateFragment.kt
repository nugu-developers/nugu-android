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

package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProvider
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo

class TemplateFragment : Fragment() {
    companion object {
        private const val TAG = "TemplateFragment"
        private const val ARG_DIALOG_REQUEST_ID = "dialog_request_id"
        private const val ARG_NAME = "name"
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_TEMPLATE = "template"
        private const val ARG_DISPLAY_TYPE = "display_type"
        private const val ARG_PLAY_SERVICE_ID = "play_service_id"

        fun newInstance(
            nuguProvider: TemplateRenderer.NuguClientProvider,
            name: String,
            dialogRequestId: String,
            templateId: String,
            template: String,
            displayType: String,
            playServiceId: String
        ): TemplateFragment {
            return TemplateFragment().apply {
                arguments = createBundle(name, dialogRequestId, templateId, template, displayType, playServiceId)
                pendingNuguProvider = nuguProvider
            }
        }

        private fun createBundle(
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

    enum class RenderNotifyState { NONE, RENDERED, RENDER_FAILED, RENDER_CLEARED }

    private val layoutId = R.layout.fragment_template
    private var templateView: TemplateView? = null
    private var pendingNuguProvider: TemplateRenderer.NuguClientProvider? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val viewModel: TemplateViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(TemplateViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNuguProvider?.run {
            viewModel.nuguClientProvider = this
        }

        viewModel.onClose = { onClose() }
    }

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

        val parent = this.view?.findViewById<ViewGroup>(R.id.template_view)

        parent?.run {
            with(templateView!!) {

                templateHandler = BasicTemplateHandler(
                    viewModel.nuguClientProvider,
                    TemplateInfo(getTemplateId()),
                    this@TemplateFragment)

                addView(this.asView())
            }
        }
    }

    override fun onDestroy() {
        templateView?.templateHandler?.clear()
        super.onDestroy()
    }

    private fun loadTemplate() {
        val dialogRequestId = arguments?.getString(ARG_DIALOG_REQUEST_ID, "") ?: ""
        val template = arguments?.getString(ARG_TEMPLATE, "") ?: ""

        Logger.d(TAG, "[load] templateId: ${getTemplateId()}")

        if (template.isBlank()) {
            return
        }

        templateView?.run {
            if (TemplateRenderer.USE_STG_SERVER) setServerUrl("http://stg-template.aicloud.kr/view")

            mainHandler.post {
                load(template, TemplateRenderer.DEVICE_TYPE_CODE, dialogRequestId, onLoadingComplete = {
                    notifyRendered()
                })
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
        mainHandler.post {
            templateView?.run {
                (templateHandler as? BasicTemplateHandler)?.templateInfo = TemplateInfo(getTemplateId())
                load(templateContent, TemplateRenderer.DEVICE_TYPE_CODE, getDialogRequestedId(), onLoadingComplete = {
                    notifyRendered()
                })
            }
        }
    }

    fun update(templateContent: String) {
        Logger.d(TAG, "update template : $templateContent")
        mainHandler.post {
            templateView?.update(templateContent, getDialogRequestedId())
        }
    }

    fun close() {
        activity?.run {
            supportFragmentManager.beginTransaction().remove(this@TemplateFragment).commitAllowingStateLoss()
            onClose()
        }
    }

    private fun onClose() {
        Logger.d(TAG, "onClose.. current notifyRenderedState. ${viewModel.renderNotified}")
        if (viewModel.renderNotified == RenderNotifyState.RENDERED) {
            notifyCleared()
        } else if (viewModel.renderNotified == RenderNotifyState.NONE) {
            notifyRenderFailed()
        }
    }

    fun startListening() {
        viewModel.nuguClientProvider.getNuguClient().asrAgent?.startRecognition()
    }

    private fun notifyRendered() {
        if (viewModel.renderNotified == RenderNotifyState.NONE) {
            Logger.i(TAG, "notifyRendered ${getTemplateId()}")
            viewModel.nuguClientProvider.getNuguClient().getDisplay()
                ?.displayCardRendered(getTemplateId(), (templateView?.templateHandler as? BasicTemplateHandler)?.displayController)
            viewModel.renderNotified = RenderNotifyState.RENDERED
        }
    }

    private fun notifyRenderFailed() {
        Logger.i(TAG, "notifyRenderFailed ${getTemplateId()}")
        viewModel.nuguClientProvider.getNuguClient().getDisplay()?.displayCardRenderFailed(getTemplateId())
        viewModel.renderNotified = RenderNotifyState.RENDER_FAILED
    }

    private fun notifyCleared() {
        Logger.i(TAG, "notifyRenderCleared ${getTemplateId()}")
        viewModel.nuguClientProvider.getNuguClient().getDisplay()?.displayCardCleared(getTemplateId())
        viewModel.renderNotified = RenderNotifyState.RENDER_CLEARED
    }
}