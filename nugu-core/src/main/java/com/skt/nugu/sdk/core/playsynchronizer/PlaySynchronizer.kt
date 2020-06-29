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

import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
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
        val prepared: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet(),
        val started: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet()
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

            prepared.forEach {
                it.requestReleaseSync(true)
            }

            started.forEach {
                it.requestReleaseSync(true)
            }

            return preparedRemoved || startedRemoved
        }

        fun finish(syncObj: PlaySynchronizerInterface.SynchronizeObject, doSync: Boolean = true): Boolean {
            val preparedRemoved = prepared.remove(syncObj)
            val startedRemoved = started.remove(syncObj)

            if(doSync) {
                if(prepared.isEmpty()) {
                    started.forEach {
                        it.requestReleaseSync(false)
                    }
                }
            }

            return preparedRemoved || startedRemoved
        }

        fun isEmpty(): Boolean = prepared.isEmpty() && started.isEmpty()
    }

    private val syncContexts = HashMap<String, ContextInfo>()

    override fun prepareSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        lock.withLock {
            Logger.d(
                TAG,
                "[prepareSync] dialogRequestId: ${synchronizeObject.getDialogRequestId()}, synchronizeObject: $synchronizeObject"
            )
            var contextInfo = syncContexts[synchronizeObject.getDialogRequestId()]
            if (contextInfo == null) {
                contextInfo = ContextInfo()
                syncContexts[synchronizeObject.getDialogRequestId()] = contextInfo
            }
            contextInfo.prepare(synchronizeObject)

            Logger.d(TAG, "[prepareSync] syncContexts: ${syncContexts.size}")
        }
    }

    override fun startSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener
    ) {
        lock.withLock {
            val dialogRequestId = synchronizeObject.getDialogRequestId()
            Logger.d(TAG, "[startSync] dialogRequestId: $dialogRequestId, listener: $listener")

            if(syncContexts[dialogRequestId]?.start(synchronizeObject) == true) {
                listener.onGranted()
                Logger.d(TAG, "[startSync] granted.")
            } else {
                listener.onDenied()
                Logger.w(TAG, "[startSync] denied: prepareSync not called.")
            }
        }
    }

    override fun releaseSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener
    ) {
        releaseSyncInternal(synchronizeObject, listener, false)
    }

    override fun releaseSyncImmediately(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener
    ) {
        releaseSyncInternal(synchronizeObject, listener, true)
    }

    override fun releaseWithoutSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        lock.withLock {
            val dialogRequestId = synchronizeObject.getDialogRequestId()

            val contextInfo = syncContexts[dialogRequestId]
            if (contextInfo == null) {
                Logger.w(TAG, "[releaseWithoutSync] no context info for $dialogRequestId")
                return
            }

            contextInfo.finish(synchronizeObject, false)
            if(contextInfo.isEmpty()) {
                syncContexts.remove(dialogRequestId)
            }

            Logger.d(TAG, "[releaseWithoutSync] synContexts: $syncContexts")
        }
    }

    private fun releaseSyncInternal(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener,
        immediate: Boolean
    ) {
        lock.withLock {
            val dialogRequestId = synchronizeObject.getDialogRequestId()

            val contextInfo = syncContexts[dialogRequestId]
            if (contextInfo == null) {
                Logger.w(TAG, "[releaseSyncInternal] no context info for $dialogRequestId")
                listener.onDenied()
                return
            }

            Logger.d(
                TAG,
                "[releaseSyncInternal] dialogRequestId: $dialogRequestId ,object: $synchronizeObject, listener: $listener, immediate: $immediate"
            )

            val released = if(immediate) {
                contextInfo.cancel(synchronizeObject)
            } else {
                contextInfo.finish(synchronizeObject)
            }

            if(contextInfo.isEmpty()) {
                syncContexts.remove(dialogRequestId)
            }

            if(released) {
                listener.onGranted()
            } else {
                listener.onDenied()
            }

            Logger.d(TAG, "[releaseSyncInternal] syncContexts: $syncContexts")
        }
    }

    override fun existOtherSyncObject(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject): Boolean {
        val contextInfo = syncContexts[synchronizeObject.getDialogRequestId()] ?: return false

        if(contextInfo.prepared.firstOrNull {
            it != synchronizeObject
        } != null) {
            return true
        }

        if(contextInfo.started.firstOrNull {
                it != synchronizeObject
            } != null) {
            return true
        }

        return false
    }
}
