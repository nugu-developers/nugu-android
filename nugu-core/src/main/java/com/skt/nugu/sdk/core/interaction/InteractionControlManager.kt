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

package com.skt.nugu.sdk.core.interaction

import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControl
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlManagerInterface
import com.skt.nugu.sdk.core.interfaces.interaction.InteractionControlMode
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InteractionControlManager: InteractionControlManagerInterface {
    companion object {
        private const val TAG = "InteractionControlManager"
    }
    private val lock = ReentrantLock()
    private val refs = HashSet<InteractionControl>()
    private val listeners = LinkedHashSet<InteractionControlManagerInterface.Listener>()

    override fun start(interactionControl: InteractionControl) {
        lock.withLock {
            Logger.d(TAG, "[start] $interactionControl / $refs, $listeners")
            if(interactionControl.getMode() != InteractionControlMode.MULTI_TURN) {
                return@withLock
            }

            if(refs.add(interactionControl)) {
                if(refs.size == 1) {
                    listeners.forEach {
                        it.onMultiturnStateChanged(true)
                    }
                }
            }
        }
    }

    override fun finish(interactionControl: InteractionControl) {
        lock.withLock {
            Logger.d(TAG, "[finish] $interactionControl / $refs, $listeners")
            if(refs.remove(interactionControl)) {
                if(refs.isEmpty()) {
                    listeners.forEach {
                        it.onMultiturnStateChanged(false)
                    }
                }
            }
        }
    }

    override fun addListener(listener: InteractionControlManagerInterface.Listener) {
        lock.withLock {
            Logger.d(TAG, "[addListener] $listener / $refs, $listeners")
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: InteractionControlManagerInterface.Listener) {
        lock.withLock {
            Logger.d(TAG, "[removeListener] $listener / $refs, $listeners")
            listeners.remove(listener)
        }
    }
}