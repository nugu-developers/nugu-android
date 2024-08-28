/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.core.network

import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.atomic.AtomicBoolean

class ServerInitiatedDirectiveController(val TAG: String) {
    private var isStart =  AtomicBoolean(false)
    private var completionListenerCalled =  AtomicBoolean(false)
    private var listener: (() -> Unit)? = null

    fun notifyOnCompletionListener() {
        if (!completionListenerCalled.get() && isStart.get()) {
            listener?.invoke()
        }
        completionListenerCalled.set(true)
    }

    fun setOnCompletionListener(onCompletionListener: (() -> Unit)?) {
        listener = onCompletionListener
    }

    /**
     * Start the DirectivesService
     */
    fun start() {
        if (isStart.get()) {
            Logger.w(TAG, "[start] ServerInitiatedDirective is already started.")
            return
        }
        isStart.set(true)
        completionListenerCalled.set(false)

        Logger.i(TAG, "[start] ServerInitiatedDirective started")
    }

    fun stop(transport: Transport?) {
        listener = null

        if (!isStart.get()) {
            Logger.w(TAG, "[stop] ServerInitiatedDirective is already stopped. (isStart=$isStart)")
            return
        }
        isStart.set(false)

        transport?.stopDirectivesService() ?: run {
            Logger.w(
                TAG,
                "[stop] activeTransport is not possible."
            )
        }
        Logger.i(TAG, "[stop] ServerInitiatedDirective stopped.")
    }

    fun release() {
        listener = null
        isStart.set(false)
        completionListenerCalled.set(false)
    }

    fun isStarted() = isStart.get()
}