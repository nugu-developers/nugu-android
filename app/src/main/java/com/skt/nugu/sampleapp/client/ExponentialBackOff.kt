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
package com.skt.nugu.sampleapp.client

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 *  Helper to implement exponential backoff.
 **/
object ExponentialBackOff {
    private const val TAG = "ExponentialBackOff"
    private val DEFAULT_STRATEGY = Exponential(1000, 2, 10, 60.0 * 1000L, 0.5)
    private var future: ScheduledFuture<*>? = null
    private val executorService = ScheduledThreadPoolExecutor(1)
    private var connectivityManager: AndroidConnectivityManager? = null
    private var attempts = 0

    enum class ErrorCode {
        ERROR_LIMIT_EXCEEDED,
        ERROR_UNKNOWN
    }

    interface Callback {
        fun onRetry()
        fun onError(reason: ErrorCode)
    }

    private interface Strategy {
        fun getDelayMs(attempt: Int): Long
        fun shouldRetry(attempt: Int): Boolean
    }

    /**
     * Wait for network connection. after waits for DelayMs, increases the delay and runs the specified task. If there was a
     * pending backoff task waiting to run already, it will be canceled.
     *
     * @param context the application context
     * @param callback The Callback
     */
    fun awaitConnectedAndRetry(context: Context, callback: Callback) {
        cancelPrevious()

        if (connectivityManager == null) {
            connectivityManager = AndroidConnectivityManager(context)
        }
        connectivityManager?.stop()

        if (!DEFAULT_STRATEGY.shouldRetry(attempts)) {
            Log.d(TAG, "Errors limit exceeded. (attempts=$attempts)")
            callback.onError(ErrorCode.ERROR_LIMIT_EXCEEDED)
            return
        }
        val startTimeMillis = System.currentTimeMillis()
        if(!connectivityManager!!.start(object : AndroidConnectivityManager.NetworkCallback {
            override fun onAvailable() {
                connectivityManager?.stop()

                attempts++
                val idleTime = System.currentTimeMillis() - startTimeMillis
                val delay = max(DEFAULT_STRATEGY.getDelayMs(attempts) - idleTime, 0)
                scheduleNext(Runnable {
                    callback.onRetry()
                }, delay)
            }
        })) {
            callback.onError(ErrorCode.ERROR_UNKNOWN)
        }
    }

    /**
     * Sets the interval back to the initial retry interval and restarts the timer.
     */
    fun reset() {
        connectivityManager?.stop()
        cancelPrevious()
        attempts = 0
    }

    private fun scheduleNext(
        task: Runnable,
        millis: Long
    ) {
        Log.d(TAG, "Scheduling next retry in {$millis} milliseconds")

        try {
            future = executorService.schedule(task, millis, TimeUnit.MILLISECONDS)
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "Failed to schedule $e")
        }
    }

    private fun cancelPrevious() {
        if (future != null) {
            future?.cancel(true)
        }
    }

    class Exponential(
        private val scale: Int,
        private val exponent: Int,
        private val maxTries: Int,
        private val maxElapsedMillis: Double,
        private val randomizationFactor: Double
    ) : Strategy {
        /**
         * Returns a random value from the interval
         */
        private fun makeRandomizedDelay(delayMillis: Int): Int {
            val delta = randomizationFactor * delayMillis
            val min = delayMillis - delta
            val max = delayMillis + delta
            // Get a random value from the range [min, max].
            // The formula used below has a +1 because if the minInterval is 1 and the maxInterval is 3 then
            // we want a 33% chance for selecting either 1, 2 or 3.
            return (min + Math.random() * (max - min + 1)).toInt()
        }

        override fun getDelayMs(attempt: Int): Long {
            if (attempt == 1) return 0
            var delay =
                scale * Math.pow(exponent.toDouble(), attempt - 2.toDouble())
            delay = min(delay, maxElapsedMillis.toDouble())
            delay = max(delay, 0.0)

            val randomizedDelay = makeRandomizedDelay(
                delay.toInt()
            )
            return randomizedDelay.toLong()
        }

        override fun shouldRetry(attempt: Int): Boolean {
            return attempt < maxTries
        }
    }
    /**
     * Class that manages the network for network connection status. It also
     * notifies applications when network connectivity changes.
     */
    class AndroidConnectivityManager(val context: Context) {
        interface NetworkCallback {
            fun onAvailable()
        }

        private var unregisterRunnable: Runnable? = null

        val connectivityManager by lazy {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }

        fun start(delegate: NetworkCallback) : Boolean {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val defaultNetworkCallback = DefaultNetworkCallback(delegate)
                    connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
                    unregisterRunnable = Runnable {
                            connectivityManager.unregisterNetworkCallback(
                                defaultNetworkCallback
                            )
                        }
                } else {
                    val networkReceiver = NetworkReceiver(delegate)
                    val networkIntentFilter =
                        IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                    context.registerReceiver(networkReceiver, networkIntentFilter)
                    unregisterRunnable = Runnable { context.unregisterReceiver(networkReceiver) }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Does app have ACCESS_NETWORK_STATE permission?", e)
                return false
            }
            return true
        }

        fun stop() {
            unregisterRunnable?.run()
            unregisterRunnable = null
        }

        /** Respond to network changes. Only used on API levels < 24.  */
        private class NetworkReceiver(val delegate: NetworkCallback) : BroadcastReceiver() {
            private var isConnected = false
            override fun onReceive(context: Context, intent: Intent?) {
                val conn =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = conn.activeNetworkInfo
                val wasConnected = isConnected
                isConnected = networkInfo != null && networkInfo.isConnected
                if (isConnected && !wasConnected) {
                    delegate.onAvailable()
                }
            }
        }

        /** Respond to changes in the default network. Only used on API levels 24+.  */
        @TargetApi(Build.VERSION_CODES.N)
        private class DefaultNetworkCallback(val delegate: NetworkCallback) :
            ConnectivityManager.NetworkCallback() {
            private var isConnected = false
            override fun onAvailable(network: Network) {
                if (isConnected) {
                    Log.d(TAG, "이미 상태가 isConnected 입니다.")
                }
                isConnected = true
                delegate.onAvailable()
            }

            override fun onLost(network: Network) {
                isConnected = false
            }
        }
    }
}