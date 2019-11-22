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
import java.util.concurrent.Executors
import kotlin.collections.HashSet

class PlaySynchronizer : PlaySynchronizerInterface {
    companion object {
        private const val TAG = "PlaySynchronizer"
    }

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * waiting start sync object : the sync object which is prepared
     * should release sync object : the sync object which is started
     */
    private data class ContextInfo(
        val waitingStartSyncObjects: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet(),
        val shouldReleaseSyncObjects: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet()
    )

    private val syncContexts = HashMap<String, ContextInfo>()

    override fun prepareSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        executor.submit {
            Logger.d(
                TAG,
                "[prepareSync] dialogRequestId: ${synchronizeObject.getDialogRequestId()}, synchronizeObject: $synchronizeObject"
            )
            var contextInfo = syncContexts[synchronizeObject.getDialogRequestId()]
            if (contextInfo == null) {
                contextInfo = ContextInfo()
                syncContexts[synchronizeObject.getDialogRequestId()] = contextInfo
            }

            contextInfo.waitingStartSyncObjects.add(synchronizeObject)

            Logger.d(TAG, "[prepareSync] syncContexts: ${syncContexts.size}")
        }
    }

    override fun startSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener
    ) {
        executor.submit {
            val dialogRequestId = synchronizeObject.getDialogRequestId()
            Logger.d(TAG, "[startSync] dialogRequestId: $dialogRequestId, listener: $listener")

            var contextInfo = syncContexts[dialogRequestId]
            if (contextInfo == null || !contextInfo.waitingStartSyncObjects.remove(synchronizeObject)) {
                listener.onDenied()
                Logger.w(TAG, "[startSync] denied: prepareSync not called.")
            } else {
                contextInfo.shouldReleaseSyncObjects.add(synchronizeObject)
                listener.onGranted()
                Logger.d(TAG, "[startSync] granted.")
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
        executor.submit {
            val dialogRequestId = synchronizeObject.getDialogRequestId()

            val contextInfo = syncContexts[dialogRequestId]
            if (contextInfo == null) {
                Logger.w(TAG, "[releaseWithoutSync] no context info for $dialogRequestId")
                return@submit
            }

            with(contextInfo) {
                val removedAtWatingStart = waitingStartSyncObjects.remove(synchronizeObject)
                val removedAtShouldRelease = shouldReleaseSyncObjects.remove(synchronizeObject)

                if(waitingStartSyncObjects.isEmpty() && shouldReleaseSyncObjects.isEmpty()) {
                    syncContexts.remove(dialogRequestId)
                }

                Logger.d(TAG, "[releaseWithoutSync] removedAtWaitingStart: $removedAtWatingStart, removedAtShouldRelease: $removedAtShouldRelease, synContexts: $syncContexts")
            }
        }
    }

    private fun releaseSyncInternal(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener,
        immediate: Boolean
    ) {
        executor.submit {
            val dialogRequestId = synchronizeObject.getDialogRequestId()

            val contextInfo = syncContexts[dialogRequestId]
            if (contextInfo == null) {
                Logger.w(TAG, "[releaseSyncInternal] no context info for $dialogRequestId")
                listener.onDenied()
                return@submit
            }

            Logger.d(
                TAG,
                "[releaseSyncInternal] dialogRequestId: $dialogRequestId ,object: $synchronizeObject, listener: $listener, immediate: $immediate"
            )

            with(contextInfo) {
                if (waitingStartSyncObjects.remove(synchronizeObject) || shouldReleaseSyncObjects.remove(
                        synchronizeObject
                    )
                ) {
                    listener.onGranted()
                    Logger.d(TAG, "[releaseSyncInternal] granted")

                    if(waitingStartSyncObjects.isEmpty() && shouldReleaseSyncObjects.isEmpty()) {
                        syncContexts.remove(dialogRequestId)
                    } else {
                        if(immediate) {
                            waitingStartSyncObjects.forEach {
                                it.requestReleaseSync(true)
                            }
                            shouldReleaseSyncObjects.forEach {
                                it.requestReleaseSync(true)
                            }
                        } else {
                            if (waitingStartSyncObjects.isEmpty()) {
                                shouldReleaseSyncObjects.forEach {
                                    it.requestReleaseSync(false)
                                }
                            } else {

                            }
                        }
                    }
                } else {
                    Logger.d(TAG, "[releaseSyncInternal] denied")
                    listener.onDenied()
                }
            }

            Logger.d(TAG, "[releaseSyncInternal] syncContexts: $syncContexts")
        }
    }

    override fun existOtherSyncObject(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject): Boolean {
        val contextInfo = syncContexts[synchronizeObject.getDialogRequestId()] ?: return false

        if(contextInfo.waitingStartSyncObjects.firstOrNull {
            it != synchronizeObject
        } != null) {
            return true
        }

        if(contextInfo.shouldReleaseSyncObjects.firstOrNull {
                it != synchronizeObject
            } != null) {
            return true
        }

        return false
    }
}
