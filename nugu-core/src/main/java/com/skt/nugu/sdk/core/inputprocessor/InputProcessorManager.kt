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

import com.skt.nugu.sdk.core.directivesequencer.DirectiveGroupHandler
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.utils.Logger
import java.util.concurrent.*

class InputProcessorManager : InputProcessorManagerInterface, DirectiveGroupHandler {
    companion object {
        private const val TAG = "InputProcessorManager"
    }

    private val responseTimeoutListeners = HashSet<InputProcessorManagerInterface.OnResponseTimeoutListener>()
    private val requests = ConcurrentHashMap<String, InputProcessor>()
    private val timeoutFutureMap = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun onReceiveDirectives(directives: List<Directive>) {
        var removedDialogRequestId = ""
        var timeoutFutureRemoved = false
        for (directive in directives) {
            try {
                val header = directive.header
                if(!timeoutFutureRemoved && header.namespace != AbstractASRAgent.NAMESPACE) {
                    timeoutFutureRemoved = true
                    header.dialogRequestId.let {
                        removedDialogRequestId = it
                        timeoutFutureMap[it]?.cancel(true)
                        timeoutFutureMap.remove(it)
                    }
                }
                onReceiveResponse(header.dialogRequestId, header)
            } catch (th: Throwable) {
                // ignore
            }
        }

        if(timeoutFutureRemoved && !removedDialogRequestId.isNullOrBlank()) {
            requests.remove(removedDialogRequestId)
        }
    }

    override fun onRequested(inputProcessor: InputProcessor, dialogRequestId: String) {
        Logger.d(TAG, "[onRequested] $inputProcessor, $dialogRequestId")
        requests[dialogRequestId] = inputProcessor
        timeoutFutureMap[dialogRequestId]?.cancel(true)
        timeoutFutureMap[dialogRequestId] = timeoutScheduler.schedule({
            onResponseTimeout(inputProcessor, dialogRequestId)
        }, 10, TimeUnit.SECONDS)
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

    private fun onReceiveResponse(dialogRequestId: String, header: Header) {
        val inputProcessor = requests[dialogRequestId]
        if (inputProcessor != null) {
            inputProcessor.onReceiveResponse(dialogRequestId, header)
        } else {
            Logger.w(TAG, "[receiveResponse] no input processor for $dialogRequestId")
        }
    }

    override fun addResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.add(listener)
    }

    override fun removeResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.remove(listener)
    }
}