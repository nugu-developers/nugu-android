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
package com.skt.nugu.sdk.core.interfaces.playsynchronizer

/**
 * Synchronize of the start, end and cancellation between associated plays.
 * (Associated plays means that have equal dialogRequestId)
 */
interface PlaySynchronizerInterface {
    interface OnRequestSyncListener {
        fun onGranted(){}
        fun onDenied(){}
    }

    /**
     * The synchronize object to interact with [PlaySynchronizerInterface]
     */
    interface SynchronizeObject {
        /**
         * A playServiceId if exist.
         */
        val playServiceId : String?

        /**
         * A dialogRequestId
         */
        val dialogRequestId : String

        /**
         * Called when a synchronize object should be released.
         *
         * Implementer should cancel or stop the play associated with the synchronizeObject
         */
        fun requestReleaseSync(){}

        /**
         * Called when a sync state changed.
         * The sync state changed at [prepareSync], [startSync], [releaseSync], [releaseSyncImmediately], [cancelSync] called for assocated synchronizeObject.
         * @param prepared prepared synchronizeObjects associated with the synchronizeObject
         * @param started started synchronizeObjects associated with the synchronizeObject
         */
        fun onSyncStateChanged(
            prepared: List<SynchronizeObject>,
            started: List<SynchronizeObject>
        ){}

        /**
         * Returns whether it is for display or not
         */
        fun isDisplay(): Boolean = false
    }

    interface Listener {
        /**
         * Called when a sync state changed.
         * The sync state changed at [prepareSync], [startSync], [releaseSync], [releaseSyncImmediately], [cancelSync] called for assocated synchronizeObject.
         * @param prepared all prepared synchronizeObjects
         * @param started all started synchronizeObjects
         */
        fun onSyncStateChanged(
            prepared: Set<SynchronizeObject>,
            started: Set<SynchronizeObject>
        )
    }

    /**
     * Notify that a [synchronizeObject] is preparing to start.
     *
     * @param synchronizeObject the synchronizeObject
     */
    fun prepareSync(synchronizeObject: SynchronizeObject)

    /**
     * Notify that a [synchronizeObject] has been started.
     *
     * @param synchronizeObject the synchronizeObject
     * @param listener the listener. If [synchronizeObject] is not prepared or release, [OnRequestSyncListener.onDenied] will be called.
     */
    fun startSync(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)

    /**
     * Notify that a [synchronizeObject] was finished.
     *
     * @param synchronizeObject the synchronizeObject
     * @param listener the listener. If [synchronizeObject] is not prepared or started, [OnRequestSyncListener.onDenied] will be called.
     */
    fun releaseSync(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)

    /**
     * Notify that a [synchronizeObject] was stopped or canceled.
     *
     * @param synchronizeObject the synchronizeObject
     * @param listener the listener. If [synchronizeObject] is not prepared or started, [OnRequestSyncListener.onDenied] will be called.
     */
    fun releaseSyncImmediately(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)

    /**
     * Cancel plays(synchronizeObject) with matching [dialogRequestId]
     * @param dialogRequestId the dialogRequestId
     */
    fun cancelSync(dialogRequestId: String)

    /**
     * Add a [listener]
     * @param listener the listener that added
     */
    fun addListener(listener: Listener)

    /**
     * Remove a [listener]
     * @param listener the listener that removed
     */
    fun removeListener(listener: Listener)
}