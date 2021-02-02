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

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.Activity
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.annotation.NonNull

/**
 * PermissionUtils is a wrapper library to simplify basic system permissions logic when targeting Android M+
 */
object PermissionUtils {
    fun checkPermissions(context: Context, permissions: Array<String>): Boolean {
        permissions.forEach {
            if(ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        return true
    }

    fun checkStorageGroupPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var sTargetSdkVersion = 0

    /**
     * Check that all given permissions have been granted by verifying that each entry in the
     * given array is of the value [PackageManager.PERMISSION_GRANTED].
     *
     * @see Activity.onRequestPermissionsResult
     */
    fun verifyPermissions(@NonNull context: Context, grantResults: IntArray): Boolean {
        // Verify that each required permission has been granted, otherwise return false.
        // in case targetSdkVersion is less than 23, it gives all required permissions have been granted
        // while this is called in case permission rejected, so just return false
        if (sTargetSdkVersion == 0) {
            sTargetSdkVersion = getTargetSdkVersion(context)
        }
        if (sTargetSdkVersion >= Build.VERSION_CODES.M) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        } else {
            return false
        }
    }

    private fun getTargetSdkVersion(@NonNull context: Context): Int {
        try {
            val pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
            return pi.applicationInfo.targetSdkVersion
        } catch (ignore: PackageManager.NameNotFoundException) {
        }

        return 0
    }

    fun targetSdkVersion(@NonNull context: Context): Int {
        if (sTargetSdkVersion == 0) {
            sTargetSdkVersion = getTargetSdkVersion(context)
        }
        return sTargetSdkVersion
    }

    fun needCheckPermissions(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}