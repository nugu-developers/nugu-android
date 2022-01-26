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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo
import java.util.*

class TemplateFragment : Fragment() {
    companion object {
        private const val TAG = "TemplateFragment"
        private const val ARG_DIALOG_REQUEST_ID = "dialog_request_id"
        private const val ARG_NAME = "name"
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_PARENT_TEMPLATE_ID = "parent_template_id"
        private const val ARG_TEMPLATE = "template"
        private const val ARG_DISPLAY_TYPE = "display_type"
        private const val ARG_PLAY_SERVICE_ID = "play_service_id"

        fun newInstance(
            nuguProvider: TemplateRenderer.NuguClientProvider,
            externalRenderer: TemplateRenderer.ExternalViewRenderer? = null,
            templateLoadingListener: TemplateRenderer.TemplateLoadingListener? = null,
            templateHandlerFactory: TemplateHandler.TemplateHandlerFactory,
            name: String,
            dialogRequestId: String,
            templateId: String,
            parentTemplateId: String?,
            template: String,
            displayType: DisplayAggregatorInterface.Type,
            playServiceId: String,
        ): TemplateFragment {
            return TemplateFragment().apply {
                arguments = createBundle(name, dialogRequestId, templateId, parentTemplateId, template, displayType, playServiceId)
                pendingNuguProvider = nuguProvider
                pendingExternalViewRenderer = externalRenderer
                pendingTemplateLoadingListener = templateLoadingListener
                handlerFactory = templateHandlerFactory
            }
        }

        @VisibleForTesting
        fun createBundle(
            name: String,
            dialogRequestId: String,
            templateId: String,
            parentTemplateId: String?,
            template: String,
            displayType: DisplayAggregatorInterface.Type,
            playServiceId: String,
        ): Bundle =
            Bundle().apply {
                putString(ARG_NAME, name)
                putString(ARG_DIALOG_REQUEST_ID, dialogRequestId)
                putString(ARG_TEMPLATE_ID, templateId)
                putString(ARG_PARENT_TEMPLATE_ID, parentTemplateId)
                putString(ARG_TEMPLATE, template)
                putSerializable(ARG_DISPLAY_TYPE, displayType)
                putString(ARG_PLAY_SERVICE_ID, playServiceId)
            }
    }

    enum class RenderNotifyState { NONE, RENDERED, RENDER_FAILED, RENDER_CLEARED }

    private val layoutId = R.layout.fragment_template
    private var templateView: TemplateView? = null
    private var pendingNuguProvider: TemplateRenderer.NuguClientProvider? = null
    private var pendingExternalViewRenderer: TemplateRenderer.ExternalViewRenderer? = null
    private var pendingTemplateLoadingListener: TemplateRenderer.TemplateLoadingListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var previousRenderInfo: Any? = null

    private lateinit var handlerFactory: TemplateHandler.TemplateHandlerFactory

    private val viewModel: TemplateViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(TemplateViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNuguProvider?.run {
            viewModel.nuguClientProvider = this
        }

        pendingExternalViewRenderer?.run {
            viewModel.externalRenderer = this
        }

        pendingTemplateLoadingListener?.run {
            viewModel.templateLoadingListener = this
        }

        viewModel.onClose = { onClose(false) }

        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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

                if (viewModel.templateHandler != null) {
                    (viewModel.templateHandler as? DefaultTemplateHandler)?.updateFragment(this@TemplateFragment)
                    templateHandler = viewModel.templateHandler
                } else {
                    templateHandler = handlerFactory.onCreate(viewModel.nuguClientProvider,
                        TemplateInfo(getTemplateId(), getTemplateType()),
                        this@TemplateFragment)
                    viewModel.templateHandler = templateHandler
                }

                addView(this.asView())
            }
        }
    }

    private fun loadTemplate() {
        val dialogRequestId = arguments?.getString(ARG_DIALOG_REQUEST_ID, "") ?: ""
        val template = arguments?.getString(ARG_TEMPLATE, "") ?: ""

        Logger.d(TAG, "[load] templateId: ${getTemplateId()}")

        if (template.isBlank()) {
            return
        }

        templateView?.run {
            TemplateRenderer.SERVER_URL?.run { setServerUrl(this) }

            mainHandler.post {
                viewModel.templateLoadingListener?.onStart(getTemplateId(), getTemplateType(), getDisplayType())
                load(template, TemplateRenderer.DEVICE_TYPE_CODE, dialogRequestId,
                    onLoadingComplete = {
                        notifyRendered()

                        previousRenderInfo?.run {
                            templateView?.applyRenderInfo(this)
                            previousRenderInfo = null
                        }

                        viewModel.templateLoadingListener?.onComplete(getTemplateId(), getTemplateType(), getDisplayType())
                    }, onLoadingFail = { reason ->
                        viewModel.templateLoadingListener?.onReceivedError(getTemplateId(), getTemplateType(), getDisplayType(), reason)
                    }, !TemplateView.enableCloseButton(getTemplateType(), getPlayServiceId(), getDisplayType()))
            }
        }
    }

    fun getTemplateId(): String {
        return arguments?.getString(ARG_TEMPLATE_ID, "") ?: ""
    }

    fun getParentTemplateId(): String {
        return arguments?.getString(ARG_PARENT_TEMPLATE_ID, "") ?: ""
    }

    fun getDialogRequestedId(): String {
        return arguments?.getString(ARG_DIALOG_REQUEST_ID, "") ?: ""
    }

    fun getDisplayType(): DisplayAggregatorInterface.Type? {
        return (arguments?.getSerializable(ARG_DISPLAY_TYPE) as? DisplayAggregatorInterface.Type)
    }

    fun getTemplateType(): String {
        return arguments?.getString(ARG_NAME, "") ?: ""
    }

    fun getPlayServiceId(): String {
        return arguments?.getString(ARG_PLAY_SERVICE_ID, "") ?: ""
    }

    fun isMediaTemplate(): Boolean {
        return TemplateView.MEDIA_TEMPLATE_TYPES.contains(getTemplateType())
    }

    fun getRenderInfo(): Any? {
        return templateView?.getRenderInfo()
    }

    fun update(templateContent: String) {
        Logger.d(TAG, "update template : $templateContent")

        mainHandler.post {
            templateView?.update(templateContent, getDialogRequestedId())
        }

        arguments?.run {
            putString(ARG_TEMPLATE, mergeTemplate(getString(ARG_TEMPLATE, ""), templateContent))
        }
    }

    fun close() {
        activity?.run {
            supportFragmentManager.beginTransaction().remove(this@TemplateFragment).commitAllowingStateLoss()
            onClose()
        }
    }

    fun closeWithParents() {
        activity?.run {
            supportFragmentManager.beginTransaction().remove(this@TemplateFragment).commitAllowingStateLoss()
            onClose()

            supportFragmentManager.fragments.filterIsInstance<TemplateFragment>()
                .find { it.getTemplateId() == this@TemplateFragment.getParentTemplateId() }?.let {
                supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
                it.onClose()
            }
        }
    }

    fun closeAll() {
        activity?.run {
            supportFragmentManager.fragments.filterIsInstance<TemplateFragment>().forEach {
                supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
                it.onClose()
            }
        }
    }

    fun isNuguButtonVisible(): Boolean = templateView?.isNuguButtonVisible() == true

    /**
     * @param isUserIntention : If it is true, User wanted to close template intentionally.
     * For example user could click close button or request as uttering.
     * Also It could be done by SDK's request.
     * If is is false, this means the fragment is destroyed by unknown reason.
     */
    private fun onClose(isUserIntention: Boolean = true) {
        Logger.d(TAG,
            "onClose.. current notifyRenderedState. ${viewModel.renderNotified}, isUserInteraction : $isUserIntention,  externalRendering :${
                viewModel.externalRenderer?.getVisibleList()?.any { it.templateId == getTemplateId() } == true
            }")
        if (viewModel.renderNotified == RenderNotifyState.RENDERED) {
            if (!isUserIntention
                && viewModel.externalRenderer?.getVisibleList()?.any { it.templateId == getTemplateId() } == true
            ) {
                // do not invoke notifyCleared()
            } else {
                notifyCleared()
            }

        } else if (viewModel.renderNotified == RenderNotifyState.NONE) {
            notifyRenderFailed()
        }
    }

    private fun notifyRendered() {
        if (viewModel.renderNotified == RenderNotifyState.NONE) {
            Logger.i(TAG, "notifyRendered ${getTemplateId()}")
            viewModel.nuguClientProvider.getNuguClient().getDisplay()
                ?.displayCardRendered(getTemplateId(), (templateView?.templateHandler as? DefaultTemplateHandler)?.displayController)
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