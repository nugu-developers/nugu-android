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

package com.skt.nugu.sdk.agent.dialog

import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*

class FocusHolderManagerImpl(
    private val transitionDelayForIdleState: Long
) : FocusHolderManager {
    companion object {
        private const val TAG = "FocusHolderManager"
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val listeners = CopyOnWriteArraySet<FocusHolderManager.OnStateChangeListener>()

    private val holders = CopyOnWriteArraySet<FocusHolderManager.FocusHolder>()

    private var state = FocusHolderManager.State.UNHOLD

    private val tryEnterUnholdStateScheduler = ScheduledThreadPoolExecutor(1)
    private var tryEnterUnholdStateRunnableFuture: ScheduledFuture<*>? = null
    private val tryEnterUnholdStateRunnable: Runnable = Runnable {
        executor.submit {
            if (state == FocusHolderManager.State.HOLD && holders.isEmpty()) {
                Logger.d(TAG, "[tryEnterUnholdStateRunnable] unhold")
                state = FocusHolderManager.State.UNHOLD
                listeners.forEach {
                    it.onStateChanged(FocusHolderManager.State.UNHOLD)
                }
            }
        }
    }

    override fun addOnStateChangeListener(listener: FocusHolderManager.OnStateChangeListener) {
        listeners.add(listener)
    }

    override fun removeOnStateChangeListener(listener: FocusHolderManager.OnStateChangeListener) {
        listeners.remove(listener)
    }

    override fun request(holder: FocusHolderManager.FocusHolder) {
        holders.add(holder)

        executor.submit {
            Logger.d(TAG, "[request] holder: $holder, state: $state, size: ${holders.size}")
            if (state == FocusHolderManager.State.UNHOLD) {
                state = FocusHolderManager.State.HOLD
                listeners.forEach {
                    it.onStateChanged(FocusHolderManager.State.HOLD)
                }
            }
        }
    }

    override fun abandon(holder: FocusHolderManager.FocusHolder) {
        holders.remove(holder)

        tryEnterUnholdStateRunnableFuture?.cancel(true)
        tryEnterUnholdStateRunnableFuture = tryEnterUnholdStateScheduler.schedule(
            tryEnterUnholdStateRunnable,
            transitionDelayForIdleState,
            TimeUnit.MILLISECONDS
        )
        Logger.d(TAG, "[abandon] holder: $holder, state: $state, size: ${holders.size}")
    }
}