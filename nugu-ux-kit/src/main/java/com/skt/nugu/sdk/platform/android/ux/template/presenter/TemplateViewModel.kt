package com.skt.nugu.sdk.platform.android.ux.template.presenter

import androidx.lifecycle.ViewModel
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler


class TemplateViewModel : ViewModel() {
    companion object {
        const val TAG = "TemplateViewModel"
    }

    lateinit var nuguClientProvider: TemplateRenderer.NuguClientProvider
    var externalRenderer: TemplateRenderer.ExternalViewRenderer? = null
    var templateLoadingListener: TemplateRenderer.TemplateLoadingListener? = null
    var renderNotified = TemplateFragment.RenderNotifyState.NONE
    var onCleared: (() -> Unit)? = null
    var templateHandler: TemplateHandler? = null

    override fun onCleared() {
        Logger.d(TAG, "cleared")
        super.onCleared()
        onCleared?.invoke()
        templateHandler?.clear()

        onCleared = null
        templateHandler = null
        externalRenderer = null
        templateLoadingListener = null
    }
}