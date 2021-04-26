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

class SessionManager(private val inactiveTimeoutInMillis: Long = DEFAULT_INACTIVE_TIMEOUT) : SessionManagerInterface {
    companion object {
        private const val TAG = "SessionManager"
        private const val DEFAULT_INACTIVE_TIMEOUT = 60 * 1000L // millis
    }

    private val lock = ReentrantLock()

    private val allSessions = LinkedHashMap<String, SessionManagerInterface.Session>()
    private val sessionSetTimeMap = HashMap<String, Long>()
    private val activeSessionsMap = HashMap<String, HashSet<SessionManagerInterface.Requester>>()
    private val listeners = LinkedHashSet<SessionManagerInterface.Listener>()

    private val timeoutFutureMap = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun set(key: String, session: SessionManagerInterface.Session) {
        lock.withLock {
            Logger.d(TAG, "[set] key: $key, session: $session")
            allSessions[key] = session
            sessionSetTimeMap[key] = System.currentTimeMillis()

            // if activate called before set, do not schedule.
            if(activeSessionsMap.containsKey(key)) {
                listeners.forEach {
                    it.onSessionActivated(key, session)
                }
            } else {
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
                sessionSetTimeMap.remove(key)
                timeoutFutureMap.remove(key)
            }
        }, inactiveTimeoutInMillis, TimeUnit.MILLISECONDS)
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

                val session = allSessions[key]
                if(session != null && syncSet.size == 1) {
                    listeners.forEach {
                        it.onSessionActivated(key, session)
                    }
                }
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
                Logger.d(TAG, "[deactivate] key: $key, requester: $requester => deactivated")
                if(syncSet.isEmpty()) {
                    activeSessionsMap.remove(key)
                    scheduleTimeout(key)
                    Logger.d(TAG, "[deactivate] key: $key, session deactivated")

                    val session = allSessions[key]
                    if(session != null) {
                        listeners.forEach {
                            it.onSessionDeactivated(key, session)
                        }
                    }
                }
            } else {
                Logger.d(TAG, "[deactivate] key: $key, requester: $requester => already deactivated")
            }
        }
    }

    override fun getActiveSessions(): Map<String, SessionManagerInterface.Session> {
        return lock.withLock {
            val actives = LinkedHashMap<String, SessionManagerInterface.Session>().apply {
                activeSessionsMap.forEach {
                    allSessions[it.key]?.let { session ->
                        put(it.key, session)
                    }
                }
            }

            val shouldBeRemoved = HashSet<String>()

            // remove duplicated playServiceId
            HashMap<String, String>().apply {
                actives.forEach {
                    val current = get(it.value.playServiceId)
                    val currentTime = sessionSetTimeMap[current]

                    val targetTime = sessionSetTimeMap[it.key]
                    if(current != null && currentTime != null && targetTime != null) {
                        if(currentTime < targetTime) {
                            put(it.value.playServiceId, it.key)
                            shouldBeRemoved.add(current)
                        } else {
                            shouldBeRemoved.add(it.key)
                        }
                    } else {
                        put(it.value.playServiceId, it.key)
                    }
                }
            }

            shouldBeRemoved.forEach {
                actives.remove(it)
            }

            actives
        }
    }

    override fun addListener(listener: SessionManagerInterface.Listener) {
        lock.withLock {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: SessionManagerInterface.Listener) {
        lock.withLock {
            listeners.remove(listener)
        }
    }
}