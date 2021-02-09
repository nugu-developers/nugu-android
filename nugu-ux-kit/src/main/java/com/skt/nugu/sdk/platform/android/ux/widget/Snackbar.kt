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
import android.view.View
import android.widget.FrameLayout
import com.skt.nugu.sdk.platform.android.ux.R
import com.google.android.material.snackbar.Snackbar as AndroidSnackbar

/**
 * Wrapper class to customise android.support.design.widget.Snackbar
 * @param fab is parent view
 */
@Deprecated("Use NuguSnackbar")
class Snackbar(val fab: View) {
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
        val LENGTH_INDEFINITE = -2
        /**
         * Show the Snackbar for a short period of time.
         **/
        val LENGTH_SHORT = -1
        /**
         * Show the Snackbar for a long period of time.
         **/
        val LENGTH_LONG = 0

        /**
         * Returns a new [Snackbar] on this builder.
         * */
        fun with(view: View): Snackbar {
            return Snackbar(view)
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
    fun message(resId: Int): Snackbar {
        this.resId = resId
        return this
    }

    /**
     * How long to display the message
     */
    fun duration(duration: Int): Snackbar {
        this.duration = duration
        return this
    }

    /**
     * set [Callback]
     */
    fun callback(callback: Callback): Snackbar {
        this.callback = callback
        return this
    }

    /**
     * Show the BaseTransientBottomBar.
     */
    fun show() {
        AndroidSnackbar.make(
            fab,
            this.resId,
            this.duration
        ).apply {
            setContentView(this.view)
            addCallback(object : AndroidSnackbar.Callback() {
                override fun onDismissed(snackbar: com.google.android.material.snackbar.Snackbar?, event: Int) {
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
        val margin = dpToPx(8).toInt()
        val params = view.layoutParams as FrameLayout.LayoutParams
        view.layoutParams = params.apply {
            setMargins(margin, 0, margin, margin)
            width = FrameLayout.LayoutParams.MATCH_PARENT
        }
        view.context.resources.apply {
            view.background = ContextCompat.getDrawable(view.context, R.drawable.nugu_rounded_snackbar_corners)
        }
    }

    /**
     * dp utility
     */
    private fun dpToPx(dp: Int): Float {
        return (dp * Resources.getSystem().displayMetrics.density)
    }
}