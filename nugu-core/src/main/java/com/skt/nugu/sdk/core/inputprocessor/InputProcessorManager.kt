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
package com.skt.nugu.sdk.core.inputprocessor

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*

class InputProcessorManager(private val timeoutInMilliSeconds: Long = 10 * 1000L) : InputProcessorManagerInterface, DirectiveGroupProcessorInterface.Listener {
    companion object {
        private const val TAG = "InputProcessorManager"
    }

    private val responseTimeoutListeners = HashSet<InputProcessorManagerInterface.OnResponseTimeoutListener>()
    private val requests = ConcurrentHashMap<String, InputProcessor>()
    private val timeoutFutureMap = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun onPostProcessed(directives: List<Directive>) {
        val dialogRequestId = directives.firstOrNull()?.header?.dialogRequestId ?: return

        val receiveResponse = requests[dialogRequestId]?.onReceiveDirectives(dialogRequestId, directives) ?: false

        if(receiveResponse) {
            dialogRequestId.let {
                timeoutFutureMap[it]?.cancel(true)
                timeoutFutureMap.remove(it)
            }
        }

        if(receiveResponse && !dialogRequestId.isBlank()) {
            requests.remove(dialogRequestId)
        }
    }

    override fun onRequested(inputProcessor: InputProcessor, dialogRequestId: String) {
        Logger.d(TAG, "[onRequested] $inputProcessor, $dialogRequestId")
        requests[dialogRequestId] = inputProcessor
        timeoutFutureMap[dialogRequestId]?.cancel(true)
        timeoutFutureMap[dialogRequestId] = timeoutScheduler.schedule({
            onResponseTimeout(inputProcessor, dialogRequestId)
        }, timeoutInMilliSeconds, TimeUnit.MILLISECONDS)
    }

    private fun onResponseTimeout(inputProcessor: InputProcessor, dialogRequestId: String) {
        Logger.d(TAG, "[onResponseTimeout] $inputProcessor, $dialogRequestId")
        requests.remove(dialogRequestId)
        timeoutFutureMap.remove(dialogRequestId)
        inputProcessor.onResponseTimeout(dialogRequestId)
        responseTimeoutListeners.forEach {
            it.onResponseTimeout(dialogRequestId)
        }
    }

    override fun addResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.add(listener)
    }

    override fun removeResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.remove(listener)
    }
}