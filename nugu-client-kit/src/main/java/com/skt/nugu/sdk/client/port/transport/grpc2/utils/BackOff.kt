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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.google.common.annotations.VisibleForTesting
import com.skt.nugu.sdk.core.utils.Logger
import io.grpc.Status
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Implementation of BackOff that increases the back off period for each retry attempt.
 */
class BackOff private constructor(builder: Builder) {
    companion object {
        private const val TAG = "BackOff"

        /**
         * Default maximum back-off time before retrying a request
         */
        val DEFAULT_MAX_BACKOFF_IN_MILLISECONDS : Long = 1000 * 300

        /**
         * Default base sleep time (milliseconds) for non-throttled exceptions.
         */
        private val DEFAULT_BASE_DELAY : Long = 1000

        /**
         * Default constructor of Credentials
         **/
        fun DEFAULT() =
            BackOff(
                Builder(
                    maxAttempts = 3,
                    baseDelay = DEFAULT_BASE_DELAY,
                    maxBackoffTime = DEFAULT_MAX_BACKOFF_IN_MILLISECONDS,
                    enableJitter = true
                )
            )
    }

    enum class BackoffError {
        MaxAttemptExceed,
        NoMoreRetryableCode,
        ScheduleCancelled,
        AlreadyStarted,
        AlreadyShutdown
    }

    interface Observer {
        fun onError(error: BackoffError)
        /**
         *  Determine whether a request should or should not be retried.
         * @param attempts The number of times the current request has been attempted.**/
        fun onRetry(retriesAttempted: Int)
    }

    private val executorService: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }
    private var scheduledFuture: ObservableScheduledFuture? = null

    /** The current retry count.  */
    @VisibleForTesting
    var attempts: Int = 0
    private var baseDelay: Long
    private var maxBackoffTime: Long
    private var waitTime: Long
    private var enableJitter: Boolean
    /** The maximum number of attempts.  */
    private var maxAttempts: Int

    init {
        this.maxAttempts = builder.maxAttempts
        this.maxBackoffTime = builder.maxBackoffTime
        this.baseDelay = builder.baseDelay.also {
            waitTime = it
        }
        this.enableJitter = builder.enableJitter
    }
    /**
     * Returns the duration (milliseconds).
     * @see https://aws.amazon.com/ko/blogs/architecture/exponential-backoff-and-jitter/
     *
     * implement "Decorrelated Jitter"
     * temp = min(cap, base * 2 ** attempt)
     * sleep = temp / 2 + random_between(0, temp / 2)
     * sleep = min(cap, random_between(base, sleep * 3))
     */
    @VisibleForTesting
    fun duration(): Long {
        // Exponential backoff
        val exponential = Math.min(maxBackoffTime.toDouble(), baseDelay *  Math.pow(2.0, attempts.toDouble()) )
        if(!enableJitter) {
            return exponential.toLong()
        }
        // Full Jitter
        val fullJitter =  exponential.toLong() / 2 + betweenRandom(baseDelay, exponential.toLong() / 2)
        // Decorrelated Jitter
        return Math.min(maxBackoffTime, betweenRandom(baseDelay, fullJitter * 3)).also { waitTime = it }
    }

    /**
     * Difference between Random
     * @param i is min
     * @param j is maxBackoffTime
     */
    private fun betweenRandom(i: Long, j: Long): Long {
        return i + Math.floor(Math.random() * (j - i + 1)).toLong()
    }

    /**
     *  clean up
     */
    fun reset() {
        Logger.d(TAG, "[reset] called")
        synchronized(this) {
            this.waitTime = baseDelay
            this.attempts = 0
            scheduledFuture?.cancel(true)
            scheduledFuture = null
        }
    }

    /**
     * Indicates whether a retry should be performed or not.
     * @return True to retry, false to not.
     */
    private fun isAttemptExceed(): Boolean {
        return attempts < maxAttempts
    }
    /**
     * Indicates whether a retry should be performed or not.
     * @param The exception status code
     * @return True to retry, false to not.
     */
    private fun isRetryableStatusCode(code: Status.Code): Boolean {
        return when(code) {
            Status.Code.UNAUTHENTICATED -> false
            else -> true
        }
    }

    /**
     * Wait for Retry
     * @param The Status Code
     * @param observer [Observer]
     */
    fun awaitRetry(code: Status.Code, observer: Observer) {
        synchronized(this) {
            if (executorService.isShutdown) {
                Logger.w(TAG, "already shutdown")
                observer.onError(BackoffError.AlreadyShutdown)
                return
            }
            if (scheduledFuture?.isDone() == false) {
                Logger.w(TAG, "already started")
                observer.onError(BackoffError.AlreadyStarted)
                return
            }
            if (!isAttemptExceed()) {
                observer.onError(BackoffError.MaxAttemptExceed)
                return
            }
            if (!isRetryableStatusCode(code)) {
                observer.onError(BackoffError.NoMoreRetryableCode)
                return
            }

            scheduledFuture = ObservableScheduledFuture(observer,
                executorService.schedule({
                    Logger.d(TAG, "[Scheduled] start")
                    // prevent future invocations.
                    synchronized(this) {
                        scheduledFuture?.cancel(false)
                        scheduledFuture = null
                        // Retry done
                        observer.onRetry(attempts)
                    }
                }, duration(), TimeUnit.MILLISECONDS)
            )

            // Increase attempts count
            attempts++

            Logger.w(
                TAG,
                String.format("will wait ${waitTime}ms before reconnect attempt $attempts / $maxAttempts")
            )
        }
    }

    fun shutdown() {
        synchronized(this) {
            scheduledFuture?.cancel(true)
            scheduledFuture = null
            executorService.shutdown()
        }
    }


    /**
     * Builder class for [BackOff]
     */
    data class Builder(
        /** The maximum number of attempts.  */
        internal val maxAttempts : Int,
        internal val baseDelay: Long = DEFAULT_BASE_DELAY,
        internal val maxBackoffTime: Long = DEFAULT_MAX_BACKOFF_IN_MILLISECONDS,
        internal val enableJitter : Boolean = true
    ) {
        fun build() =
            BackOff(this)
    }

    internal class ObservableScheduledFuture(private val observerDelegate: Observer, private val scheduledDelegate: ScheduledFuture<*>) {
        fun cancel(notify: Boolean) {
            scheduledDelegate.cancel(true)
            if(notify) {
                observerDelegate.onError(BackoffError.ScheduleCancelled)
            }
        }
        fun isDone() = scheduledDelegate.isDone
    }
}