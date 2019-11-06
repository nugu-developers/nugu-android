package com.skt.nugu.sdk.client.port.transport.grpc.utils

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
        val DEFAULT_MAX_BACKOFF_IN_MILLISECONDS : Long = 1000 * 60

        /**
         * Default base sleep time (milliseconds) for non-throttled exceptions.
         */
        private val DEFAULT_BASE_DELAY : Long = 1000

        /**
         * Default constructor of Credentials
         **/
        fun DEFAULT() = BackOff(Builder(
                maxAttempts = Int.MAX_VALUE,
                baseDelay = DEFAULT_BASE_DELAY,
                maxBackoffTime = DEFAULT_MAX_BACKOFF_IN_MILLISECONDS
            )
        )
    }

    interface Observer {
        fun onError(reason: String)
        /**
         *  Determine whether a request should or should not be retried.
         * @param attempts The number of times the current request has been attempted.**/
        fun onRetry(retriesAttempted: Int)
    }

    private var state = State.RETRY_INIT

    private val executorService: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }
    private var scheduledFuture: ScheduledFuture<*>? = null


    /** The current retry count.  */
    @Volatile private var attempts: Int = 0
    private var baseDelay: Long
    private var maxBackoffTime: Long
    private var waitTime: Long

    private val RETRYABLE_STATUS_CODES: MutableSet<Status.Code> = HashSet(5)

    init {
        RETRYABLE_STATUS_CODES.add(Status.Code.UNAVAILABLE)
        RETRYABLE_STATUS_CODES.add(Status.Code.ABORTED)
        RETRYABLE_STATUS_CODES.add(Status.Code.INTERNAL)
        RETRYABLE_STATUS_CODES.add(Status.Code.CANCELLED)
        RETRYABLE_STATUS_CODES.add(Status.Code.DEADLINE_EXCEEDED)
    }

    internal enum class State {
        RETRY_INIT,
        RETRY_IN_PROGRESS,
        RETRY_FAILED,
        RETRY_COMPLETE,
    }


    /** The maximum number of attempts.  */
    var maxAttempts: Int

    init {
        this.maxAttempts = builder.maxAttempts
        this.maxBackoffTime = builder.maxBackoffTime
        this.baseDelay = builder.baseDelay
        this.waitTime = this.baseDelay
    }
    /**
     * Returns the duration (milliseconds).
     * @see https://aws.amazon.com/ko/blogs/architecture/exponential-backoff-and-jitter/
     * sleep = min(cap, random_between(base, sleep * 3))
     */
    fun duration(): Long {
        waitTime = Math.min(maxBackoffTime, betweenRandom(baseDelay, waitTime * 3))
        return waitTime
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
     *  Explicitly clean up
     */
    fun reset() {
        this.waitTime = baseDelay
        this.attempts = 0
        this.state = State.RETRY_INIT
        this.scheduledFuture?.cancel(true)
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
        return RETRYABLE_STATUS_CODES.contains(code)
    }

    /**
     * Wait for Retry
     * @param The Status Code
     * @param observer [Observer]
     *
     */
    fun awaitRetry(code: Status.Code, observer: Observer) {
        if (state == State.RETRY_IN_PROGRESS) {
            return
        }
        state = State.RETRY_IN_PROGRESS

        if (!isAttemptExceed()) {
            state = State.RETRY_FAILED
            observer.onError("exceeded maxBackoffTime attempts")
            return
        }
        if (!isRetryableServiceException(code)) {
            state = State.RETRY_FAILED
            observer.onError("Status code($code) that can't be retried")
            return
        }

        // Increase attempts count
        attempts++


        val duration = duration()
        scheduledFuture = executorService.schedule({
            state = State.RETRY_COMPLETE
            // Retry done
            observer.onRetry(attempts)

        }, duration, TimeUnit.MILLISECONDS)
        Logger.w(TAG, String.format("will wait ${waitTime}ms before reconnect attempt ${attempts} / ${maxAttempts}"))
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
        fun build() = BackOff(this)
    }
}