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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.interfaces.message.MessageObserver
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*

class InputProcessorManager : InputProcessorManagerInterface, MessageObserver {
    companion object {
        private const val TAG = "InputProcessorManager"
        private const val KEY_DIRECTIVES = "directives"
        private const val KEY_ATTACHMENT = "attachment"
    }

    private val responseTimeoutListeners = HashSet<InputProcessorManagerInterface.OnResponseTimeoutListener>()
    private val requests = ConcurrentHashMap<String, InputProcessor>()
    private val timeoutFutureMap = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun receive(message: String) {
        // message의 parsing을 담당.
        try {
            val jsonObject = JsonParser().parse(message).asJsonObject
            when {
                jsonObject.has(KEY_DIRECTIVES) -> onReceiveDirectives(jsonObject)
                jsonObject.has(KEY_ATTACHMENT) -> onReceiveAttachment(jsonObject)
                else -> onReceiveUnknownMessage(message)
            }
        } catch (e: Exception) {
            onReceiveUnknownMessage(message)
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

    private fun onReceiveDirectives(jsonObject: JsonObject) {
        val directives = jsonObject.getAsJsonArray(KEY_DIRECTIVES)

        var removedDialogRequestId = ""
        var timeoutFutureRemoved = false
        for (directive in directives) {
            try {
                val header = MessageFactory.createHeader(directive.asJsonObject.getAsJsonObject("header"))
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

    private fun onReceiveAttachment(jsonObject: JsonObject) {
        Logger.d(TAG, "[onReceiveAttachment] ignore attachment")
    }

    private fun onReceiveUnknownMessage(message: String) {
        Logger.e(TAG, "[onReceiveUnknownMessage] $message")
    }

    override fun addResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.add(listener)
    }

    override fun removeResponseTimeoutListener(listener: InputProcessorManagerInterface.OnResponseTimeoutListener) {
        responseTimeoutListeners.remove(listener)
    }
}