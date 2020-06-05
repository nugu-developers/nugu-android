/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import devicegateway.grpc.DirectiveMessage

object DirectivePreconditions {
    fun DirectiveMessage.checkIfDirectiveIsUnauthorizedRequestException(): Boolean {
        val namespace = "System"
        val name = "Exception"
        val code = "UNAUTHORIZED_REQUEST_EXCEPTION"

        this.directivesOrBuilderList.forEach {
            if (it.header.namespace == namespace && it.header.name == name) {
                if (it.payload.contains(code)) {
                    return true
                }
            }
        }
        return false
    }

    fun EventMessageRequest.checkIfEventMessageIsAsrRecognize(): Boolean {
        val namespace = "ASR"
        val name = "Recognize"

        if (this.namespace == namespace && this.name == name) {
            return true
        }
        return false
    }
}