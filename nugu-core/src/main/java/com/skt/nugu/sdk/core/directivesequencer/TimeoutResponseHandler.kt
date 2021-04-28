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
package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class drop directives which received after timeout
 */
class TimeoutResponseHandler : DirectiveGroupPreProcessor,
    InputProcessorManagerInterface.OnResponseTimeoutListener {
    companion object {
        private const val TAG = "TimeoutResponseHandler"
        private const val MAX_CAPACITY = 100
    }

    private val lock = ReentrantLock()
    private val responseTimeoutDialogRequestIds = LinkedHashSet<String>()

    override fun preProcess(directives: List<Directive>): List<Directive> {
        lock.withLock {
            return directives.filter {
                !responseTimeoutDialogRequestIds.contains(it.getDialogRequestId())
            }.also {
                responseTimeoutDialogRequestIds.removeAll { dialogRequestId ->
                    directives.any { it.getDialogRequestId() == dialogRequestId }
                }
            }
        }
    }

    override fun onResponseTimeout(dialogRequestId: String) {
        lock.withLock {
            Logger.d(TAG, "[onResponseTimeout] added dialogRequestId: $dialogRequestId")
            responseTimeoutDialogRequestIds.add(dialogRequestId)
            if (responseTimeoutDialogRequestIds.size > MAX_CAPACITY) {
                Logger.w(TAG, "[onResponseTimeout] responseTimeoutDialogRequestIds's capacity exceeded!!!")
                responseTimeoutDialogRequestIds.remove(responseTimeoutDialogRequestIds.first())
            }
        }
    }
}