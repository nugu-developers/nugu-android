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

import android.util.Log
import com.skt.nugu.sdk.platform.android.login.auth.*
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for scheduling proactive token refresh events.
 */
class TokenRefresher(val authClient: NuguOAuth) {
    interface Listener {
        /**
         * Return {@code true} if the token needs to be refresh_token.
         */
        fun onShouldRefreshToken(): Boolean
        /**
         * Callback that is invoked when the refresh_token change.
         */
        fun onCredentialsChanged(credentials: Credentials)
        /**
         * Callback that is invoked when a refresh_token error occurs.
         */
        fun onRefreshTokenError(error: NuguOAuthError)
    }

    private val TAG = "TokenRefresher"

    private var isRunning = AtomicBoolean(false)

    private var listener: Listener? = null

    /**
     * minimum number of seconds to schedule a refresh
     */
    private val MIN_REFRESH_MILLIS: Long = TimeUnit.SECONDS.toMillis(30)

    /**
     * number of seconds to remove from suggested token timeout
     */
    private val REFRESH_BACKOFF_MILLIS: Long = TimeUnit.HOURS.toMillis(1)

    private var future: ScheduledFuture<*>? = null
    private val executorService = ScheduledThreadPoolExecutor(1)

    private fun nextInterval(): Long {
        val expiresInMillis = authClient.getExpiresInMillis()
        val backoffMillis =
            min((expiresInMillis * 0.1).toLong(), REFRESH_BACKOFF_MILLIS).apply {
                max(this, MIN_REFRESH_MILLIS)
            }
        return max(
            expiresInMillis + authClient.getIssuedTime() - Date().time - backoffMillis,
            MIN_REFRESH_MILLIS
        )
    }

    /**
     * Start TokenRefresher.
     */
    fun start(forceUpdate: Boolean = false) {
        Log.d(TAG, "Starting the proactive token refresher")

        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "already started, forceUpdate=$forceUpdate")
            if(!forceUpdate) return
        }
        scheduleRefresh(if (forceUpdate) 0 else nextInterval())
    }

    /**
     * Stop TokenRefresher.
     */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "already stop")
            return
        }
        cancelPrevious()
        removeListener()
    }

    /**
     * Set a listener
     * @param listener the listener that added
     */
    fun setListener(listener: Listener) {
        this.listener = listener
    }

    /**
     * Removes the listener from the TokenRefresher.
     */
    fun removeListener() {
        this.listener = null
    }
    /**
     * Schedule a token refresh to be executed after a specified duration.
     * @param task the task
     * @param millis Duration in milliseconds, after which the token should be forcibly refreshed.
     */
    private fun scheduleNext(
        task: Runnable,
        millis: Long
    ) {
        Log.d(TAG, "Scheduling next token refresh in {$millis} milliseconds")

        try {
            future = executorService.schedule(task, millis, TimeUnit.MILLISECONDS)
        } catch (e: UnsupportedOperationException) {
            // Cannot support task scheduling in the current runtime.
            Log.d(TAG, "Failed to schedule token refresh event $e")
        }
    }

    private fun cancelPrevious() {
        if (future != null) {
            future?.cancel(true)
        }
    }

    /**
     * Execute the token refresh.
     */
    private fun scheduleRefresh(millis: Long) {
        cancelPrevious()
        scheduleNext(Runnable {
            Log.d(TAG, "Perform refresh_token")
            runCatching {
                if (listener?.onShouldRefreshToken() ?: false) {
                    refreshToken()
                } else {
                    throw IllegalStateException("refresh_token is denied to the app")
                }
            }.onSuccess {
                // nothing to do
            }.onFailure {
                scheduleRefresh(MIN_REFRESH_MILLIS)
            }
        }, millis)
    }

    /**
     * Execute the token refresh.
     */
    private fun refreshToken() {
        val refreshToken = authClient.getRefreshToken()
        if (refreshToken.isEmpty()) {
            /** anonymous **/
            authClient.loginAnonymously(object : NuguOAuthInterface.OnLoginListener {
                override fun onSuccess(credentials: Credentials) {
                    listener?.onCredentialsChanged(credentials)
                    scheduleRefresh(nextInterval())
                }

                override fun onError(error: NuguOAuthError) {
                    listener?.onRefreshTokenError(error)
                }
            })
        } else {
            /** tid  **/
            authClient.loginSilentlyWithTid(refreshToken, object : NuguOAuthInterface.OnLoginListener {
                override fun onSuccess(credentials: Credentials) {
                    listener?.onCredentialsChanged(credentials)
                    scheduleRefresh(nextInterval())
                }

                override fun onError(error: NuguOAuthError) {
                    listener?.onRefreshTokenError(error)
                }
            })
        }
    }
}