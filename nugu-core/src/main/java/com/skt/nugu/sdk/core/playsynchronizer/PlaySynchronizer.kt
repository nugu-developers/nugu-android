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
package com.skt.nugu.sdk.core.playsynchronizer

import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PlaySynchronizer : PlaySynchronizerInterface {
    companion object {
        private const val TAG = "PlaySynchronizer"
    }

    private val lock = ReentrantLock()

    /**
     * waiting start sync object : the sync object which is prepared
     * should release sync object : the sync object which is started
     */
    private data class ContextInfo(
        private val prepared: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet(),
        private val started: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet()
    ) {
        fun prepare(obj: PlaySynchronizerInterface.SynchronizeObject) {
            prepared.add(obj)
        }

        fun start(syncObj: PlaySynchronizerInterface.SynchronizeObject): Boolean {
            return if(prepared.remove(syncObj)) {
                started.add(syncObj)
                true
            } else {
                false
            }
        }

        fun cancel(syncObj: PlaySynchronizerInterface.SynchronizeObject): Boolean {
            val preparedRemoved = prepared.remove(syncObj)
            val startedRemoved = started.remove(syncObj)

            prepared.filter {
                it.getDialogRequestId() == syncObj.getDialogRequestId()
            }.forEach {
                it.requestReleaseSync(true)
            }

            started.filter {
                it.getDialogRequestId() == syncObj.getDialogRequestId()
            }.forEach {
                it.requestReleaseSync(true)
            }

            return preparedRemoved || startedRemoved
        }

        fun finish(syncObj: PlaySynchronizerInterface.SynchronizeObject, doSync: Boolean = true): Boolean {
            val preparedRemoved = prepared.remove(syncObj)
            val startedRemoved = started.remove(syncObj)

            if(doSync) {
                val playServiceId = syncObj.getPlayServiceId()
                val dialogRequestId = syncObj.getDialogRequestId()

                val existPrepared = prepared.any() {
                    if(playServiceId.isNullOrBlank()) {
                        it.getDialogRequestId() == dialogRequestId
                    } else {
                        it.getPlayServiceId() == playServiceId || it.getDialogRequestId() == dialogRequestId
                    }
                }

                Logger.d(TAG, "[finish] existPrepared: $existPrepared")
                if (!existPrepared) {
                    val filteredStarted = started.filter {
                        if(playServiceId.isNullOrBlank()) {
                            it.getDialogRequestId() == dialogRequestId
                        } else {
                            it.getPlayServiceId() == playServiceId || it.getDialogRequestId() == dialogRequestId
                        }
                    }

                    if(filteredStarted.size == 1) {
                        filteredStarted.forEach {
                            it.requestReleaseSync(false)
                        }
                    }
                }
            }

            return preparedRemoved || startedRemoved
        }
    }

    private val syncContext = ContextInfo()

    override fun prepareSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        lock.withLock {
            Logger.d(
                TAG,
                "[prepareSync] dialogRequestId: ${synchronizeObject.getDialogRequestId()}, synchronizeObject: $synchronizeObject"
            )

            syncContext.prepare(synchronizeObject)

            Logger.d(TAG, "[prepareSync] syncContext: $syncContext")
        }
    }

    override fun startSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        lock.withLock {
            val dialogRequestId = synchronizeObject.getDialogRequestId()
            Logger.d(TAG, "[startSync] dialogRequestId: $dialogRequestId, listener: $listener")

            if(syncContext.start(synchronizeObject)) {
                listener?.onGranted()
                Logger.d(TAG, "[startSync] granted.")
            } else {
                listener?.onDenied()
                Logger.w(TAG, "[startSync] denied: prepareSync not called.")
            }
        }
    }

    override fun releaseSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        releaseSyncInternal(synchronizeObject, listener, false)
    }

    override fun releaseSyncImmediately(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        releaseSyncInternal(synchronizeObject, listener, true)
    }

    override fun releaseWithoutSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        lock.withLock {
            syncContext.finish(synchronizeObject, false)
            Logger.d(TAG, "[releaseWithoutSync] syncContext: $syncContext")
        }
    }

    private fun releaseSyncInternal(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?,
        immediate: Boolean
    ) {
        lock.withLock {
            Logger.d(
                TAG,
                "[releaseSyncInternal] dialogRequestId: ${synchronizeObject.getDialogRequestId()} ,object: $synchronizeObject, listener: $listener, immediate: $immediate"
            )

            val released = if(immediate) {
                syncContext.cancel(synchronizeObject)
            } else {
                syncContext.finish(synchronizeObject)
            }

            if(released) {
                listener?.onGranted()
            } else {
                listener?.onDenied()
            }

            Logger.d(TAG, "[releaseSyncInternal] syncContext: $syncContext")
        }
    }
}
