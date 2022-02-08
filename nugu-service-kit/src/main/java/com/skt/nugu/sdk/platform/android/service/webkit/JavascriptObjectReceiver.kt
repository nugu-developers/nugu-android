/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.platform.android.service.webkit

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import com.google.gson.JsonObject

@SuppressLint("VisibleForTests")
class JavascriptObjectReceiver(val listener: Listener) {
    private val gson = com.google.gson.Gson()

    interface Listener {
        fun openExternalApp(androidScheme: String?, androidAppId: String?)
        fun openInAppBrowser(url: String)
        fun closeWindow(reason: String?)
        fun setTitle(title: String)
        fun fixedTextZoom()
        fun requestActiveRoutine()
        fun requestPermission(permission: String)
        fun checkPermission(permission: String) : Boolean
    }

    @Keep
    data class JavascriptParameter(
        val method: String,
        val body: JsonObject? = null
    )
    @Keep
    data class SetTitle(val title: String)

    @Keep
    data class OpenExternalApp(val androidScheme: String?, val androidAppId: String?)

    @Keep
    data class OpenInAppBrowser(val url: String)

    @Keep
    data class CloseWindow(val reason: String)

    @Keep
    data class Permissions(val permission: String)

    @JavascriptInterface
    fun openExternalApp(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "openExternalApp") {
                val info = gson.fromJson(it.body, OpenExternalApp::class.java)
                listener.openExternalApp(info.androidScheme, info.androidAppId)
            }
        }
    }

    @JavascriptInterface
    fun openInAppBrowser(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "openInAppBrowser") {
                val info = gson.fromJson(it.body, OpenInAppBrowser::class.java)
                listener.openInAppBrowser(info.url)
            }
        }
    }

    @JavascriptInterface
    fun closeWindow(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "closeWindow") {
                val info = gson.fromJson(it.body, CloseWindow::class.java)
                listener.closeWindow(info?.reason)
            }
        }
    }

    @JavascriptInterface
    fun setTitle(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "setTitle") {
                val info = gson.fromJson(it.body, SetTitle::class.java)
                listener.setTitle(info?.title.toString())
            }
        }
    }

    @JavascriptInterface
    fun fixedTextZoom(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "fixedTextZoom") {
                listener.fixedTextZoom()
            }
        }
    }

    @JavascriptInterface
    fun requestActiveRoutine(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "requestActiveRoutine") {
                listener.requestActiveRoutine()
            }
        }
    }

    @JavascriptInterface
    fun requestPermission(parameter: String) {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "requestPermission") {
                val info = gson.fromJson(it.body, Permissions::class.java)
                listener.requestPermission(info?.permission.toString())
            }
        }
    }

    @JavascriptInterface
    fun checkPermission(parameter: String) : Boolean {
        gson.fromJson(parameter, JavascriptParameter::class.java)?.let {
            if (it.method == "checkPermission") {
                val info = gson.fromJson(it.body, Permissions::class.java)
                return listener.checkPermission(info?.permission.toString())
            }
        }
        return false
    }
}