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
package com.skt.nugu.sdk.platform.android.ux.widget

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.skt.nugu.sdk.platform.android.ux.R


/**
 * Wrapper class to customise android.widget.Toast
 * @param view is parent view
 */
class NuguToast(val context: Context) {
    /**
     * Companion objects
     */
    companion object {
        /**
         * Show the view or text notification for a short period of time.
         **/
        val LENGTH_SHORT = 0
        /**
         * Show the view or text notification for a long period of time.
         **/
        val LENGTH_LONG = 1

        /**
         * Returns a new [NuguToast] on this builder.
         * */
        fun with(context: Context): NuguToast {
            return NuguToast(context)
        }
    }

    /**
     * The resource id of the string resource to use
     */
    var message: String = ""

    /**
     * How long to display the message. Either LENGTH_SHORT or LENGTH_LONG
     */
    var duration: Int = LENGTH_SHORT

    var yOffset: Int = 0
    /**
     * Set the message to be displayed
     */
    fun message(resId: Int): NuguToast {
        this.message = context.resources.getString(resId)
        return this
    }
    /**
     * Set the message to be displayed
     */
    fun message(message: String): NuguToast {
        this.message = message
        return this
    }
    /**
     * Set how long to show the view for.
     * @see [LENGTH_SHORT]
     * @see [LENGTH_LONG]
     */
    fun duration(duration: Int): NuguToast {
        this.duration = duration
        return this
    }
    /**
     *  Set the Y offset in pixels to apply to the gravity's location.
     */
    fun yOffset(yOffset: Int) : NuguToast {
        this.yOffset = yOffset
        return this
    }
    /**
     * Show the toast.
     */
    fun show() {
        val toast = Toast(context.applicationContext)
        toast.setGravity(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0, yOffset)
        toast.duration = duration
        toast.view = makeContentView()
        toast.show()
    }
    /**
     * Set the toast content to an explicit view
     */
    @SuppressLint("InflateParams")
    private fun makeContentView() : View {
        val contentView = LayoutInflater.from(context).inflate(R.layout.nugu_toast_view, null)
        contentView.findViewById<TextView>(R.id.message).text = message
        return contentView
    }
}