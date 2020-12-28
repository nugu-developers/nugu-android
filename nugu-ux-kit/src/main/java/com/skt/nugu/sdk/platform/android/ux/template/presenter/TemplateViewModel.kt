package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.arch.lifecycle.ViewModel
import com.skt.nugu.sdk.core.utils.Logger

class TemplateViewModel : ViewModel() {
    companion object{
        const val TAG = "TemplateViewModel"
    }

    lateinit var nuguClientProvider: TemplateRenderer.NuguClientProvider
    var renderNotified = TemplateFragment.RenderNotifyState.NONE
    var onClose : (() -> Unit)? = null


    override fun onCleared() {
        super.onCleared()
        onClose?.invoke()
        Logger.d(TAG, "cleared")
    }
}