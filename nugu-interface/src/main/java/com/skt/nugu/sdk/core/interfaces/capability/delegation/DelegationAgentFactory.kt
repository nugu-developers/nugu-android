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
package com.skt.nugu.sdk.core.interfaces.capability.delegation

import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

interface DelegationAgentFactory {
    fun create(
        contextGetter: ContextGetterInterface,
        messageSender: MessageSender,
        inputProcessorManager: InputProcessorManagerInterface,
        defaultClient: DelegationClient
    ): AbstractDelegationAgent
}