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
    }

    interface Observer {
        fun onError(reason: String)
        fun onComplete(attempts: Int)
    }

    private var state = State.RETRY_INIT

    private val executorService: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }
    private var scheduledFuture: ScheduledFuture<*>? = null

    /** The default and maximum socket timeout in milliseconds  */
    private var seed: Long = 1000
    private var max: Long = 1000 * 60
    private var waitTime: Long = seed

    private val RETRYABLE_STATUS_CODES: MutableSet<Status.Code> = HashSet(1)

    init {
        RETRYABLE_STATUS_CODES.add(Status.Code.UNAVAILABLE)
        RETRYABLE_STATUS_CODES.add(Status.Code.ABORTED)
        RETRYABLE_STATUS_CODES.add(Status.Code.INTERNAL)
    }

    internal enum class State {
        RETRY_INIT,
        RETRY_IN_PROGRESS,
        RETRY_FAILED,
        RETRY_COMPLETE,
    }

    /** The current retry count.  */
    @Volatile private var attempts: Int = 0

    /** The maximum number of attempts.  */
    var maxAttempts: Int

    init {
        this.maxAttempts = builder.maxAttempts
    }
    /**
     * Returns the duration (milliseconds).
     * @see https://aws.amazon.com/ko/blogs/architecture/exponential-backoff-and-jitter/
     * sleep = min(cap, random_between(base, sleep * 3))
     */
    fun duration(): Long {
        waitTime = Math.min(max, betweenRandom(seed, waitTime * 3))
        return waitTime
    }

    /**
     * Difference between Random
     * @param i is min
     * @param j is max
     */
    private fun betweenRandom(i: Long, j: Long): Long {
        return i + Math.floor(Math.random() * (j - i + 1)).toLong()
    }

    /**
     *  Explicitly clean up
     */
    fun reset() {
        this.waitTime = seed
        this.attempts = 0
        this.state = State.RETRY_INIT

        this.scheduledFuture?.cancel(true)
    }

    /**
     * Increase attempts count
     */
    private fun attempt(): Int {
        return ++attempts
    }

    /**
     * Indicates whether a retry should be performed or not.
     * @param The attempt number.
     * @return True to retry, false to not.
     */
    private fun shouldRetry(attempt : Int): Boolean {
        return attempt < maxAttempts
    }

    /**
     * Indicates whether a retry should be performed or not.
     * @param The retryable Status Code
     * @return True to retry, false to not.
     */
    private fun shouldRetry(code: Status.Code): Boolean {
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
        if (!shouldRetry(attempts)) {
            state = State.RETRY_FAILED
            observer.onError("reached maximum retry attempts")
            return
        }
        if (!shouldRetry(code)) {
            state = State.RETRY_FAILED
            observer.onError("Status code that can't be retried")
            return
        }

        state = State.RETRY_IN_PROGRESS

        attempt()
        Logger.w(TAG, String.format("will wait ${waitTime}ms before reconnect attempt ${attempts} / ${maxAttempts}"))
        scheduledFuture = executorService.schedule({
            state = State.RETRY_COMPLETE
            observer.onComplete(attempts)
        }, duration(), TimeUnit.MILLISECONDS)
    }


    /**
     * Builder class for [BackOff]
     */
    data class Builder(
        /** The maximum number of attempts.  */
        internal var maxAttempts: Int = Int.MAX_VALUE
    ) {
        fun build() = BackOff(this)
    }
}