/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.platform.android.ux.template.controller

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler.TemplateInfo
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import java.lang.ref.WeakReference

/**
 * TemplateHandler focused on interaction with Android component such as Fragment
 */
open class TemplateAndroidHandler(fragment: Fragment, override val templateInfo: TemplateInfo) : TemplateHandler {
    companion object {
        private const val TAG = "TemplateAndroidHandler"
    }

    protected var fragmentRef = WeakReference(fragment)

    override fun onCloseClicked() {
        Logger.i(TAG, "onClose()")
        fragmentRef.get()?.run { (this as? TemplateFragment)?.close() }
    }

    override fun onCloseWithParents() {
        Logger.i(TAG, "onCloseWithMyParent()")
        fragmentRef.get()?.run { (this as? TemplateFragment)?.closeWithParents() }
    }

    override fun onCloseAllClicked() {
        Logger.i(TAG, "onCloseAll()")
        fragmentRef.get()?.run { (this as? TemplateFragment)?.closeAll() }
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
        fragmentRef.clear()
    }

    override fun getNuguClient(): NuguAndroidClient? = null

    fun updateFragment(fragment: Fragment) {
        fragmentRef.clear()
        fragmentRef = WeakReference(fragment)
    }
}