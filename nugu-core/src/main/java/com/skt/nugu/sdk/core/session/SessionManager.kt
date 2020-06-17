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

package com.skt.nugu.sdk.core.session

import com.skt.nugu.sdk.core.interfaces.session.SessionManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SessionManager : SessionManagerInterface {
    companion object {
        private const val TAG = "SessionManager"
        private const val INACTIVE_TIMEOUT = 60L // sec
    }

    private val lock = ReentrantLock()

    private val allSessions = LinkedHashMap<String, SessionManagerInterface.Session>()
    private val activeSessionsMap = HashMap<String, HashSet<SessionManagerInterface.Requester>>()

    private val timeoutFutureMap = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun set(key: String, session: SessionManagerInterface.Session) {
        lock.withLock {
            Logger.d(TAG, "[set] key: $key, session: $session")
            allSessions[key] = session

            // if activate called before set, do not schedule.
            if(!activeSessionsMap.containsKey(key)) {
                scheduleTimeout(key)
            }
        }
    }

    private fun scheduleTimeout(key: String) {
        Logger.d(TAG, "[scheduleTimeout] key: $key")
        timeoutFutureMap.remove(key)?.cancel(true)
        timeoutFutureMap[key] = timeoutScheduler.schedule({
            lock.withLock {
                Logger.d(TAG, "[scheduleTimeout] occur timeout key: $key")
                allSessions.remove(key)
                timeoutFutureMap.remove(key)
            }
        }, INACTIVE_TIMEOUT, TimeUnit.SECONDS)
    }

    override fun activate(key: String, requester: SessionManagerInterface.Requester) {
        lock.withLock {
            var syncSet = activeSessionsMap[key]
            if(syncSet == null) {
                syncSet = HashSet()
                activeSessionsMap[key] = syncSet
            }

            if(syncSet.add(requester)) {
                timeoutFutureMap.remove(key)?.cancel(true)
                Logger.d(TAG, "[activate] key: $key, requester: $requester => activated")
            } else {
                Logger.d(TAG, "[activate] key: $key, requester: $requester => already activated")
            }
        }
    }

    override fun deactivate(key: String, requester: SessionManagerInterface.Requester) {
        lock.withLock {
            val syncSet = activeSessionsMap[key]
            if(syncSet == null) {
                Logger.d(TAG, "[deactivate] key: $key, requester: $requester => no active session")
                return@withLock
            }

            if(syncSet.remove(requester)) {
                if(syncSet.isEmpty()) {
                    activeSessionsMap.remove(key)
                    scheduleTimeout(key)
                }
                Logger.d(TAG, "[deactivate] key: $key, requester: $requester => deactivated")
            } else {
                Logger.d(TAG, "[deactivate] key: $key, requester: $requester => already deactivated")
            }
        }
    }

    override fun getActiveSessions(): List<SessionManagerInterface.Session> {
        return lock.withLock {
            ArrayList<SessionManagerInterface.Session>().apply {
                activeSessionsMap.forEach {
                    allSessions[it.key]?.let { session ->
                        add(session)
                    }
                }
            }
        }
    }
}