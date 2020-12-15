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
package com.skt.nugu.sdk.platform.android.login.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build


/**
 * Utility methods for access meta-data of package
 **/
class PackageUtils {
    /**
     * Companion objects
     */
    companion object {
        /**
         * Retrieve all of the information we know about a particular
         * package/application.
         */
        private fun getApplicationInfo(context: Context): ApplicationInfo {
            return context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
        }

        /**
         * Gets the meta-data of the component.
         * @return the value associated with the given key
         */
        fun getMetaData(context: Context, key: String): String = try {
            val metadata =
                getApplicationInfo(context).metaData
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                metadata.getString(key, "")
            } else {
                metadata.getString(key) ?: ""
            }
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }

        /**
         * Gets the string of the resource.
         * @return the value associated with the given key
         */
        fun getString(context: Context, key: String): String = try {
            context.resources.apply {
                return getString(getIdentifier(key, "string", context.packageName))
            }
            ""
        } catch (e: Resources.NotFoundException) {
            ""
        } catch (e : Exception) {
            ""
        }
    }
}