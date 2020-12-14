package com.skt.nugu.sampleapp.template

import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.controller.DefaultTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo
import java.lang.ref.WeakReference

class SampleTemplateHandler(androidClient: NuguAndroidClient, templateInfo: TemplateInfo, fragment: TemplateFragment) :
    DefaultTemplateHandler(androidClient, templateInfo) {
    companion object {
        private const val TAG = "SampleTemplateHandler"
    }

    private val fragmentRef = WeakReference(fragment)

    override fun onCloseClicked() {
        Log.i(TAG, "onClose()")
        fragmentRef.get()?.run { close() }
    }

    override fun onNuguButtonSelected() {
        Log.i(TAG, "onNuguButtonSelected()")
        fragmentRef.get()?.run { startListening() }
    }

    override fun showToast(text: String) {
        Log.i(TAG, "showToast() $text")
        fragmentRef.get()?.run {
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showActivity(className: String) {
        Log.i(TAG, "showActivity() $className")
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
}