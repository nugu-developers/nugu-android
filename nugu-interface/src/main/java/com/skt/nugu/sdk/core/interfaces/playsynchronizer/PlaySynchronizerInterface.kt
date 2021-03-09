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

interface PlaySynchronizerInterface {
    interface OnRequestSyncListener {
        fun onGranted(){}
        fun onDenied(){}
    }

    interface SynchronizeObject {
        val playServiceId : String?
        val dialogRequestId : String

        fun requestReleaseSync(){}
        fun onSyncStateChanged(
            prepared: List<SynchronizeObject>,
            started: List<SynchronizeObject>
        ){}
    }

    fun prepareSync(synchronizeObject: SynchronizeObject)
    fun startSync(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)
    fun releaseSync(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)
    fun releaseSyncImmediately(synchronizeObject: SynchronizeObject, listener: OnRequestSyncListener? = null)
    fun cancelSync(dialogRequestId: String)
}