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

package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert
import org.junit.Test

class TimeoutResponseHandlerTest {
    @Test
    fun testPreProcessOnTimeout() {
        val handler = TimeoutResponseHandler()
        val timeoutDialogRequestId = "timeout_dialogRequestId"
        val normalDialogRequetId = "normal_dialogRequestId"

        handler.onResponseTimeout(timeoutDialogRequestId)

        val preProcessed = handler.preProcess(
            arrayListOf(
                Directive(
                    null, Header(
                        timeoutDialogRequestId,
                        "messageId",
                        "name",
                        "namespace",
                        "1.0",
                        ""
                    ), "{}"
                ),
                Directive(
                    null, Header(
                        normalDialogRequetId,
                        "messageId",
                        "name",
                        "namespace",
                        "1.0",
                        ""
                    ), "{}"
                )
            )
        )

        Assert.assertTrue(preProcessed.size == 1 && preProcessed.first().header.dialogRequestId == normalDialogRequetId)
    }
}