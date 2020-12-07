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

package com.skt.nugu.sdk.platform.android.ux.utils

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.support.annotation.RequiresPermission
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Utility classes for checking network availability
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"
    private const val DNS_DEFAULT_TIMEOUT_MS = 5000L
    private const val DEFAULT_HOST = "google.com"
    private val EXECUTOR: Executor = Executors.newCachedThreadPool()

    /**
     * Gets a value indicating whether a network is available to the application.
     * @return true If the device has data connectivity
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isNetworkAvailable(context: Context): Boolean {
        val connMgr =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        if (networkInfo?.isConnected != true) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networkCapabilities = connMgr.activeNetwork ?: return false
            val actNw = connMgr.getNetworkCapabilities(networkCapabilities) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> checkPublicDns()
            }
        } else {
            connMgr.activeNetworkInfo?.run {
                return when (type) {
                    ConnectivityManager.TYPE_MOBILE -> true
                    ConnectivityManager.TYPE_ETHERNET -> true
                    else -> checkPublicDns()
                }
            }
        }
        return false
    }

    /**
     * Check if Public DNS resolve is successful
     */
    private fun checkPublicDns(): Boolean {
        var result: Boolean = false
        val latch = CountDownLatch(1)
        EXECUTOR.execute {
            try {
                val address = InetAddress.getAllByName(DEFAULT_HOST)
                result = address.isNotEmpty()
            } catch (e: Throwable) {
                Log.d(TAG, "EXCEPTION : $e")
            } finally {
                latch.countDown()
            }
        }
        latch.await(DNS_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return result
    }
}