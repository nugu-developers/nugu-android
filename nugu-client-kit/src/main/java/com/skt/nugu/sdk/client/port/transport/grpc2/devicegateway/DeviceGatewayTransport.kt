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
package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import devicegateway.grpc.AttachmentMessage
import devicegateway.grpc.DirectiveMessage
import io.grpc.Status

interface DeviceGatewayTransport {
    interface TransportObserver {
        fun onConnected()
        fun onReconnecting(reason: ConnectionStatusListener.ChangedReason = ConnectionStatusListener.ChangedReason.NONE)
        fun onError(reason: ConnectionStatusListener.ChangedReason)
    }

    fun onReceiveDirectives(directiveMessage: DirectiveMessage)
    fun onReceiveAttachment(attachmentMessage: AttachmentMessage)
    fun onError(status: Status, who: String)
    fun onPingRequestAcknowledged()

    fun send(call: Call) : Boolean
    fun connect() : Boolean
    fun disconnect()
    fun shutdown()
    fun startDirectivesService()
    fun stopDirectivesService()
}