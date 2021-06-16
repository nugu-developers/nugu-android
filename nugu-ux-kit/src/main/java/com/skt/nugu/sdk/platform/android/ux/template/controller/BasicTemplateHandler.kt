package com.skt.nugu.sdk.platform.android.ux.template.controller

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import java.lang.ref.WeakReference

/**
 * TemplateHandler focused on interaction with Android component such as Fragment
 */
open class BasicTemplateHandler(nuguProvider: TemplateRenderer.NuguClientProvider, templateInfo: TemplateInfo, fragment: Fragment) :
    DefaultTemplateHandler(nuguProvider, templateInfo) {
    companion object {
        private const val TAG = "BasicTemplateHandler"
    }

    private var fragmentRef = WeakReference(fragment)

    override fun onCloseClicked() {
        Logger.i(TAG, "onClose()")
        fragmentRef.get()?.run { (this as? TemplateFragment)?.close() }
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