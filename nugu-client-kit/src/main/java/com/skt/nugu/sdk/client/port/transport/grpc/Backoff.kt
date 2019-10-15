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
package com.skt.nugu.sdk.client.port.transport.grpc

/**
 * Back-off policy when retrying an operation.
 */
internal class Backoff {
    /** The default and maximum socket timeout in milliseconds  */
    private var seed: Long = 1000
    private var max: Long = 1000 * 60
    private var waitTime: Long = seed

    /** The current retry count.  */
    @Volatile
    private var attempts: Int = 0
    /** The maximum number of attempts.  */
    var maxAttempts: Int = Int.MAX_VALUE
    /* ping timeout */
    var healthCheckTimeout: Long = 0
    /* ping interval */
    var retryDelay: Long = 0

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
    @Synchronized
    fun reset() {
        this.waitTime = seed
        this.attempts = 0
    }

    /**
     * Returns the attempts
     */
    @Synchronized
    fun getAttempts(): Int {
        return this.attempts
    }

    /**
     * Increase attempts count
     */
    @Synchronized
    fun attempt() {
        this.attempts ++
    }

    /**
     * Returns true if this policy has attempts remaining, false otherwise.
     */
    @Synchronized
    fun hasAttemptRemaining(): Boolean {
        return getAttempts() < maxAttempts
    }
}