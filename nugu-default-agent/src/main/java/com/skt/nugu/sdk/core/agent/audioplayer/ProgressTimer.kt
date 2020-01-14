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
package com.skt.nugu.sdk.core.agent.audioplayer

import com.skt.nugu.sdk.core.utils.Logger

class ProgressTimer {
    interface ProgressListener {
        fun onProgressReportDelay(request: Long, actual: Long)
        fun onProgressReportInterval(request: Long, actual: Long)
    }

    interface ProgressProvider {
        fun getProgress(): Long
    }

    companion object {
        private const val TAG = "ProgressTimer"
        const val NO_DELAY = 0L
        const val NO_INTERVAL = 0L
    }

    private var timerThread: TimerThread? = null
    private var delay: Long = NO_DELAY
    private var interval: Long = NO_INTERVAL
    private var onProgressListener: ProgressListener? = null
    private var progressProvider: ProgressProvider? = null

    private var lastReportedProgress: Long = 0L

    fun init(delay: Long, interval: Long, progressListener: ProgressListener, progressProvider: ProgressProvider) {
        Logger.d(TAG, "[init] delay: $delay, interval: $interval, onProgressListener: $progressListener, progressProvider: $progressProvider")
        cancelTimer()

        this.delay = delay
        this.interval = interval
        this.onProgressListener = progressListener
        this.progressProvider = progressProvider

        lastReportedProgress = 0L

        if (NO_DELAY == delay && NO_INTERVAL == interval) {
            Logger.d(TAG, "[init] no timer (no delay and no interval)")
            return
        }
    }

    private fun startTimer() {
        Logger.d(TAG, "[startTimer] onProgressListener: $onProgressListener")
        val listener = onProgressListener
        val provider = progressProvider

        if(listener ==  null || provider == null) {
            return
        }

        timerThread = TimerThread(listener, provider).apply {
            start()
        }
    }

    private fun cancelTimer() {
        Logger.d(TAG, "[cancelTimer] timer: $timerThread")
        timerThread?.requestCancel()
    }

    fun start() {
        Logger.d(TAG, "[start]")
        startTimer()
    }

    fun pause() {
        Logger.d(TAG, "[pause]")
        cancelTimer()
    }

    fun resume() {
        Logger.d(TAG, "[resume]")
        startTimer()
    }

    fun stop() {
        Logger.d(TAG, "[stop]")
        cancelTimer()

        delay = NO_DELAY
        interval = NO_INTERVAL
        onProgressListener = null
        progressProvider = null
        lastReportedProgress = 0L
    }

    private inner class TimerThread(
//        private val delay: Long,
//        private val interval: Long,
        private val progressListener: ProgressListener,
        private val progressProvider: ProgressProvider
    ) : Thread() {
        private var isCancelling = false

        override fun run() {
            super.run()

            while(!isCancelling) {
                try {
                    val prevReportedProgress = lastReportedProgress
                    lastReportedProgress = progressProvider.getProgress()

                    if (delay != NO_DELAY && !isCancelling) {
                        if(delay in (prevReportedProgress + 1)..lastReportedProgress) {
                            progressListener.onProgressReportDelay(delay, lastReportedProgress)
                        }
                    }

                    var intervalReport = 0L
                    if (interval != NO_INTERVAL && !isCancelling) {
                        intervalReport = interval * (prevReportedProgress / interval + 1)
                        if(intervalReport in (prevReportedProgress + 1)..lastReportedProgress) {
                            progressListener.onProgressReportInterval(intervalReport, lastReportedProgress)
                        }
                    }

                    sleep(100L)
                } catch (e: Exception) {
                }
            }
        }

        fun requestCancel() {
            isCancelling = true
        }
    }
}