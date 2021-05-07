/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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

import io.grpc.Status
import junit.framework.TestCase
import org.junit.Test
import java.util.concurrent.CountDownLatch

class BackOffTest : TestCase() {
    @Test
    fun testDurationShouldIncrease() {
        val backoff = BackOff.Builder(
            maxAttempts = 100,
            baseDelay = 100,
            maxBackoffTime = 1000 * 1000,
            enableJitter = false
        ).build()
        assertTrue(100L == backoff.duration())
        backoff.attempts = 1
        assertTrue(200L == backoff.duration())
        backoff.attempts = 2
        assertTrue(400L == backoff.duration())
        backoff.attempts = 3
        assertTrue(800L == backoff.duration())

        backoff.reset()
        assertTrue(100L == backoff.duration())
        backoff.attempts = 1
        assertTrue(200L == backoff.duration())
    }

    @Test
    fun testDurationOverflow() {
        val backoff = BackOff.Builder(
            maxAttempts = 100,
            baseDelay = 100,
            maxBackoffTime = 1000 * 1000
        ).build()
        // repeats to make it overflow
        for (attempt in 0..100) {
            val duration = backoff.duration()
            assertTrue(
                "$attempt : $duration < " + 1000 * 1000,
                duration <= 1000 * 1000
            )
            backoff.attempts++
        }
    }

    @Test
    fun testReset() {
        val latch = CountDownLatch(1)
        val backoff = BackOff.DEFAULT()
        backoff.awaitRetry(Status.Code.DEADLINE_EXCEEDED, object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                assertEquals(BackOff.BackoffError.ScheduleCancelled, error)
                latch.countDown()
            }

            override fun onRetry(retriesAttempted: Int) {
                latch.countDown()
            }
        })
        backoff.reset()
        latch.await()
    }

    @Test
    fun testAwaitRetry() {
        var attempts = 0
        val cycles = Status.Code.values().size
        val backoff = BackOff.Builder(
            maxAttempts = cycles,
            baseDelay = 10,
            maxBackoffTime = 100
        ).build()

        Status.Code.values().forEach { code ->
            val latch = CountDownLatch(1)
            backoff.awaitRetry(code, observer = object : BackOff.Observer {
                override fun onError(error: BackOff.BackoffError) {
                    latch.countDown()
                    attempts++
                }

                override fun onRetry(retriesAttempted: Int) {
                    latch.countDown()
                    attempts++
                }
            })
            latch.await()
        }
        assertEquals(cycles, attempts)
    }

    @Test
    fun testNoRetryableStatusCode() {
        val backoff = BackOff.Builder(
            maxAttempts = 3,
            baseDelay = 10,
            maxBackoffTime = 100
        ).build()

        val latch = CountDownLatch(1)
        backoff.awaitRetry(Status.Code.UNAUTHENTICATED, observer = object : BackOff.Observer {
            override fun onError(error: BackOff.BackoffError) {
                assertEquals(BackOff.BackoffError.NoMoreRetryableCode, error)
                latch.countDown()
            }

            override fun onRetry(retriesAttempted: Int) {
                latch.countDown()
            }
        })
        latch.await()
    }

    @Test
    fun testMaxAttemptExceed() {
        val maxAttempts = 3
        var attempts = 0
        val backoff = BackOff.Builder(
            maxAttempts = maxAttempts,
            baseDelay = 10,
            maxBackoffTime = 100
        ).build()
        for (attempt in 0..3) {
            val latch = CountDownLatch(1)
            backoff.awaitRetry(Status.Code.DEADLINE_EXCEEDED, object : BackOff.Observer {
                override fun onError(error: BackOff.BackoffError) {
                    assertEquals(BackOff.BackoffError.MaxAttemptExceed, error)
                    latch.countDown()
                }

                override fun onRetry(retriesAttempted: Int) {
                    attempts++
                    latch.countDown()
                }
            })
            latch.await()
        }
        assertEquals(maxAttempts, backoff.attempts)
        assertEquals(maxAttempts, attempts)
    }
}