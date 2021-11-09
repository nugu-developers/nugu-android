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

package com.skt.nugu.sdk.core.inputprocessor

import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessor
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Test
import org.mockito.kotlin.*

class InputProcessorManagerTest {
    private val timeoutInMilliSeconds = 1L
    private val verifyTimeout = timeoutInMilliSeconds * 500

    @Test
    fun testOnResponseTimeout() {
        val manager = InputProcessorManager(timeoutInMilliSeconds)
        val timeoutDialogRequestId = "timeoutDialogRequestId"
        val listener: InputProcessorManagerInterface.OnResponseTimeoutListener = mock()
        manager.addResponseTimeoutListener(listener)

        val inputProcessor: InputProcessor = mock()

        manager.onRequested(inputProcessor, timeoutDialogRequestId)

        Thread {
            verify(inputProcessor, timeout(verifyTimeout)).onResponseTimeout(
                timeoutDialogRequestId
            )
            verify(listener, timeout(verifyTimeout)).onResponseTimeout(
                timeoutDialogRequestId
            )
        }.let {
            it.start()
            it.join()
        }
    }

    @Test
    fun testOnReceiveResponse() {
        val manager = InputProcessorManager()
        val dialogRequestId = "dialogRequestId"
        val listener: InputProcessorManagerInterface.OnResponseTimeoutListener = mock()
        manager.addResponseTimeoutListener(listener)

        val received = arrayListOf(
            Directive(
                null,
                Header(dialogRequestId, "", "", "", "", ""),
                "{}"
            )
        )

        val inputProcessor: InputProcessor = mock()
        whenever(inputProcessor.onReceiveDirectives(dialogRequestId, received)).thenReturn(true)

        manager.onRequested(inputProcessor, dialogRequestId)
        manager.onPostProcessed(received)

        verify(inputProcessor).onReceiveDirectives(dialogRequestId, received)
        Thread.sleep(100)
        verify(inputProcessor, never()).onResponseTimeout(dialogRequestId)
        verify(listener, never()).onResponseTimeout(dialogRequestId)
    }

    @Test
    fun testRemoveListener() {
        val manager = InputProcessorManager(timeoutInMilliSeconds)
        val timeoutDialogRequestId = "timeoutDialogRequestId"
        val listener: InputProcessorManagerInterface.OnResponseTimeoutListener = mock()
        manager.addResponseTimeoutListener(listener)
        manager.removeResponseTimeoutListener(listener)

        val inputProcessor: InputProcessor = mock()

        manager.onRequested(inputProcessor, timeoutDialogRequestId)

        Thread {
            verify(inputProcessor, timeout(verifyTimeout)).onResponseTimeout(
                timeoutDialogRequestId
            )
            verify(listener, never()).onResponseTimeout(timeoutDialogRequestId)
        }.let {
            it.start()
            it.join()
        }
    }
}