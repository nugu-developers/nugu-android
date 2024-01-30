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
package com.skt.nugu.sdk.agent.audioplayer

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

    internal data class ProgressReportParamAndProgress(
        val delay: Long,
        val interval: Long,
        val progressListener: ProgressListener,
        val progressProvider: ProgressProvider
    ) {
        var lastReportedProgress: Long = 0L
        var isDelayReported = false
    }

    private var timerThread: TimerThread? = null

    private var currentProgressReportParamAndProgress: ProgressReportParamAndProgress? = null

    fun init(delay: Long, interval: Long, progressListener: ProgressListener, progressProvider: ProgressProvider) {
        Logger.d(TAG, "[init] delay: $delay, interval: $interval, onProgressListener: $progressListener, progressProvider: $progressProvider")
        cancelTimer()

        currentProgressReportParamAndProgress = ProgressReportParamAndProgress(delay, interval, progressListener, progressProvider)

        if (NO_DELAY == delay && NO_INTERVAL == interval) {
            Logger.d(TAG, "[init] no timer (no delay and no interval)")
            return
        }
    }

    private fun startTimer() {
        val copyCurrentProgressReportParam = currentProgressReportParamAndProgress
        Logger.d(TAG, "[startTimer] ProgressReportParam: $copyCurrentProgressReportParam")
        if(copyCurrentProgressReportParam == null) {
            return
        }

        timerThread = TimerThread(copyCurrentProgressReportParam).apply {
            start()
        }
    }

    private fun cancelTimer() {
        Logger.d(TAG, "[cancelTimer] ProgressReportParam: $currentProgressReportParamAndProgress, timer: $timerThread")
        timerThread?.requestCancel()
        timerThread = null
    }

    private fun finishTimer() {
        Logger.d(TAG, "[finishTimer] ProgressReportParam: $currentProgressReportParamAndProgress, timer: $timerThread")
        timerThread?.requestFinish()
        timerThread = null
    }

    fun start() {
        Logger.d(TAG, "[start]")
        startTimer()
    }

    fun finish() {
        Logger.d(TAG, "[finish]")
        finishTimer()
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
        currentProgressReportParamAndProgress = null
    }

    private inner class TimerThread(
        private val progressReportParamAndProgress: ProgressReportParamAndProgress
    ) : Thread() {
        private var isCancelling = false
        private var isFinishing = false

        override fun run() {
            super.run()

            while (!isCancelling) {
                with(progressReportParamAndProgress) {
                    try {
                        val prevReportedProgress = lastReportedProgress
                        lastReportedProgress = progressProvider.getProgress()

                        if (delay != NO_DELAY && !isCancelling && !isDelayReported) {
                            if (delay in (prevReportedProgress + 1)..lastReportedProgress) {
                                progressListener.onProgressReportDelay(delay, lastReportedProgress)
                                isDelayReported = true
                            }
                        }

                        val intervalReport: Long
                        if (interval != NO_INTERVAL && !isCancelling) {
                            intervalReport = interval * (prevReportedProgress / interval + 1)
                            if (intervalReport in (prevReportedProgress + 1)..lastReportedProgress) {
                                progressListener.onProgressReportInterval(
                                    intervalReport,
                                    lastReportedProgress
                                )
                            }
                        }

                        if (isFinishing) {
                            return
                        }

                        sleep(100L)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        fun requestCancel() {
            if(!isFinishing) {
                // if finish requested, do not cancel.
                isCancelling = true
            }
        }

        fun requestFinish() {
            isFinishing = true
        }
    }
}
