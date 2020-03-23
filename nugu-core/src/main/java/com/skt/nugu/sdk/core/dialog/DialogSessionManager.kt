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
package com.skt.nugu.sdk.core.dialog

import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface

class DialogSessionManager : DialogSessionManagerInterface {
    companion object {
        private const val TAG = "DialogSessionManager"
    }

    private val listeners = HashSet<DialogSessionManagerInterface.OnSessionStateChangeListener>()

    private var currentSessionId: String? = null

    override fun openSession(
        sessionId: String,
        domainTypes: Array<String>?,
        playServiceId: String?
    ) {
        Logger.d(TAG, "[openSession] $sessionId")
        currentSessionId = sessionId
        listeners.forEach {
            it.onSessionOpened(sessionId, domainTypes, playServiceId)
        }
    }

    override fun closeSession() {
        val sessionId = currentSessionId
        Logger.d(TAG, "[closeSession] $sessionId")
        currentSessionId = null
        if(sessionId == null) {
            return
        }

        listeners.forEach {
            it.onSessionClosed(sessionId)
        }
    }

    override fun addListener(listener: DialogSessionManagerInterface.OnSessionStateChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: DialogSessionManagerInterface.OnSessionStateChangeListener) {
        listeners.remove(listener)
    }
}