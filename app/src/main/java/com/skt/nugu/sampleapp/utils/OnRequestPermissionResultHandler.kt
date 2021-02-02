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
package com.skt.nugu.sampleapp.utils

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import android.util.Log
import androidx.annotation.UiThread

class OnRequestPermissionResultHandler(
    private val context: Context
    ): ActivityCompat.OnRequestPermissionsResultCallback {
    companion object {
        //private const val TAG = "OnRequestPermissionsResultHandler"
        private const val TAG = "ORPResultHandler"
    }

    interface OnPermissionListener {
        fun onGranted()
        fun onDenied()
        fun onCanceled()
    }

    private val onPermissionRequestMap = HashMap<Int, OnPermissionListener?>()

    @UiThread
    fun requestPermissions(activity: Activity,
                           permissions: Array<out String>, requestCode: Int, listener: OnPermissionListener?) {
        Log.d(TAG, "[requestPermissions]  permissions : ${permissions.contentToString()} , requestCode : $requestCode")
        onPermissionRequestMap[requestCode] = listener
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    @UiThread
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "[onRequestPermissionsResult] requestCode : $requestCode  , permissions : ${permissions.contentToString()} , grantResults : ${grantResults.contentToString()}")

        val listener = onPermissionRequestMap.remove(requestCode) ?: return

        if (grantResults.isEmpty()) {
            listener.onCanceled()
        } else {
            if (PermissionUtils.verifyPermissions(context, grantResults)) {
                listener.onGranted()
            } else {
                listener.onDenied()
            }
        }
    }
}