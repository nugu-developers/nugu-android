package com.skt.nugu.sdk.platform.android.login.helper

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat.startActivityForResult

/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 */
object CustomTabActivityHelper {
    const val CHROME_CUSTOM_TAB_REQUEST_CODE: Int = 100
    /**
     * To be used as a fallback to open the Uri when Custom Tabs is not available.
     */
    interface CustomTabFallback {
        /**
         * @param activity The Activity that wants to open the Uri.
         * @param uri      The uri to be opened by the fallback.
         */
        fun openUri(activity: Activity?, uri: Uri?)
    }

    /**
     * Opens the URL on a Custom Tab if possible. Otherwise fallsback to opening it on a WebView.
     *
     * @param activity         The host activity.
     * @param customTabsIntent a CustomTabsIntent to be used if Custom Tabs is available.
     * @param uri              the Uri to be opened.
     * @param fallback         a CustomTabFallback to be used if Custom Tabs is not available.
     */
    fun openCustomTab(
        activity: Activity,
        customTabsIntent: CustomTabsIntent,
        uri: Uri?,
        fallback: CustomTabFallback?
    ) {
        val packageName = CustomTabsHelper.getPackageNameToUse(activity)

        //If we cant find a package name, it means theres no browser that supports
        //Chrome Custom Tabs installed. So, we fallback to the webview
        if (packageName == null) {
            fallback?.openUri(activity, uri)
        } else {
            try {
                customTabsIntent.intent.setPackage(packageName)
                customTabsIntent.intent.data = uri
                startActivityForResult(
                    activity,
                    customTabsIntent.intent,
                    CHROME_CUSTOM_TAB_REQUEST_CODE,
                    null
                )
            } catch (e : Throwable) {
                fallback?.openUri(activity, uri)
            }
        }
    }
}