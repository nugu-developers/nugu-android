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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.content.res.Resources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.ux.R
import com.google.android.material.snackbar.Snackbar
import android.view.ViewGroup


/**
 * Wrapper class to customise android.support.design.widget.Snackbar
 * @param view is parent view
 */
class NuguSnackbar(val parentView: View) {
    /**
     * Callback class for Snackbar instances
     */
    abstract class Callback {
        /**
         * Called when the given BaseTransientBottomBar has been dismissed,
         * either through a time-out,
         * having been manually dismissed,
         * or an action being clicked.
         */
        abstract fun onDismissed()
    }
    /**
     * Companion objects
     */
    companion object {
        /**
         * Show the Snackbar indefinitely.
         **/
        const val LENGTH_INDEFINITE = Snackbar.LENGTH_INDEFINITE
        /**
         * Show the Snackbar for a short period of time.
         **/
        const val LENGTH_SHORT = Snackbar.LENGTH_SHORT
        /**
         * Show the Snackbar for a long period of time.
         **/
        const val LENGTH_LONG = Snackbar.LENGTH_LONG

        /**
         * Returns a new [NuguSnackbar] on this builder.
         * */
        fun with(view: View): NuguSnackbar {
            return NuguSnackbar(view)
        }
    }

    /**
     * The resource id of the string resource to use
     */
    var resId: Int = 0

    /**
     * How long to display the message. Either LENGTH_SHORT or LENGTH_LONG
     */
    var duration: Int = LENGTH_SHORT
    /**
     *  callback to be invoked when the action is onDismissed
     */
    var callback: Callback? = null

    /**
     * Set the message to be displayed
     */
    fun message(resId: Int): NuguSnackbar {
        this.resId = resId
        return this
    }

    /**
     * How long to display the message
     */
    fun duration(duration: Int): NuguSnackbar {
        this.duration = duration
        return this
    }

    /**
     * set [Callback]
     */
    fun callback(callback: Callback): NuguSnackbar {
        this.callback = callback
        return this
    }

    /**
     * Show the BaseTransientBottomBar.
     */
    @Throws(IllegalArgumentException::class)
    fun show() {
        if (resId == 0) throw IllegalArgumentException("message resId necessary")

        Snackbar.make(
            this.parentView,
            this.resId,
            this.duration
        ).apply {
            setContentView(this.view)
            addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar?, event: Int) {
                    callback?.onDismissed()
                }
            })
            show()
        }
    }
    /**
     * Set the snackbar content to an explicit view
     */
    private fun setContentView(view: View) {
        // 8 is margin from all the sides
        val margin8 = dpToPx(8).toInt()
        val margin20 = dpToPx(8).toInt()
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        view.layoutParams = params.apply {
            setMargins(margin8, 0, margin8, margin20)
            width = FrameLayout.LayoutParams.MATCH_PARENT
        }
        view.background = ContextCompat.getDrawable(view.context, R.drawable.nugu_rounded_snackbar_corners)
        val textView = view.findViewById<TextView>(R.id.snackbar_text)
        ResourcesCompat.getFont(view.context, R.font.noto_sans_kr_regular_hestia)?.apply {
            textView?.typeface = this
        }
        textView?.textSize = 14F
    }

    /**
     * dp utility
     */
    private fun dpToPx(dp: Int): Float {
        return (dp * Resources.getSystem().displayMetrics.density)
    }
}