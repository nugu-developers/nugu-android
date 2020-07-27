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
package com.skt.nugu.sdk.platform.android.service.webkit

import android.support.annotation.Keep
import android.webkit.JavascriptInterface
import com.google.gson.JsonObject

internal class JavascriptObjectReceiver(val listener: Listener) {
    val gson = com.google.gson.Gson()

    interface Listener {
        fun openExternalApp(androidScheme: String?, androidAppId: String?)
        fun openInAppBrowser(url: String)
        fun closeWindow(reason: String?)
    }

    @Keep
    data class JavascriptParameter(
        val method: String,
        val body: JsonObject? = null
    )

    @Keep
    data class OpenExternalApp(val androidScheme: String?, val androidAppId: String?)

    @Keep
    data class OpenInAppBrowser(val url: String)

    @Keep
    data class CloseWindow(val reason: String)

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
}