package com.skt.nugu.sdk.platform.android.ux.template.presenter

import android.content.Intent
import android.widget.Toast
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo
import java.lang.ref.WeakReference

class BasicTemplateHandler(nuguProvider: TemplateRenderer.NuguClientProvider, templateInfo: TemplateInfo, fragment: TemplateFragment) :
    DefaultTemplateHandler(nuguProvider, templateInfo) {
    companion object {
        private const val TAG = "BasicTemplateHandler"
    }

    private var fragmentRef = WeakReference(fragment)

    override fun onCloseClicked() {
        Logger.i(TAG, "onClose()")
        fragmentRef.get()?.run { close() }
    }

    override fun onNuguButtonSelected() {
        Logger.i(TAG, "onNuguButtonSelected()")
        fragmentRef.get()?.run { startListening() }
    }

    override fun showToast(text: String) {
        Logger.i(TAG, "showToast() $text")
        fragmentRef.get()?.run {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showActivity(className: String) {
        Logger.i(TAG, "showActivity() $className")
        fragmentRef.get()?.run {
            try {
                startActivity(Intent(context, Class.forName(className)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    override fun clear() {
        super.clear()
        fragmentRef.clear()
    }

    fun updateFragment(fragment: TemplateFragment) {
        fragmentRef.clear()
        fragmentRef = WeakReference(fragment)
    }
}