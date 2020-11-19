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
package com.skt.nugu.sdk.client.port.transport.http2.utils

import com.skt.nugu.sdk.client.port.transport.http2.Status
import com.skt.nugu.sdk.core.utils.Logger
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
                    maxBackoffTime = DEFAULT_MAX_BACKOFF_IN_MILLISECONDS
                )
            )
    }

    enum class BackoffError {
        MaxAttemptExceed,
        NoRetryableException
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
    private var scheduledFuture: ScheduledFuture<*>? = null


    /** The current retry count.  */
    private var attempts: Int = 0
    private var baseDelay: Long
    private var maxBackoffTime: Long
    private var waitTime: Long
    /** The maximum number of attempts.  */
    private var maxAttempts: Int

    init {
        this.maxAttempts = builder.maxAttempts
        this.maxBackoffTime = builder.maxBackoffTime
        this.baseDelay = builder.baseDelay
        this.waitTime = this.baseDelay
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
    private fun duration(): Long {
        // Exponential backoff
        val temp = Math.min(maxBackoffTime.toDouble(), baseDelay *  Math.pow(2.0, attempts.toDouble()) )
        // Full Jitter
        val sleep =  temp.toLong() / 2 + betweenRandom(baseDelay, temp.toLong() / 2)
        // Decorrelated Jitter
        return Math.min(maxBackoffTime, betweenRandom(baseDelay, sleep * 3)).apply { waitTime = this }
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
        synchronized(this) {
            this.waitTime = baseDelay
            this.attempts = 0
            this.scheduledFuture?.cancel(true)
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
    private fun isRetryableServiceException(code: Status.Code): Boolean {
        return when(code) {
            Status.Code.OK,
            Status.Code.PERMISSION_DENIED,
            Status.Code.UNAUTHENTICATED -> false
            else -> true
        }
    }

    /**
     * Wait for Retry
     * @param The Status Code
     * @param observer [Observer]
     *
     */
    fun awaitRetry(code: Status.Code, observer: Observer) {
        synchronized(this) {
            if (scheduledFuture?.isDone == false) {
                return
            }

            if (!isAttemptExceed()) {
                observer.onError(BackoffError.MaxAttemptExceed)
                return
            }

            // Increase attempts count
            attempts++

            if (!isRetryableServiceException(code)) {
                observer.onError(BackoffError.NoRetryableException)
                return
            }

            val duration = duration()
            scheduledFuture = executorService.schedule({
                // prevent future invocations.
                scheduledFuture?.cancel(false)
                // Retry done
                observer.onRetry(attempts)
            }, duration, TimeUnit.MILLISECONDS)

            Logger.w(
                TAG,
                String.format("will wait ${waitTime}ms before reconnect attempt ${attempts} / ${maxAttempts}")
            )
        }
    }


    /**
     * Builder class for [BackOff]
     */
    data class Builder(
        /** The maximum number of attempts.  */
        internal val maxAttempts : Int,
        internal val baseDelay: Long = DEFAULT_BASE_DELAY,
        internal val maxBackoffTime: Long = DEFAULT_MAX_BACKOFF_IN_MILLISECONDS
    ) {
        fun build() =
            BackOff(this)
    }
}